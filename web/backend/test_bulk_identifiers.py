import json
from pathlib import Path
import tempfile
import unittest

from bulk_identifiers import Registry, EXISTING


class IdentifierRegistryTests(unittest.TestCase):
    def test_collision_across_chunks_and_rebuilt_resume(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            first = root / 'first.jsonl'
            second = root / 'second.jsonl'
            def journal(path, owner, rule='patient-rule', value='constant'):
                path.write_text(json.dumps(dict(system='urn:test', value=value, owner=owner, rule=rule)) + '\n')
            journal(first, 'Patient/one')
            journal(second, 'Patient/two')
            for attempt in range(2):
                registry = Registry(root / 'index.sqlite')
                try:
                    registry.include(first)
                    registry.include(first)  # Repeated serialization of the same resource.
                    with self.assertRaisesRegex(ValueError, 'Patient/one and Patient/two'):
                        registry.include(second)
                    journal(second, 'Patient/two', value='unique')
                    registry.include(second)
                    journal(second, 'Patient/two')
                finally:
                    registry.close()

    def test_shared_source_identifiers_and_case_owners(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            journal = root / 'chunk.jsonl'
            registry = Registry(root / 'index.sqlite')
            def include(owner, rule, value):
                journal.write_text(json.dumps(dict(system='urn:test', value=value, owner=owner, rule=rule)) + '\n')
                registry.include(journal)
            try:
                include('Patient/one', EXISTING, 'source')
                include('Patient/two', EXISTING, 'source')
                with self.assertRaisesRegex(ValueError, 'Identifier collision'):
                    include('Patient/one', 'generated', 'source')
                include('case:one/facility', 'case-rule', 'case')
                include('case:one/facility', 'case-rule', 'case')
                with self.assertRaisesRegex(ValueError, 'Identifier collision'):
                    include('case:two/facility', 'case-rule', 'case')
                include('Patient/three', 'generated', 'mixed')
                with self.assertRaisesRegex(ValueError, 'Identifier collision'):
                    include('Patient/four', EXISTING, 'mixed')
            finally:
                registry.close()
