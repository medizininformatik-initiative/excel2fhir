import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import unittest
from transfer_findings import notes


class TransferNotesTest(unittest.TestCase):
    def test_disabled_resources_and_overwritten_fields_are_not_mapping_gaps(self):
        source = {'entry': [{'resource': {'resourceType': 'Condition', 'id': 'c'}}]}
        loss = {'resourceType': 'Condition', 'id': 'c', 'path': 'clinicalStatus', 'reason': 'source detail'}
        report = {'losses': [loss]}
        for options in ({'CONDITION_ENABLED': 'false'}, {'DAR_CONDITION_CLINICAL_STATUS': 'made-up'}):
            result = notes(source, report, options)
            self.assertEqual([], result['importNotes'])
            self.assertEqual([loss], result['configuredOmissions'])
        result = notes(source, report, {'DAR_CONDITION_CLINICAL_STATUS': 'made-up',
                                       'DAR_CONDITION_CLINICAL_STATUS_ONLY_WHEN_MISSING': 'true'})
        self.assertEqual([loss], result['importNotes'])
        self.assertEqual([loss], notes(source, report, {})['importNotes'])
