"""Optional local product mappings; public imports never require MMI data.

The catalogue is generated separately from licensed sources. No source data or
licence grant is bundled with this adapter. Load once per import, not per row.
"""
import copy
import hashlib
import json
from pathlib import Path
import re
from national_medication_mapping import NationalMedicationMapping

ROOT = Path(__file__).resolve().parents[1]
PZN = 'http://fhir.de/CodeSystem/ifa/pzn'
INGREDIENT_SYSTEMS = {'http://fhir.de/CodeSystem/ask': 'ASK',
                      'http://fdasis.nlm.nih.gov': 'UNII',
                      'http://snomed.info/sct': 'SNOMED CT (Version nicht angegeben)',
                      'http://www.nlm.nih.gov/research/umls/rxnorm': 'RxNorm'}


def local_catalog_path():
    return Path.home() / '.local/share/excel2fhir/medication-products.json'


def require_external_path(path):
    """Resolve symlinks as well; an ignored repo directory is still in the repo."""
    resolved = Path(path).resolve()
    if resolved.is_relative_to(ROOT):
        raise ValueError('Local product data must be stored outside the project repository: ' + str(path))
    return resolved


class ProductCatalog:
    def __init__(self):
        self.entries = {}
        self.national = NationalMedicationMapping()
        self.metadata = {'id': 'source-products-v1', 'provider': 'public-source',
                         'nationalMapping': self.national.metadata,
                         'description': 'Öffentliche deutsche ATC- und Produktzuordnungen'}
        path = local_catalog_path()
        if not path.exists():
            return
        require_external_path(path)
        raw = path.read_bytes()
        data = json.loads(raw)
        if data.get('schemaVersion') != 1 or data.get('provider') != 'mmi-local':
            raise ValueError('Unknown local product catalog format')
        for field in ('id', 'sourceVersion'):
            if not isinstance(data.get(field), str) or not data[field].strip():
                raise ValueError('Product catalog requires ' + field)
        if not isinstance(data.get('entries'), list):
            raise ValueError('Product catalog requires entries')
        registry = json.loads((ROOT / 'scripts/mappings/synthea-source-code-registry.json').read_text())
        allowed = {(e['system'], e['code']) for e in registry['entries']
                   if 'state-code:MedicationOrder' in e['usages']}
        for entry in data['entries']:
            source = entry.get('source', {})
            key = (source.get('system'), source.get('code'))
            if key not in allowed or source.get('version'):
                raise ValueError('Product mapping is outside the supported Synthea inventory: ' + str(key))
            if key in self.entries:
                raise ValueError('Mehrdeutige Produktzuordnung: ' + str(key))
            target = entry.get('target', {})
            if (target.get('system') != PZN or not isinstance(target.get('code'), str)
                    or not re.fullmatch(r'[0-9]{8}', target['code']) or target.get('version')):
                raise ValueError('Target product requires an eight-digit PZN as text')
            for field in ('display', 'doseForm'):
                if not isinstance(target.get(field), str) or not target[field].strip():
                    raise ValueError('Target product requires German ' + field)
            if target.keys() - {'system', 'code', 'display', 'doseForm', 'ingredient'}:
                raise ValueError('Target product contains unsupported properties')
            ingredient = target.get('ingredient')
            if ingredient is not None and (ingredient.get('system') not in INGREDIENT_SYSTEMS
                    or not isinstance(ingredient.get('code'), str) or not ingredient['code'].strip()
                    or ingredient.get('version')):
                raise ValueError('Unsupported ingredient coding')
            if entry.get('doseCompatibility') not in ('unchanged', 'unresolved'):
                raise ValueError('Product mapping requires doseCompatibility')
            provenance = entry.get('provenance', {})
            if any(not isinstance(provenance.get(f), str) or not provenance[f].strip()
                   for f in ('source', 'method', 'evidence')):
                raise ValueError('Product mapping requires a source, method and rationale')
            self.entries[key] = copy.deepcopy(entry)
        self.metadata = {'id': data['id'], 'provider': 'mmi-local',
                         'nationalMapping': self.national.metadata,
                         'sourceVersion': data['sourceVersion'],
                         'sha256': hashlib.sha256(raw).hexdigest()}

    def select(self, coding):
        result = self.national.select(coding)
        entry = self.entries.get((coding.get('system'), coding.get('code')))
        if entry is None or coding.get('version'):
            return result
        if entry['doseCompatibility'] != 'unchanged':
            result['reason'] += ' Lokale Zuordnung nicht angewendet: Dosierung bei Produktwechsel ungeklärt.'
            return result
        result.update(status='local-product', provider='mmi-local',
                      target=copy.deepcopy(entry['target']),
                      provenance=copy.deepcopy(entry['provenance']),
                      doseCompatibility=entry['doseCompatibility'],
                      reason='Lokales deutsches Produkt gemäß dokumentierter Zuordnung gewählt.',
                      redistribution='not-cleared')
        return result


def select_german_product(coding):
    return ProductCatalog().select(coding)


def require_external_output(report, output):
    if report.get('productDataUsage', {}).get('containsLocalProductData'):
        require_external_path(output)
