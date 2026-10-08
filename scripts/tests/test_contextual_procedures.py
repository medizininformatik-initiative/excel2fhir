import copy
import itertools
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from procedure_projection import project
from procedure_mapping import DATA
from contextual_procedures import choose, RULES, summary
from audit_procedure_mapping import validate_structure
from audit_contextual_procedures import check as audit_contextual, check_conflict
from test_synthea_import import bundle

SCT = 'http://snomed.info/sct'
OPS = 'http://fhir.de/CodeSystem/bfarm/ops'


def source(code, identifier='procedure', sex='female', birth='1980-01-01', hours=48):
    result = bundle()['entry'][:3]
    result[0]['resource'].update(gender=sex, birthDate=birth)
    result[1]['resource']['period']['end'] = '2026-09-03T08:00:00+02:00' if hours == 48 else '2026-09-01T09:00:00+02:00'
    result.append({'fullUrl': 'urn:uuid:' + identifier, 'resource': {
        'resourceType': 'Procedure', 'id': identifier, 'status': 'completed',
        'subject': {'reference': 'urn:uuid:p'}, 'encounter': {'reference': 'urn:uuid:e'},
        'code': {'coding': [{'system': SCT, 'code': code}]},
        'performedPeriod': {'start': '2026-09-01T08:00:00+02:00', 'end': '2026-09-01T08:30:00+02:00'}}})
    return result


def ops(decision):
    return [c['code'] for o in decision['outputs'] for c in o['codings'] if c['system'] == OPS]


class ContextualProceduresTest(unittest.TestCase):
    def test_catalogue_provenance_and_rule_structure_are_required(self):
        self.assertEqual(validate_structure(DATA), 429)
        self.assertEqual(sum('contextualRule' in e for e in DATA['entries']), 261)
        changed = copy.deepcopy(DATA)
        changed['entries'][0]['provenance'].pop('sourceEvidence')
        with self.assertRaises(ValueError): validate_structure(changed)
        changed = copy.deepcopy(DATA)
        changed['contextualRules']['echo']['candidates'][0]['weight'] = 0
        with self.assertRaises(ValueError): validate_structure(changed)

    def test_existing_and_new_maternal_codes_reject_male_newborn_and_unknown(self):
        for code in ('177157003', '31208007', '169230002', '386216000', '1263416007', '287664005'):
            for sex, birth in [('male', '1980-01-01'), ('female', '2026-09-01'), ('unknown', '1980-01-01')]:
                with self.subTest(code=code, sex=sex, birth=birth):
                    d = project(source(code, sex=sex, birth=birth))['procedure']
                    self.assertEqual(d['status'], 'context-conflict')
                    self.assertEqual(d['outputs'], [])
        self.assertIn('9-261', ops(project(source('31208007'))['procedure']))

    def test_male_reproductive_source_rejects_female(self):
        self.assertEqual(project(source('65575008'))['procedure']['status'], 'context-conflict')
        self.assertEqual(ops(project(source('65575008', sex='male'))['procedure']), ['1-464.00'])

    def test_reproductive_history_blocks_later_pregnancy_not_its_own_operation(self):
        s = source('31208007')
        s.insert(3, {'resource': {'resourceType': 'Procedure', 'id': 'hyst', 'status': 'completed',
                                'subject': {'reference': 'Patient/p'}, 'encounter': {'reference': 'Encounter/e'},
                                'code': {'coding': [{'system': SCT, 'code': 'fake-hyst', 'display': 'Hysterectomy'}]},
                                'performedDateTime': '2025-01-01T00:00:00Z'}})
        self.assertEqual(project(s)['procedure']['status'], 'context-conflict')

    def test_weighted_choice_is_order_independent_and_has_genuine_variation(self):
        s = source('40701008')
        d = project(s)['procedure']
        selection = d['contextualSelection']['selection']
        for permutation in itertools.permutations(s):
            self.assertEqual(project(list(permutation))['procedure']['contextualSelection']['selection'], selection)
        other = source('241149003', 'other')[-1]
        self.assertEqual(project(s + [other])['procedure']['contextualSelection']['selection'], selection)
        before = copy.deepcopy(s)
        project(s)
        self.assertEqual(s, before)
        candidates = RULES['echo']['candidates']
        chosen = {choose('p', str(i), {'system': SCT, 'code': '40701008'}, 'echo', candidates)[0]['code'] for i in range(100)}
        self.assertEqual(chosen, {'3-034', '3-031'})
        self.assertEqual(choose('p', 'x', {'system': SCT, 'code': '40701008'}, 'echo', candidates),
                         choose('p', 'x', {'system': SCT, 'code': '40701008'}, 'echo', list(reversed(candidates))))

    def test_linked_injury_region_wins_over_other_encounter_diagnoses(self):
        s = source('288086009')
        s[2]['resource']['code'] = {'coding': [{'system': SCT, 'code': '284549007', 'display': 'Laceration of hand'}]}
        s[-1]['resource']['reasonReference'] = [{'reference': 'urn:uuid:c'}]
        other = copy.deepcopy(s[2]);other['fullUrl'] = 'urn:uuid:other';other['resource']['id'] = 'other'
        other['resource']['code'] = {'coding': [{'system': SCT, 'code': '284551006', 'display': 'Laceration of foot'}]}
        self.assertEqual(ops(project(s + [other])['procedure']), ['5-900.09'])
        self.assertEqual(ops(project(source('288086009'))['procedure']), [])

    def test_contradictory_wound_sites_and_cross_patient_reason_do_not_select(self):
        s = source('288086009')
        s[-1]['resource']['reasonCode'] = [{'coding': [{'system': SCT, 'code': '284549007'}, {'system': SCT, 'code': '284551006'}]}]
        self.assertEqual(ops(project(s)['procedure']), [])
        s = source('288086009')
        s[2]['resource']['subject']['reference'] = 'Patient/another'
        s[-1]['resource']['reasonReference'] = [{'reference': 'urn:uuid:c'}]
        with self.assertRaises(ValueError): project(s)

    def test_education_has_real_duration_and_retains_original_source_separately(self):
        s = source('223484005')
        d = project(s)['procedure']
        self.assertEqual(ops(d), ['9-500.0'])
        self.assertEqual(len(d['outputs']), 2)
        self.assertEqual(d['outputs'][0]['codings'][0]['system'], SCT)
        self.assertEqual(d['outputs'][1]['end'], '2026-09-01T10:00:00+02:00')
        self.assertEqual(len(d['outputs'][1]['codings']), 1)
        self.assertEqual(ops(project(source('223484005', hours=1))['procedure']), [])

    def test_companions_merge_without_merging_original_services(self):
        s = source('14152002', 'b')
        s.append(source('43060002', 'a')[-1])
        result = project(s)
        self.assertEqual(sum(len(ops(d)) for d in result.values()), 1)
        self.assertEqual(result['a']['outputs'][1]['sourceIds'], ['a', 'b'])
        self.assertEqual(result['b']['sharedOutputs'][0]['ownerSourceId'], 'a')
        self.assertEqual(summary(result)['withOps'], 2)
        self.assertEqual(summary(result)['companionOnly'], 2)
        self.assertEqual(summary(result)['opsRows'], 1)
        self.assertEqual(project(list(reversed(s))), result)

    def test_existing_ops_is_reused_across_equivalent_time_offsets(self):
        s = source('40701008', 'synthetic')
        s[2]['resource']['code'] = {'coding': [{'system': SCT, 'code': 'shock', 'display': 'Cardiogenic shock'}]}
        existing = source('3-034', 'existing')[-1]
        existing['resource']['code']['coding'][0]['system'] = OPS
        existing['resource']['performedPeriod']['start'] = '2026-09-01T06:00:00Z'
        result = project(s + [existing])
        self.assertEqual(result['synthetic']['outputs'], [])
        self.assertEqual(result['existing']['outputs'][0]['sourceIds'], ['existing', 'synthetic'])
        self.assertEqual(result['synthetic']['sharedOutputs'], [{'ownerSourceId': 'existing', 'code': '3-034'}])

    def test_invalid_events_do_not_enter_chemotherapy_blocks(self):
        s = source('367336001', 'valid')
        s[0]['resource']['deceasedDateTime'] = '2026-09-01T12:00:00+02:00'
        invalid = source('367336001', 'after-death')[-1]
        invalid['resource']['performedPeriod'] = {'start': '2026-09-02T08:00:00+02:00'}
        result = project(s + [invalid])
        self.assertEqual(result['after-death']['status'], 'context-conflict')
        self.assertEqual(result['valid']['chemotherapyBlock']['sourceIds'], ['valid'])
        self.assertEqual(summary(result)['withOps'], 1)

    def test_synthetic_course_has_five_units_and_a_single_course_ops(self):
        s = source('91251008')
        s[1]['resource']['period']['end'] = '2026-09-08T08:00:00+02:00'
        d = project(s)['procedure']
        self.assertEqual(ops(d), ['8-561.1'])
        self.assertEqual(len(d['contextualSelection']['syntheticSchedule']), 5)
        self.assertEqual(d['outputs'][0]['end'], '2026-09-05T08:30:00+02:00')

    def test_explicit_both_regions_are_emitted_together(self):
        self.assertEqual(set(ops(project(source('1290953004'))['procedure'])), {'3-203', '3-206'})

    def test_no_unstable_stress_or_cooperative_test(self):
        s = source('40701008')
        s[2]['resource']['code'] = {'coding': [{'system': SCT, 'code': 'fake-shock', 'display': 'Cardiogenic shock'}]}
        self.assertEqual(ops(project(s)['procedure']), ['3-034'])
        self.assertEqual(ops(project(source('65710008'))['procedure']), [])

    def test_new_rules_do_not_reinterpret_version_or_conflicting_display(self):
        for field, value in [('version', 'unknown'), ('display', 'Wrong procedure')]:
            s = source('40701008');s[-1]['resource']['code']['coding'][0][field] = value
            self.assertEqual(ops(project(s)['procedure']), [])
        s = source('40701008');s[-1]['resource']['status'] = 'not-done'
        self.assertEqual(ops(project(s)['procedure']), [])

    def test_missing_age_cannot_select_geriatrics(self):
        s = source('165197003');s[0]['resource'].pop('birthDate')
        d = project(s)['procedure']
        self.assertNotIn('1-770', ops(d))
        self.assertTrue(any(c['code'] == '1-770' for c in d['contextualSelection']['rejectedCandidates']))

    def test_after_death_is_withheld_and_reference_aliases_are_supported(self):
        s = source('40701008');s[0]['resource']['deceasedDateTime'] = '2026-08-01T00:00:00Z'
        self.assertEqual(project(s)['procedure']['status'], 'context-conflict')
        s = source('40701008');s[-1]['resource']['subject']['reference'] = 'Patient/p'
        s[-1]['resource']['encounter']['reference'] = 'Encounter/e'
        self.assertTrue(ops(project(s)['procedure']))

    def test_independent_audit_requires_evidence_for_omissions(self):
        for sex in ('male', 'female'):
            s = source('31208007', sex=sex)
            index = {e['fullUrl']: e['resource'] for e in s}
            d = project(s)['procedure']
            if sex == 'male':
                check_conflict(s[-1]['resource'], d, index, DATA)
            else:
                d.update(outputs=[], status='context-conflict', reason='Invented conflict')
                with self.assertRaises(AssertionError):
                    check_conflict(s[-1]['resource'], d, index, DATA)

    def test_independent_audit_rejects_tampered_choice_duration_and_companion(self):
        for code in ('40701008', '223484005'):
            s = source(code);d = project(s)['procedure']
            index = {e['fullUrl']: e['resource'] for e in s}
            entry = next(e for e in DATA['entries'] if e['sourceCode'] == code)
            audit_contextual(s[-1]['resource'], entry, d, index, {'procedure': d}, DATA)
            bad = copy.deepcopy(d)
            bad['contextualSelection']['selection']['sha256'] = '0'*64
            with self.assertRaises(AssertionError): audit_contextual(s[-1]['resource'], entry, bad, index, {'procedure': bad}, DATA)
            if code == '223484005':
                bad = copy.deepcopy(d);bad['outputs'][1]['end'] = '2026-09-01T08:30:00+02:00'
                with self.assertRaises(AssertionError): audit_contextual(s[-1]['resource'], entry, bad, index, {'procedure': bad}, DATA)
                bad = copy.deepcopy(d);bad['outputs'][1]['codings'].append(bad['outputs'][0]['codings'][0])
                with self.assertRaises(AssertionError): audit_contextual(s[-1]['resource'], entry, bad, index, {'procedure': bad}, DATA)


if __name__ == '__main__':
    unittest.main()
