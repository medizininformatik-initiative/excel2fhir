import sys
from pathlib import Path
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from check_clinical_references import matching_contact


def contact(identifier, level, start, end, kind='IMP', secondary=None):
    levels = {'einrichtungskontakt': 'facility', 'abteilungskontakt': 'department', 'versorgungsstellenkontakt': 'ward-service'}
    return {'id': identifier, 'level': levels[level], 'start': start, 'end': end, 'cls': kind, 'kind': secondary or ''}


def configured_condition_reference(condition, resources, level):
    return matching_contact({r['id']: r for r in resources}, [condition.get('recordedDate')], level, {})



class ConfiguredConditionReferenceTests(unittest.TestCase):
    def test_level_timestamp_and_inpatient_preference(self):
        condition = {'recordedDate': '2026-09-12T12:00:00+02:00'}
        start, end = '2026-09-12T08:00:00+02:00', '2026-09-12T16:00:00+02:00'
        resources = [contact('facility', 'einrichtungskontakt', start, end),
                     contact('department', 'abteilungskontakt', start, end),
                     contact('ward', 'versorgungsstellenkontakt', start, end),
                     contact('operation', 'versorgungsstellenkontakt', start, end, secondary='operation'),
                     contact('ambulatory', 'abteilungskontakt', '2026-09-12T11:00:00+02:00', end, 'AMB')]
        for level, expected in [('facility', 'facility'), ('department', 'department'), ('ward-service', 'ward')]:
            self.assertEqual('Encounter/' + expected, configured_condition_reference(condition, resources, level))
        self.assertEqual('', configured_condition_reference(condition, resources, 'none'))
        self.assertEqual('', configured_condition_reference({'recordedDate': '2026-09-13T12:00:00+02:00'}, resources, 'department'))

    def test_missing_level_does_not_fall_back_to_facility(self):
        resources = [contact('facility', 'einrichtungskontakt', '2026-09-12T08:00:00+02:00', '2026-09-12T16:00:00+02:00')]
        self.assertEqual('', configured_condition_reference({'recordedDate': '2026-09-12T12:00:00+02:00'}, resources, 'department'))
