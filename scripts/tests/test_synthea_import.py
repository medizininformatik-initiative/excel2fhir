import copy
from datetime import datetime
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from synthea_to_excel import prepare, SNOMED
from check_synthea_roundtrip import check


def bundle():
    resources = [
        {'resourceType':'Patient','id':'p','name':[{'family':'Example','given':['Test']}],
         'gender':'female','birthDate':'1980-01-01'},
        {'resourceType':'Encounter','id':'e','subject':{'reference':'urn:uuid:p'},
         'class':{'system':'http://terminology.hl7.org/CodeSystem/v3-ActCode','code':'EMER'},
         'period':{'start':'2026-09-01T08:00:00+02:00','end':'2026-09-01T09:00:00+02:00'}},
        {'resourceType':'Condition','id':'c','subject':{'reference':'urn:uuid:p'},
         'encounter':{'reference':'urn:uuid:e'},'code':{'coding':[{'system':SNOMED,'code':'00123','display':'Original'}]},
         'recordedDate':'2026-09-01T08:00:00+02:00','onsetDateTime':'2020-01',
         'clinicalStatus':{'coding':[{'system':'http://terminology.hl7.org/CodeSystem/condition-clinical','code':'active'}]}},
        {'resourceType':'Procedure','id':'x','status':'completed'}]
    return {'resourceType':'Bundle','entry':[{'fullUrl':'urn:uuid:'+r['id'],'resource':r}for r in resources]}


class ImportTest(unittest.TestCase):
    def test_contact_times_keep_both_instants_at_autumn_clock_change(self):
        source = bundle()
        period = {'start': '1992-09-27T00:43:12+00:00', 'end': '1992-09-27T01:43:12+00:00'}
        source['entry'][1]['resource']['period'] = period
        rows, _ = prepare(source)
        for value, expected in zip(rows['Fall'][0][2:4], period.values()):
            self.assertEqual(datetime.fromisoformat(value), datetime.fromisoformat(expected))

    def test_keeps_codes_times_references_and_reports_excluded_resources(self):
        source=bundle();before=copy.deepcopy(source)
        rows,report=prepare(source)
        self.assertEqual(source,before)
        self.assertEqual(rows['Fall'][0][4],'ambulant')
        self.assertEqual(rows['Fall'][0][9],'Notfall')
        self.assertEqual(report['encounterMappings'][0]['admissionReasonFourthComponent'],'7')
        self.assertEqual(rows['Diagnose'][0][1],'1')
        self.assertEqual(rows['Diagnose'][0][3],'00123')
        self.assertEqual(rows['Diagnose'][0][8],'2020-01')
        self.assertEqual(rows['Diagnose'][0][10],'Aktiv')
        self.assertEqual(report['importedConditions'],1)
        self.assertTrue(any(x['resourceType']=='Procedure'for x in report['losses']))

    def test_rejects_broken_reference_and_unknown_version(self):
        source=bundle();source['entry'][2]['resource']['encounter']['reference']='urn:uuid:missing'
        with self.assertRaises(ValueError):prepare(source)
        source=bundle();source['entry'][2]['resource']['code']['coding'][0]['version']='unverified-release'
        with self.assertRaises(ValueError):prepare(source)

    def test_never_emits_administrative_shell_or_relabels_unknown_encounter_class(self):
        source=bundle();source['entry']=[x for x in source['entry']if x['resource']['resourceType']!='Condition']
        with self.assertRaises(ValueError):prepare(source)
        source=bundle();source['entry'][1]['resource']['class']['code']='UNKNOWN'
        with self.assertRaises(ValueError):prepare(source)

    def test_additional_gm_coding_is_one_diagnosis(self):
        source=bundle();source['entry'][2]['resource']['code']['coding'].append(
            {'system':'http://fhir.de/CodeSystem/bfarm/icd-10-gm','version':'2026','code':'A01'})
        rows,report=prepare(source)
        self.assertEqual(len(rows['Diagnose']),1)
        self.assertEqual(rows['Diagnose'][0][3:5],['A01','ICD-10-GM 2026'])

    def test_roundtrip_detects_missing_emergency_reason_and_extra_encounters(self):
        source = bundle()
        _, report = prepare(source)
        target = copy.deepcopy(source)
        target['entry'] = target['entry'][:3]
        target['entry'][0]['resource']['name'] = [report['demographics']['name']]
        target['entry'][0]['resource']['address'] = [dict(report['demographics']['address'], state='DE-NW')]
        encounter = target['entry'][1]['resource']
        encounter['id'] = 'p-E-1'
        encounter['class']['code'] = 'AMB'
        target['entry'][2]['resource']['encounter']['reference'] = 'Encounter/p-E-1'
        encounter['extension'] = [{
            'url': 'http://fhir.de/StructureDefinition/Aufnahmegrund',
            'extension': [{'url': 'VierteStelle', 'valueCoding': {
                'system': 'http://fhir.de/CodeSystem/dkgev/AufnahmegrundVierteStelle', 'code': '7'}}]}]
        self.assertEqual(check(source, target, report)['emergencyMappings'], 1)
        incomplete = copy.deepcopy(target)
        del incomplete['entry'][1]['resource']['extension']
        with self.assertRaises(AssertionError):
            check(source, incomplete, report)
        extra = copy.deepcopy(target['entry'][1])
        extra['resource']['id'] = 'unexpected'
        target['entry'].append(extra)
        with self.assertRaises(AssertionError):
            check(source, target, report)

if __name__=='__main__':unittest.main()
