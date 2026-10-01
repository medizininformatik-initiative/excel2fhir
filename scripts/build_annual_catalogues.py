#!/usr/bin/env python3
"""Build the reviewed subset: build_annual_catalogues.py CATALOGUE_DIRECTORY OUTPUT.json.

Download CodeSystem-{icd10gm,ops,atcgm}-{2025,2026}.json from
https://terminologien.bfarm.de/rendering_data/ into the catalogue directory.
Only mapped codes are retained. Unknown/nonterminal targets fail the build.
"""
import hashlib
import json
from pathlib import Path
import sys

MAPS = Path(__file__).parent / 'mappings'
NOTICES = json.loads((Path(__file__).parent / 'terminology-notices.json').read_text())
SYSTEMS = {'icd10gm': 'http://fhir.de/CodeSystem/bfarm/icd-10-gm',
           'ops': 'http://fhir.de/CodeSystem/bfarm/ops',
           'atcgm': 'http://fhir.de/CodeSystem/bfarm/atc'}
# Reviewed broader 2025 terminal categories for the 2026 subdivisions.
REPLACEMENTS = {'R53.9': 'R53', 'R73.08': 'R73.0', 'R76.88': 'R76.8', 'Z98.88': 'Z98.8'}


def build(directory):
    codes = {system: set() for system in SYSTEMS.values()}
    def collect(value):
        if isinstance(value, dict):
            if value.get('system') in codes and value.get('code'):
                codes[value['system']].add(value['code'])
            if value.get('atc'):
                codes[SYSTEMS['atcgm']].add(value['atc']['code'])
            for item in value.values(): collect(item)
        elif isinstance(value, list):
            for item in value: collect(item)
    mappings = {}
    for path in sorted(MAPS.glob('*-2026.json')):
        raw = path.read_bytes(); collect(json.loads(raw))
        mappings[path.name] = hashlib.sha256(raw).hexdigest()
    result = {'schemaVersion': 1, 'sourceMappings': mappings, 'catalogues': {},
              'scope': 'Offline annual targets for synthetic mappings. Clinical equivalence remains approximate.',
              'copyright': 'Third-party classification notices are included with each catalogue.'}
    for name, system in SYSTEMS.items():
        for year in (2025, 2026):
            raw = (Path(directory) / f'{name}-{year}.json').read_bytes()
            rows = {r['Code']: r for r in json.loads(raw)['rows']}
            targets = {}
            for code in sorted(codes[system]):
                target = REPLACEMENTS.get(code, code) if name == 'icd10gm' and year == 2025 else code
                row = rows[target]
                if row.get('child'): raise ValueError('Nonterminal target: ' + target)
                targets[code] = {'code': target, 'display': row['Display'],
                                 'reason': ('Broader terminal category in the 2025 catalogue.' if target != code
                                            else 'Same code verified in the selected annual catalogue.')}
            result['catalogues'][system + '|' + str(year)] = {
                'url': f'https://terminologien.bfarm.de/rendering_data/CodeSystem-{name}-{year}.json',
                'sha256': hashlib.sha256(raw).hexdigest(), **NOTICES[system], 'targets': targets}
    return result


if __name__ == '__main__':
    Path(sys.argv[2]).write_text(json.dumps(build(sys.argv[1]), ensure_ascii=False, indent=2) + '\n')
