import copy
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from synthea_to_excel import prepare
from clinical_import import select_german_product
from test_synthea_import import bundle


class ClinicalImportTest(unittest.TestCase):
    def add(self, source, resource):
        resource.update(subject={'reference':'urn:uuid:p'}, encounter={'reference':'urn:uuid:e'})
        source['entry'].append({'fullUrl':'urn:uuid:'+resource['id'],'resource':resource})

    def test_components_preserve_zero_and_coded_answers_without_new_observation(self):
        source=bundle()
        self.add(source, {'resourceType':'Observation','id':'bp','status':'final',
            'category':[{'coding':[{'system':'http://terminology.hl7.org/CodeSystem/observation-category','code':'vital-signs'}]}],
            'code':{'coding':[{'system':'http://loinc.org','code':'85354-9'}]},
            'effectiveDateTime':'2026-09-01T08:00:00+02:00','component':[
                {'code':{'coding':[{'system':'http://loinc.org','code':'8480-6'}]},
                 'valueQuantity':{'value':0,'system':'http://unitsofmeasure.org','code':'mm[Hg]','unit':'mmHg'}}]})
        before=copy.deepcopy(source);rows,report=prepare(source)
        self.assertEqual(source,before)
        self.assertEqual(len(rows['Klinische Dokumentation']),2)
        self.assertEqual(rows['Klinische Dokumentation'][1][4],'0')
        self.assertEqual(rows['Klinische Dokumentation'][1][13],'bp')
        self.assertEqual(len(report['clinicalImports']),1)
        source['entry'][-1]['resource']['component'][0]['valueQuantity']['system']='unknown'
        rows,report=prepare(source)
        self.assertEqual(rows['Klinische Dokumentation'],[])
        self.assertTrue(any(x['path']=='$'for x in report['losses']if x['resourceType']=='Observation'))

    def test_product_selection_preserves_source_without_local_mapping(self):
        coding={'system':'http://www.nlm.nih.gov/research/umls/rxnorm','code':'123'}
        before=copy.deepcopy(coding);decision=select_german_product(coding)
        self.assertEqual(coding,before);self.assertEqual(decision['status'],'unmapped');self.assertIsNone(decision['target'])

    def test_report_does_not_keep_dangling_result_references(self):
        source=bundle()
        self.add(source,{'resourceType':'DiagnosticReport','id':'r','status':'final',
            'code':{'coding':[{'system':'http://loinc.org','code':'test'}]},'result':[{'reference':'urn:uuid:missing'}]})
        rows,report=prepare(source)
        self.assertEqual(rows['Befundbericht'][0][9],'')
        self.assertTrue(any(x['path']=='result'for x in report['losses']))

    def test_broken_patient_reference_is_not_silently_ignored(self):
        source=bundle()
        self.add(source,{'resourceType':'Procedure','id':'bad','status':'completed','code':{'coding':[{'system':'http://snomed.info/sct','code':'123'}]}})
        source['entry'][-1]['resource']['subject']['reference']='urn:uuid:another-patient'
        with self.assertRaises(ValueError):prepare(source)

    def test_boolean_laboratory_value_is_reported_without_partial_rows(self):
        source = bundle()
        self.add(source, {'resourceType':'Observation', 'id':'bool-lab', 'status':'final',
            'category':[{'coding':[{'system':'http://terminology.hl7.org/CodeSystem/observation-category', 'code':'laboratory'}]}],
            'code':{'coding':[{'system':'http://loinc.org', 'code':'1234-5'}]}, 'valueBoolean':True})
        rows, report = prepare(source)
        self.assertEqual(rows['Laborbefund'], [])
        self.assertTrue(any('Ja/Nein' in str(loss) for loss in report['losses']))

if __name__=='__main__':unittest.main()
