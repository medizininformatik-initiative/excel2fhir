import unittest
from collections import Counter
from copy import deepcopy
from synthea_consent import assignments, enrich, GROUPS, history_window

class ConsentTests(unittest.TestCase):
    def test_exact_quotas_order_independence_and_seed(self):
        ids = [f'p{i}' for i in range(100)]
        first = assignments(ids, '42')
        self.assertEqual(Counter(first.values()), {'full':50,'patient-data':15,'patient-and-insurance':15,'deny':10,'absent':10})
        self.assertEqual(first, assignments(reversed(ids), '42'))
        self.assertNotEqual(first, assignments(ids, '43'))
        self.assertEqual(17, len(assignments(ids[:17], '42')))
        self.assertEqual({}, assignments([], '42'))
        with self.assertRaises(ValueError): assignments(['p1','p1'], 42)

    def test_consent_fields_date_and_preservation(self):
        bundle = {'entry':[{'resource':{'resourceType':'Encounter','class':{'code':kind},'period':{'start':date}}}
                           for kind,date in [('AMB','2020-01-01'),('IMP','2022-03-04'),('IMP','2023-01-01')]]}
        for variant, expected in [('full',['ja']*5),('patient-data',['ja']+['nein']*4),
                                  ('patient-and-insurance',['ja']*3+['nein']*2),('deny',['nein']*5)]:
            rows = {'Person':[{'Patient-ID':'p1'}]}
            report = enrich(rows,bundle,variant,42)
            self.assertEqual('2022-03-04', rows['Person'][0]['Datum Einwilligung'])
            self.assertEqual(expected,[rows['Person'][0][key] for key in GROUPS])
            self.assertEqual(variant,report['variant'])
            saved = deepcopy(rows)
            enrich(rows,bundle,'full',43)
            self.assertEqual(saved,rows)
        rows = {'Person':[{'Patient-ID':'p1'}]}
        enrich(rows,bundle,'absent',42)
        self.assertEqual({'Person':[{'Patient-ID':'p1'}]},rows)
        self.assertNotIn('date', enrich(rows,{'entry':[]},'full',42))

    def test_history_prefers_first_inpatient_and_excludes_old_and_future_contacts(self):
        encounters = [('IMP', '1911-01-01'), ('AMB', '2023-10-16'),
                      ('IMP', '2024-03-04'), ('IMP', '2025-01-01'), ('IMP', '2027-01-01')]
        bundle = {'entry': [{'resource': {'resourceType': 'Encounter', 'class': {'code': kind},
                    'period': {'start': value}}} for kind, value in encounters]}
        rows = {'Person': [{'Patient-ID': 'p1'}]}
        report = enrich(rows, bundle, 'full', 42, end_date='2026-10-16', years_of_history=3)
        self.assertEqual('2024-03-04', report['date'])
        self.assertEqual('2023-10-16', report['historyStart'])
        self.assertEqual('first-inpatient-encounter', report['dateSource'])
        bundle['entry'] = [bundle['entry'][0], bundle['entry'][1], bundle['entry'][-1]]
        rows = {'Person': [{'Patient-ID': 'p1'}]}
        report = enrich(rows, bundle, 'full', 42, end_date='2026-10-16', years_of_history=3)
        self.assertEqual('2023-10-16', report['date'])
        self.assertEqual('first-encounter', report['dateSource'])

    def test_full_history_end_boundary_leap_year_and_order_independence(self):
        self.assertEqual('2021-02-28', history_window('2024-02-29', 3)[0].isoformat())
        bundle = {'entry': [{'resource': {'resourceType': 'Encounter', 'class': {'code': 'IMP'},
                    'period': {'start': value}}} for value in ('2026-10-16', '2026-10-17', '1911-01-01')]}
        for years, expected in [(0, '1911-01-01'), (3, '2026-10-16')]:
            for entries in (bundle['entry'], list(reversed(bundle['entry']))):
                report = enrich({'Person': [{'Patient-ID': 'p1'}]}, {'entry': entries}, 'deny', 42,
                                end_date='2026-10-16', years_of_history=years)
                self.assertEqual(expected, report['date'])

    def test_fallback_stays_within_history_and_lifetime(self):
        for patient, expected in [
                ({'birthDate': '1940-01-01'}, '2023-10-16'),
                ({'birthDate': '2024-01-01'}, '2024-01-01'),
                ({'birthDate': '1940-01-01', 'deceasedDateTime': '2024-05-01T10:00:00Z'}, '2023-10-16'),
                ({'birthDate': '1940-01-01', 'deceasedDateTime': '2020-05-01T10:00:00Z'}, None),
                ({'birthDate': '2027-01-01'}, None),
                ({'birthDate': '1940-01-01', 'deceasedBoolean': True}, None)]:
            rows = {'Person': [{'Patient-ID': 'p1'}]}
            bundle = {'entry': [{'resource': {'resourceType': 'Patient', **patient}},
                      {'resource': {'resourceType': 'Encounter', 'class': {'code': 'IMP'},
                                    'period': {'start': '1911-01-01'}}}]}
            record = enrich(rows, bundle, 'full', 42, end_date='2026-10-16', years_of_history=3)
            self.assertEqual(expected, record.get('date'))
            self.assertEqual(expected, rows['Person'][0].get('Datum Einwilligung'))
            if expected is None:
                self.assertFalse(any(key in rows['Person'][0] for key in GROUPS))

if __name__ == '__main__': unittest.main()
