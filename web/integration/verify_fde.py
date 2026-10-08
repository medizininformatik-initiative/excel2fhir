"""Save the latest FDE MeasureReports and verify the non-obfuscated patient count.

Usage: python3 web/integration/verify_fde.py TEST_DIRECTORY EXPECTED_PATIENT_COUNT
Run immediately after FDE on the isolated local test server.
"""
import json
from pathlib import Path
import sys
import urllib.request

root = Path(sys.argv[1])
expected = int(sys.argv[2])
base = 'http://127.0.0.1:5185/fhir'
with urllib.request.urlopen(base + '/Patient?_summary=count', timeout=30) as response:
    assert json.load(response)['total'] == expected
with urllib.request.urlopen(base + '/MeasureReport?_sort=-_lastUpdated&_count=2', timeout=30) as response:
    reports = json.load(response)
output = root / 'fde-output'
output.mkdir(exist_ok=True)
(output / 'measure-reports.json').write_text(json.dumps(reports, indent=2))
entries = reports.get('entry', [])
assert len(entries) == 2, 'Expected standard and obfuscated reports'
counts = []
for entry in entries:
    report = entry['resource']
    assert report['resourceType'] == 'MeasureReport'
    counts.append(report['group'][0]['population'][0]['count'])
assert expected in counts, (expected, counts)
print(f'FDE reports saved; expected population {expected}, latest report counts {counts}')
