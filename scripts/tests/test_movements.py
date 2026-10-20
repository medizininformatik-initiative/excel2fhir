import copy
import json
import os
import subprocess
from unittest.mock import patch
from pathlib import Path
import sys
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from workbook_records import record
from synthea_movements import enrich,dt


class MovementTest(unittest.TestCase):
    def fixture(self,count=1):
        entries=[];rows=[];numbers={}
        for i in range(count):
            key=str(i+1);numbers[key]=key
            entries.append({'fullUrl':'urn:uuid:'+key,'resource':{'resourceType':'Encounter','id':key,
                'class':{'code':'IMP'},'period':{'start':'2026-01-01T08:00:00Z','end':'2026-01-08T12:00:00Z'}}})
            rows.append(record('Fall', {
                'Patient-ID': 'p',
                'Fall-Nr': key,
                'Start': '2026-01-01T08:00:00Z',
                'Ende': '2026-01-08T12:00:00Z',
                'Einrichtungskontaktklasse': 'stationaer',
            }))
        return {'entry':entries},rows,numbers

    def test_deterministic_variety_and_bounded_parent_first_timeline(self):
        source,rows,numbers=self.fixture(30);before=copy.deepcopy(source)
        result,report=enrich(source,rows,numbers)
        self.assertEqual((result,report),enrich(source,rows,numbers))
        self.assertEqual(before,source)
        patterns=set()
        for row in result:
            self.assertEqual(11,len(row))
            if row['Ende']: self.assertLessEqual(dt(row['Start']),dt(row['Ende']))
        for number in numbers:
            care=[r for r in result if r['Fall-Nr']==number and r['Kontaktart'] in ('Normalstationär','Intensivstationär')]
            for left,right in zip(care,care[1:]): self.assertEqual(dt(left['Ende']),dt(right['Start']))
            patterns.add(tuple((r['Fachabteilung'],r['Station'],r['Zimmer'],r['Bett'],r['Kontaktart'])for r in care))
        self.assertGreater(len(patterns),10)

    def test_reproducible_across_processes_and_python_hash_seeds(self):
        source, rows, numbers = self.fixture(12)
        expected = json.loads(json.dumps(enrich(source, rows, numbers)))
        script = ("import json, random, sys; from synthea_movements import enrich; "
                  "random.seed(999); print(json.dumps(enrich(*json.load(sys.stdin))))")
        for hash_seed in ('1', '98765'):
            env = dict(os.environ, PYTHONHASHSEED=hash_seed,
                       PYTHONPATH=str(Path(__file__).resolve().parents[1]))
            result = subprocess.run([sys.executable, '-c', script], input=json.dumps([source, rows, numbers]),
                                    text=True, capture_output=True, check=True, env=env)
            self.assertEqual(expected, json.loads(result.stdout))

    def test_movement_seed_changes_synthetic_contacts_reproducibly(self):
        source, rows, numbers = self.fixture(12)
        original, _ = enrich(source, rows, numbers)
        with patch('synthea_movements.SEED', 123):
            changed, report = enrich(source, rows, numbers)
            self.assertEqual((changed, report), enrich(source, rows, numbers))
        self.assertNotEqual(original, changed)
        self.assertEqual(123, report['seed'])

    def test_only_known_operations_create_op_contacts_and_source_times_stay_unchanged(self):
        source,rows,numbers=self.fixture()
        procedure={'resourceType':'Procedure','id':'op','encounter':{'reference':'urn:uuid:1'},
            'code':{'coding':[{'system':'http://snomed.info/sct','code':'232717009'}]},
            'performedPeriod':{'start':'2026-01-02T10:00:00Z','end':'2026-01-02T13:00:00Z'}}
        source['entry'].append({'resource':procedure});original=copy.deepcopy(source)
        result,report=enrich(source,rows,numbers)
        op=[r for r in result if r['Kontaktart']=='Operation'];self.assertEqual(1,len(op))
        self.assertLessEqual(dt(op[0]['Start']),dt(procedure['performedPeriod']['start']))
        self.assertEqual('',op[0]['Ende'])
        primary=next(r for r in result if r['Kontaktart'] in ('Normalstationär','Intensivstationär') and dt(r['Start'])<=dt(op[0]['Start'])<dt(r['Ende']))
        self.assertTrue(primary['Bett'])
        self.assertEqual(primary['Ende'],report['operationSources'][0]['derivedEnd'])
        self.assertEqual(original,source);self.assertEqual(['op'],report['operationSources'][0]['sourceProcedures'])
        procedure['code']['coding'][0]['code']='not-in-mapping'
        self.assertFalse(any(r['Kontaktart']=='Operation'for r in enrich(source,rows,numbers)[0]))

    def test_ambulatory_operation_has_primary_without_secondary_kind(self):
        source, rows, numbers = self.fixture()
        source['entry'][0]['resource']['class']['code'] = 'AMB'
        rows[0]['Einrichtungskontaktklasse'] = 'ambulant'
        source['entry'].append({'resource': {'resourceType': 'Procedure', 'id': 'op',
            'encounter': {'reference': 'urn:uuid:1'},
            'code': {'coding': [{'system': 'http://snomed.info/sct', 'code': '232717009'}]},
            'performedDateTime': '2026-01-02T10:00:00Z'}})
        result, report = enrich(source, rows, numbers)
        operation_index = next(i for i, r in enumerate(result) if r['Kontaktart'] == 'Operation')
        primary = result[operation_index - 1]
        self.assertEqual('', primary['Kontaktart'])
        self.assertTrue(primary['Bett'])
        self.assertEqual(primary['Ende'], report['operationSources'][0]['derivedEnd'])

    def test_unbounded_cases_are_reported_without_inventing_dates(self):
        source,rows,numbers=self.fixture();del source['entry'][0]['resource']['period']['end']
        result,report=enrich(source,rows,numbers)
        self.assertEqual(1,len(result));self.assertEqual(1,len(report['skipped']))

    def test_operative_mapping_uses_existing_source_codes(self):
        base=Path(__file__).resolve().parents[1]/'mappings'
        registry=json.loads((base/'synthea-source-code-registry.json').read_text())
        codes={e['code']for e in registry['entries']if e['system']=='http://snomed.info/sct'}
        self.assertLessEqual(json.loads((base/'synthea-operative-procedures.json').read_text())['procedures'].keys(),codes)
