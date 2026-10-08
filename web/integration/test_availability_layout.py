"""Verify ontology layouts and repeatable bytes in the official updater image."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest


SOURCE = Path("/opt/availability-updater/src/py/elastic_availability_generator.py")


def load(path):
    spec = importlib.util.spec_from_file_location("generator", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.ElasticAvailabilityGenerator


class LayoutTest(unittest.TestCase):
    def test_layouts_preserve_output_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            generator_class = load(SOURCE)
            input_dir = root / "input"
            input_dir.mkdir()
            context = {"system": "test", "code": "context"}
            (input_dir / "stratum-to-context.json").write_text(json.dumps({"test": context}))
            term = {"system": "test", "code": "term"}
            probe = generator_class(str(input_dir), str(root / "unused"), str(root))
            child = probe._contextualized_hash(context, term)
            report = {"group": [{"stratifier": [{"code": [{"coding": [{"code": "test"}]}],
                       "stratum": [{"value": {"coding": [term]}, "measureScore": {"value": 27}}]}]}]}
            (input_dir / "availability_report.json").write_text(json.dumps(report))
            outputs = []
            for name, cls, layout in [("legacy", generator_class, "elastic"),
                                      ("current", generator_class, "elastic/content"),
                                      ("repeat", generator_class, "elastic/content")]:
                ontology = root / name
                target = ontology / layout
                target.mkdir(parents=True)
                # Use the same reverse creation order in each layout.
                for number, node, data in [(1, child, {}), (0, "parent", {"children": [
                        {"contextualized_termcode_hash": child}]})]:
                    (target / f"onto_es__ontology_{number}.ndjson").write_text(
                        json.dumps({"index": {"_id": node}}) + "\n" + json.dumps(data) + "\n")
                output = root / (name + "-output")
                generator = cls(str(input_dir), str(output), str(ontology))
                generator.generate()
                records = list(generator._build_updates({}))
                self.assertEqual({"parent": 10, child: 10},
                                 {r[0]["update"]["_id"]: r[1]["doc"]["availability"] for r in records})
                outputs.append({p.name: p.read_bytes() for p in output.iterdir()})
            self.assertTrue(all(result == outputs[0] for result in outputs))

    def test_empty_ontology_fails_before_writing_updates(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "stratum-to-context.json").write_text("{}")
            for layout in ["elastic", "elastic/content"]:
                (root / layout).mkdir(exist_ok=True)
                output = root / "output"
                with self.assertRaisesRegex(RuntimeError, "No ontology nodes found"):
                    load(SOURCE)(str(root), str(output), str(root)).generate()
                self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
