import concurrent.futures
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import store


class QueueFixture(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.patch = patch.multiple(store, ROOT=self.root / 'data', APP=self.root / 'converter')
        self.patch.start()
        self.addCleanup(self.patch.stop)
        store.APP.mkdir()
        (store.APP / 'input').mkdir()
        (store.APP / store.SOURCES['starter']).write_bytes(b'workbook')
        (store.APP / 'defaults.config').write_text('CHECK_INPUT_CONSISTENCY=true')
        (store.APP / 'excel2fhir.jar').write_bytes(b'converter')


class QueueTests(QueueFixture):
    def test_snapshots_are_independent_of_source_and_profile_changes(self):
        first = store.create('starter', 'default')
        original = (store.ROOT / 'jobs' / first / 'snapshot.json').read_bytes()
        (store.APP / 'defaults.config').write_text('changed')
        (store.APP / store.SOURCES['starter']).write_bytes(b'changed')
        second = store.create('starter', 'default')
        self.assertNotEqual(first, second)
        self.assertEqual(original, (store.ROOT / 'jobs' / first / 'snapshot.json').read_bytes())
        self.assertEqual(b'workbook', (store.ROOT / 'jobs' / first / 'input.xlsx').read_bytes())

    def test_independent_claimers_never_receive_the_same_job(self):
        expected = {store.create('starter', 'default') for _ in range(8)}
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
            actual = list(pool.map(lambda _: store.claim(), range(12)))
        claimed = [job for job in actual if job]
        self.assertEqual(expected, set(claimed))
        self.assertEqual(len(expected), len(claimed))

    def test_cancelled_queued_jobs_are_not_claimed(self):
        job = store.create('starter', 'default')
        store.cancel(job)
        self.assertIsNone(store.claim())
        self.assertEqual('cancelled', store.get(job)['state'])

    def test_rejects_unknown_paths_and_profiles(self):
        for source, profile in [('../../etc/passwd', 'default'), ('starter', 'unknown')]:
            with self.assertRaises(ValueError):
                store.create(source, profile)
        self.assertEqual([], store.jobs())


if __name__ == '__main__':
    unittest.main()
