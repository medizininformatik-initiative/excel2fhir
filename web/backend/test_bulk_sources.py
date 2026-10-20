import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import bulk_sources as sources
import store


class SourceRemainderTests(unittest.TestCase):
    def test_remainder_can_be_consumed_in_parts_then_cross_seed_boundary(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            settings = {'patientSeed': '1000', 'population': 275, 'outputMode': 'synthea'}
            first = root / 'first'; first.mkdir()
            snapshot = {'generation': settings, 'bulkSourceVersion': 1}
            calls = []
            def generate(index, destination):
                calls.append(index)
                target = destination / 'fhir'; target.mkdir(parents=True)
                for i in range(100):
                    (target / f'{i:03d}.json').write_text(json.dumps({'entry': [{'resource': {'resourceType': 'Patient', 'id': f'{index}:{i}'}}]}))
                (target / 'support.json').write_text(json.dumps({'entry': []}))
            for start, size in ((0,100),(100,100),(200,75)):
                selected = sources.select(first,snapshot,start,size,first / f'work-{start}',generate)
                self.assertEqual(size, sum(r['patient'] for r in selected))
                sources.prune(first,snapshot,start+size)
            remaining = sources.finish(first,snapshot)
            self.assertEqual((75,25,'1002'), (remaining['offset'],remaining['count'],remaining['seed']))
            self.assertEqual([0,1,2],calls)
            for step, count, offset in ((1,10,75),(2,15,85)):
                following = root / f'next-{step}'; following.mkdir()
                (following / sources.INHERITED).write_bytes((first / sources.REMAINDER).read_bytes())
                snapshot = {'bulkSourceVersion':1,'generation':{**settings,'patientSeed':'1002','population':count},
                            'continuation':{'sourceOffset':offset,'remainder':remaining}}
                selected = sources.select(following,snapshot,0,count,following/'work',lambda *args: self.fail('Must reuse remainder'))
                ids = [json.loads(r['path'].read_text())['entry'][0]['resource']['id'] for r in selected if r['patient']]
                self.assertEqual([f'2:{i}' for i in range(offset,offset+count)],ids)
                remaining = sources.finish(following,snapshot)
                first = following
            self.assertIsNone(remaining)

    def test_crossing_seed_boundary_preserves_order_and_detects_changed_remainder(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            records = [{'name':f'{i:03d}.json','patient':True,'id':str(i)} for i in range(75,100)]
            path = folder / sources.INHERITED
            sources.write(path, {'seed':'10','offset':75,'patients':records,'support':[]},
                          ((r['name'],json.dumps({'entry':[]})) for r in records))
            snapshot={'generation':{'patientSeed':'10','population':100},'continuation':{'sourceOffset':75,'remainder':sources.description(path)}}
            def generate(index,destination):
                self.assertEqual(1,index)
                target=destination/'fhir';target.mkdir(parents=True)
                for i in range(100):
                    (target/f'{i:03d}.json').write_text(json.dumps({'entry':[{'resource':{'resourceType':'Patient','id':f'next-{i}'}}]}))
            selected=sources.select(folder,snapshot,0,100,folder/'work',generate)
            self.assertEqual(['10']*25+['11']*75,[r['seed'] for r in selected])
            self.assertEqual(75,sources.finish(folder,snapshot)['offset'])
            path.write_bytes(b'changed')
            with self.assertRaisesRegex(ValueError,'changed'):
                sources.select(folder,snapshot,0,1,folder/'work2',generate)
