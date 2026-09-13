import copy
import json
from pathlib import Path
import sys
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from synthea_movements import enrich,dt


class MovementTest(unittest.TestCase):
    def fixture(self,count=1):
        entries=[];rows=[];numbers={}
        for i in range(count):
            key=str(i+1);numbers[key]=key
            entries.append({'fullUrl':'urn:uuid:'+key,'resource':{'resourceType':'Encounter','id':key,
                'class':{'code':'IMP'},'period':{'start':'2026-01-01T08:00:00Z','end':'2026-01-08T12:00:00Z'}}})
            rows.append(['p',key,'2026-01-01T08:00:00Z','2026-01-08T12:00:00Z','stationaer','','','','',''])
        return {'entry':entries},rows,numbers

    def test_deterministic_variety_and_bounded_parent_first_timeline(self):
        source,rows,numbers=self.fixture(30);before=copy.deepcopy(source)
        result,report=enrich(source,rows,numbers)
        self.assertEqual((result,report),enrich(source,rows,numbers))
        self.assertEqual(before,source)
        seen={};patterns=set()
        for row in result:
            self.assertNotIn((row[1],row[10]),seen)
            if row[13]:
                parent=seen[row[1],row[13]]
                self.assertLessEqual(dt(parent[2]),dt(row[2]));self.assertLessEqual(dt(row[3]),dt(parent[3]))
            self.assertLessEqual(dt(row[2]),dt(row[3]));seen[row[1],row[10]]=row
        for number in numbers:
            care=[r for r in result if r[1]==number and r[11]=='Versorgungsstellenkontakt']
            for left,right in zip(care,care[1:]):self.assertEqual(dt(left[3]),dt(right[2]))
            patterns.add(tuple((r[5],r[6],r[7],r[8],r[12])for r in care))
        self.assertGreater(len(patterns),10)

    def test_only_known_operations_create_op_contacts_and_source_times_stay_unchanged(self):
        source,rows,numbers=self.fixture()
        procedure={'resourceType':'Procedure','id':'op','encounter':{'reference':'urn:uuid:1'},
            'code':{'coding':[{'system':'http://snomed.info/sct','code':'232717009'}]},
            'performedPeriod':{'start':'2026-01-02T10:00:00Z','end':'2026-01-02T13:00:00Z'}}
        source['entry'].append({'resource':procedure});original=copy.deepcopy(source)
        result,report=enrich(source,rows,numbers)
        op=[r for r in result if r[12]=='Operation'];self.assertEqual(1,len(op))
        self.assertLessEqual(dt(op[0][2]),dt(procedure['performedPeriod']['start']))
        self.assertGreaterEqual(dt(op[0][3]),dt(procedure['performedPeriod']['end']))
        self.assertEqual(original,source);self.assertEqual(['op'],report['operationSources'][0]['sourceProcedures'])
        procedure['code']['coding'][0]['code']='not-in-mapping'
        self.assertFalse(any(r[12]=='Operation'for r in enrich(source,rows,numbers)[0]))

    def test_unbounded_cases_are_reported_without_inventing_dates(self):
        source,rows,numbers=self.fixture();del source['entry'][0]['resource']['period']['end']
        result,report=enrich(source,rows,numbers)
        self.assertEqual(1,len(result));self.assertEqual(1,len(report['skipped']))

    def test_operative_mapping_uses_existing_source_codes(self):
        base=Path(__file__).resolve().parents[1]/'mappings'
        registry=json.loads((base/'synthea-source-code-registry.json').read_text())
        codes={e['code']for e in registry['entries']if e['system']=='http://snomed.info/sct'}
        self.assertLessEqual(json.loads((base/'synthea-operative-procedures.json').read_text())['procedures'].keys(),codes)
