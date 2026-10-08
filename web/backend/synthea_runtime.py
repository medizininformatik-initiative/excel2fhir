"""Fingerprint the shared Synthea import pipeline used for a job."""
import hashlib
import os
from pathlib import Path

ROOT = Path(os.environ.get('SYNTHEA_HOME', '/synthea'))


def fingerprint():
    paths = [ROOT / 'FHIR_Testdatengenerator_Vorlage.xlsx',
             ROOT / 'src/main/resources/workbook-absent-reasons.json',
             *sorted((ROOT / 'scripts').glob('*.py')), *sorted((ROOT / 'scripts').glob('*.java')),
             *sorted((ROOT / 'scripts/mappings').glob('*')),
             *sorted((ROOT / 'src/main/resources/ucum').glob('*.map'))]
    result = hashlib.sha256()
    for path in paths:
        result.update(str(path.relative_to(ROOT)).encode())
        result.update(b'\0')
        result.update(path.read_bytes())
    return result.hexdigest()
