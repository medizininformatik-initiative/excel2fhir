#!/usr/bin/env python3
"""Select a small, deliberately inpatient-rich 2020–2026 demonstration cohort.
Usage: python3 examples/synthea-hospital/select_cases.py INPUT_FHIR OUTPUT_DIRECTORY
Sources are copied unchanged. This is an example recipe, not prevalence sampling.
"""
from collections import Counter
import hashlib
import json
from pathlib import Path
import shutil
import sys


def select(source_dir, output_dir):
    candidates = []
    for path in sorted(Path(source_dir).glob('*.json')):
        data = json.loads(path.read_text())
        resources = [e['resource'] for e in data.get('entry', [])]
        if sum(r['resourceType'] == 'Patient' for r in resources) != 1:
            continue
        encounters = [r for r in resources if r['resourceType'] == 'Encounter']
        period = [r for r in encounters if '2020-01-01' <= r.get('period', {}).get('start', '') < '2027-01-01']
        counts = Counter(r['class']['code'] for r in period)
        if not period:
            continue
        candidates.append({'file': path.name, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                           'resources': len(resources), 'encounters2020to2026': dict(counts),
                           'contextEncountersOutsidePeriod': len(encounters) - len(period)})
    # Six repeatedly admitted patients, two with one admission, two without an
    # admission in this period. Keep ALL their exported outpatient encounters.
    groups = [sorted((c for c in candidates if c['encounters2020to2026'].get('IMP', 0) >= 2),
                     key=lambda c: (-c['encounters2020to2026']['IMP'], c['resources'], c['file'])),
              sorted((c for c in candidates if c['encounters2020to2026'].get('IMP', 0) == 1),
                     key=lambda c: (c['resources'], c['file'])),
              sorted((c for c in candidates if c['encounters2020to2026'].get('IMP', 0) == 0),
                     key=lambda c: (c['resources'], c['file']))]
    sizes = (6, 2, 2)
    if any(len(g) < n for g, n in zip(groups, sizes)):
        raise ValueError('Insufficient matching candidates; generate a larger pool or another seed. No partial cohort written.')
    chosen = [c for group, n in zip(groups, sizes) for c in group[:n]]
    output = Path(output_dir)
    output.mkdir(parents=True, exist_ok=False)
    inputs = output / 'fhir'
    inputs.mkdir()
    for item in chosen:
        shutil.copyfile(Path(source_dir) / item['file'], inputs / item['file'])
    report = {'purpose': 'Deliberately enriched demonstration, not representative hospital prevalence',
              'selectionPeriod': ['2020-01-01', '2027-01-01 (exclusive)'],
              'selectionBasis': 'Source IMP encounters starting in the period, not generated department/location contacts',
              'candidates': len(candidates), 'selected': chosen,
              'unselected': [c['file'] for c in candidates if c not in chosen]}
    (output / 'selection.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    return report


if __name__ == '__main__':
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    report = select(*sys.argv[1:])
    total = Counter()
    for item in report['selected']:
        total.update(item['encounters2020to2026'])
    print(len(report['selected']), 'patients;', dict(total), 'source encounters in 2020–2026')
