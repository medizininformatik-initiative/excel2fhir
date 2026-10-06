"""Apply the reviewed ontology-layout correction to the pinned upstream source."""
import hashlib
from pathlib import Path


def patch(path):
    source = path.read_bytes()
    expected = "1aeeb5f7e4a6fdd0f45decf19ad83215e08a1f474555fb1e7a378339fc3d03a8"
    if hashlib.sha256(source).hexdigest() != expected:
        raise RuntimeError("Upstream updater source changed; review the local patch before building")
    text = source.decode("utf-8")
    text = text.replace(
        '        elastic_dir = self.ontology_dir / "elastic"\n',
        '        elastic_dir = self.ontology_dir / "elastic"\n'
        '        if (elastic_dir / "content").is_dir():\n'
        '            elastic_dir = elastic_dir / "content"\n',
    )
    text = text.replace(
        'elastic_dir.glob("*onto_es__ontology*"):',
        'sorted(elastic_dir.glob("*onto_es__ontology*")):',
    )
    text = text.replace(
        '        log.info("Loaded %d ontology nodes", len(self.availability))',
        '        if not self.availability:\n'
        '            raise ValueError(f"No ontology nodes found in {elastic_dir}")\n\n'
        '        log.info("Loaded %d ontology nodes", len(self.availability))',
    )
    path.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    patch(Path("/opt/availability-updater/src/py/elastic_availability_generator.py"))
