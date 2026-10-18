import gzip
import io
import json
import tempfile
import types
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest.mock import patch
from uuid import uuid4

import bulk_generation as bulk
import bulk_plan
import generation
import store
from test_bulk_generation import BulkTests


class PlanTests(unittest.TestCase):
    def test_limits_remainders_and_seed_consumption(self):
        settings = generation.Settings(executionMode='bulk', population=100000, patientsPerRun=10000, patientSeed='1000').model_dump(mode='json')
        runs = bulk_plan.plan(settings)
        self.assertEqual(10, len(runs))
        self.assertEqual(('1000','1099'), (runs[0]['firstSeed'], runs[0]['lastSeed']))
        self.assertEqual(('1900','1999'), (runs[-1]['firstSeed'], runs[-1]['lastSeed']))
        self.assertEqual([100,100,1], [r['total'] for r in bulk_plan.plan({**settings, 'population':201,'patientsPerRun':100})])
        self.assertEqual(str(-2**63), bulk_plan.seed_at(str(2**63-1), 1))
        for changes in ({'patientsPerRun':101},{'patientsPerRun':0},{'population':100001}):
            with self.assertRaises(ValueError): generation.Settings.model_validate({**settings,**changes})

    def test_work_progress_moves_within_chunks_and_waits_for_package_publication(self):
        def work(completed, phase, done=0, count=100, **kwargs):
            return bulk_plan.work_progress(101, completed, phase, done, count, **kwargs)
        points = [work(0, 'generating', 0)[0], work(0, 'generating', 51)[0],
                  work(0, 'mapping', 0)[0], work(0, 'mapping', 51)[0],
                  work(0, 'converting', 0)[0], work(0, 'converting', 100)[0],
                  work(100, 'packaging', 1, 2, package_patients=100)[0],
                  work(100, 'between-runs', packaged=100)[0],
                  work(100, 'generating', 1, 1, packaged=100)[0],
                  work(100, 'mapping', 1, 1, packaged=100)[0],
                  work(100, 'converting', 1, 1, packaged=100)[0],
                  work(101, 'packaging', 1, 2, packaged=100, package_patients=1)[0],
                  work(101, 'between-runs', packaged=101)[0],
                  work(101, 'complete')[0]]
        self.assertEqual(sorted(points), points)
        self.assertEqual(51, points[1])
        self.assertEqual(405, points[-1])
        self.assertLess(points[-2], points[-1])
        self.assertEqual((50, 304), work(0, 'generating', 50, native=True))
        self.assertEqual((150, 304), work(0, 'compressing', 50, native=True))
        self.assertEqual((200, 304), work(100, 'between-chunks', native=True))
        self.assertEqual((100, 405), work(0, 'generating', 200))
        self.assertEqual((0, 405), work(0, 'starting', 0, 0))

    def test_progress_parser_accepts_complete_records_only(self):
        with tempfile.TemporaryDirectory() as tmp:
            log=Path(tmp)/'log'
            log.write_text('noise\nWORKBENCH_SELECTION {"matched":12,"attempts":30}\nWORKBENCH_SELECTION {')
            self.assertEqual({'stageCompleted':12,'attempts':30},bulk.process_progress(log))
            log.write_text('WORKBENCH_CONVERSION 17\n')
            self.assertEqual({'stageCompleted':17},bulk.process_progress(log))

    def test_process_reports_progress_before_it_finishes(self):
        import sys
        with tempfile.TemporaryDirectory() as tmp:
            observed=[]
            bulk.checked_process([sys.executable,'-u','-c',
                'import time; print("WORKBENCH_CONVERSION 1"); time.sleep(2.3); print("WORKBENCH_CONVERSION 2")'],
                Path(tmp)/'conversion.log', observed.append)
            self.assertIn({'stageCompleted':1},observed)
            self.assertEqual({'stageCompleted':2},observed[-1])


class ResumeApiTests(BulkTests):
    def test_resume_keeps_snapshot_and_job_identity(self):
        from test_generation import CATALOG
        import synthea_runtime
        with patch.object(generation,'catalogue',return_value=CATALOG), patch.object(synthea_runtime,'fingerprint',return_value='importer'):
            job=store.create('synthea-generation','default',generation_settings={'executionMode':'bulk','population':201,'patientsPerRun':100})
        folder=store.ROOT/'jobs'/job
        original=(folder/'snapshot.json').read_bytes()
        store.claim(); store.finish(job,'failed',1)
        store.resume(job); store.resume(job)
        self.assertEqual(original,(folder/'snapshot.json').read_bytes())
        self.assertEqual('queued',store.get(job)['state'])
        self.assertEqual(1,len(store.jobs()))
        self.assertEqual(3,len(store.get(job)['bulk_subruns']))
        self.assertEqual(job,store.claim())
        with self.assertRaises(store.SubmissionConflict): store.resume(job)


class SeriesTests(unittest.TestCase):
    def test_sequential_failure_resume_packages_and_estimates(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); folder=root/'job'; folder.mkdir()
            settings={'executionMode':'bulk','population':201,'patientsPerRun':100,'patientSeed':'1000'}
            (folder/'snapshot.json').write_text(json.dumps({'generation':settings,'bulkLayoutVersion':2,'bulkOutputName':'cohort'}))
            (folder/'default.config').write_text('CONFIGURATION_VERSION=1')
            seeds=[]; fail=[True]; observed=[]; counter_starts=[]
            def prepare(bundle, *args, **kwargs):
                return {'Person':[{'Patient-ID':bundle['entry'][0]['resource']['id']}]},{}
            def csv(rows, path, prefix):
                path.mkdir(parents=True); (path/'pid').write_text(rows['Person'][0]['Patient-ID'])
            def command(chunk, destination): return ['generate',str(chunk['population']),chunk['patientSeed'],str(destination)]
            def process(command, log, update=None):
                if command[0]=='generate':
                    size,seed=int(command[1]),command[2]; seeds.append(seed)
                    target=Path(command[3])/'fhir';target.mkdir(parents=True)
                    if update:
                        update({'stageCompleted':1,'attempts':2})
                        observed.append(json.loads((folder/'bulk-progress.json').read_text()))
                    for i in range(size):
                        pid=f'{seed}-{i:03d}'
                        (target/(pid+'.json')).write_text(json.dumps({'entry':[{'resource':{'resourceType':'Patient','id':pid}}]}))
                else:
                    jobs = json.loads(Path(command[-2]).read_text())
                    start = jobs[0]['identifierCounters'].get('patient-rule', 0)
                    counter_starts.append(start)
                    if seeds[-1]=='1001' and fail[0]: raise RuntimeError('deliberate failure')
                    Path(jobs[0]['identifierCountersOutput']).write_text(json.dumps({'patient-rule': start + len(jobs)}))
                    for job in jobs:
                        pid=(Path(job['input'])/'pid').read_text()
                        with gzip.open(Path(job['output'])/(pid+'.json.gz'),'wt') as out:
                            json.dump({'entry':[{'resource':{'resourceType':'Patient','id':pid}}]},out)
                    if update: update({'stageCompleted':len(json.loads(Path(command[-2]).read_text()))})
            modules={name:types.ModuleType(name) for name in ['synthea_to_excel','direct_csv','converter_options','synthea_consent']}
            modules['synthea_to_excel'].prepare=prepare
            modules['direct_csv'].write_direct_csv=csv
            modules['converter_options'].resolve_config=lambda path:{'values':{}}
            modules['synthea_consent'].assignments=lambda *args:{}
            modules['synthea_consent'].enrich=lambda *args:{}
            with patch.dict('sys.modules',modules), patch.object(bulk,'OUTPUT_ROOT',root/'output'), patch.object(generation,'native_command',side_effect=command), patch.object(bulk,'checked_process',side_effect=process), redirect_stdout(io.StringIO()):
                with self.assertRaisesRegex(RuntimeError,'deliberate failure'): bulk.run(folder)
                first=root/'output/cohort/packages/run-00001-package-00001-100Pat.zip'
                saved=first.read_bytes(); modified=first.stat().st_mtime_ns
                progress=json.loads((folder/'bulk-progress.json').read_text())
                self.assertEqual(100,progress['completed'])
                self.assertGreater(progress['estimatedTotalBytes'],progress['generatedBytes'])
                self.assertIsNotNone(progress['remainingSeconds'])
                fail[0]=False; bulk.run(folder)
            self.assertEqual(['1000','1001','1001','1002'],seeds)
            self.assertEqual([0, 100, 100, 200], counter_starts)
            self.assertEqual(saved,first.read_bytes());self.assertEqual(modified,first.stat().st_mtime_ns)
            self.assertEqual(0,len(list((root/'output').rglob('*.json.gz'))))
            import zipfile
            archives=list((root/'output').rglob('*.zip'))
            self.assertEqual(3,len(archives))
            archived = 0
            saved_counters = []
            for path in sorted(archives):
                with zipfile.ZipFile(path) as archive:
                    archived += sum(m.filename.endswith('.json.gz') for m in archive.infolist())
                    saved_counters.extend(chunk['identifierCounters']['patient-rule']
                                          for chunk in json.loads(archive.read('manifest.json'))['chunks'].values())
            self.assertEqual(201, archived)
            self.assertEqual([100, 200, 201], saved_counters)
            progress=json.loads((folder/'bulk-progress.json').read_text())
            self.assertEqual((201,3,'complete',0),(progress['completed'],progress['finishedRuns'],progress['phase'],progress['remainingSeconds']))
            self.assertEqual(progress['generatedBytes'],progress['estimatedTotalBytes'])
            self.assertEqual(sum(sum(i.file_size for i in zipfile.ZipFile(p).infolist() if i.filename.endswith('.json.gz')) for p in archives),progress['generatedBytes'])
            self.assertTrue(any(p['stageCompleted']==1 and p['attempts']==2 for p in observed))
