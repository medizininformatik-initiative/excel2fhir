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
        patterns=set()
        for row in result:
            self.assertEqual(11,len(row))
            if row[3]: self.assertLessEqual(dt(row[2]),dt(row[3]))
        for number in numbers:
            care=[r for r in result if r[1]==number and r[10] in ('Normalstationär','Intensivstationär')]
            for left,right in zip(care,care[1:]): self.assertEqual(dt(left[3]),dt(right[2]))
            patterns.add(tuple((r[5],r[6],r[7],r[8],r[10])for r in care))
        self.assertGreater(len(patterns),10)

    def test_only_known_operations_create_op_contacts_and_source_times_stay_unchanged(self):
        source,rows,numbers=self.fixture()
        procedure={'resourceType':'Procedure','id':'op','encounter':{'reference':'urn:uuid:1'},
            'code':{'coding':[{'system':'http://snomed.info/sct','code':'232717009'}]},
            'performedPeriod':{'start':'2026-01-02T10:00:00Z','end':'2026-01-02T13:00:00Z'}}
        source['entry'].append({'resource':procedure});original=copy.deepcopy(source)
        result,report=enrich(source,rows,numbers)
        op=[r for r in result if r[10]=='Operation'];self.assertEqual(1,len(op))
        self.assertLessEqual(dt(op[0][2]),dt(procedure['performedPeriod']['start']))
        self.assertEqual('',op[0][3])
        primary=next(r for r in result if r[10] in ('Normalstationär','Intensivstationär') and dt(r[2])<=dt(op[0][2])<dt(r[3]))
        self.assertTrue(primary[8])
        self.assertEqual(primary[3],report['operationSources'][0]['derivedEnd'])
        self.assertEqual(original,source);self.assertEqual(['op'],report['operationSources'][0]['sourceProcedures'])
        procedure['code']['coding'][0]['code']='not-in-mapping'
        self.assertFalse(any(r[10]=='Operation'for r in enrich(source,rows,numbers)[0]))

    def test_ambulatory_operation_has_primary_without_secondary_kind(self):
        source, rows, numbers = self.fixture()
        source['entry'][0]['resource']['class']['code'] = 'AMB'
        rows[0][4] = 'ambulant'
        source['entry'].append({'resource': {'resourceType': 'Procedure', 'id': 'op',
            'encounter': {'reference': 'urn:uuid:1'},
            'code': {'coding': [{'system': 'http://snomed.info/sct', 'code': '232717009'}]},
            'performedDateTime': '2026-01-02T10:00:00Z'}})
        result, report = enrich(source, rows, numbers)
        operation_index = next(i for i, r in enumerate(result) if r[10] == 'Operation')
        primary = result[operation_index - 1]
        self.assertEqual('', primary[10])
        self.assertTrue(primary[8])
        self.assertEqual(primary[3], report['operationSources'][0]['derivedEnd'])

    def test_unbounded_cases_are_reported_without_inventing_dates(self):
        source,rows,numbers=self.fixture();del source['entry'][0]['resource']['period']['end']
        result,report=enrich(source,rows,numbers)
        self.assertEqual(1,len(result));self.assertEqual(1,len(report['skipped']))

    def test_operative_mapping_uses_existing_source_codes(self):
        base=Path(__file__).resolve().parents[1]/'mappings'
        registry=json.loads((base/'synthea-source-code-registry.json').read_text())
        codes={e['code']for e in registry['entries']if e['system']=='http://snomed.info/sct'}
        self.assertLessEqual(json.loads((base/'synthea-operative-procedures.json').read_text())['procedures'].keys(),codes)
