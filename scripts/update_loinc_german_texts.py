"""Refresh existing reading texts from an official LOINC distribution ZIP.

Usage: python3 scripts/update_loinc_german_texts.py /path/to/Loinc_2.83.zip
Untranslated codes keep their existing project reading texts and provenance.
"""
import csv
import hashlib
import io
import json
from pathlib import Path
import re
import sys
import zipfile

PATH = Path(__file__).parent / 'mappings/synthea-german-texts.json'
VARIANT = 'AccessoryFiles/LinguisticVariants/deDE15LinguisticVariant.csv'
SOURCE = 'loinc-de-DE'
NOTICE = ('This material contains content from LOINC (http://loinc.org). '
          'LOINC is copyright © Regenstrief Institute, Inc. and the Logical '
          'Observation Identifiers Names and Codes (LOINC) Committee and is '
          'available at no cost under the license at http://loinc.org/license. '
          'LOINC® is a registered United States trademark of Regenstrief Institute, Inc.')


def update(data, archive):
    archive = Path(archive)
    match = re.fullmatch(r'Loinc_(\d+\.\d+)\.zip', archive.name, re.I)
    if not match:
        raise ValueError('Expected an official Loinc_VERSION.zip distribution')
    with zipfile.ZipFile(archive) as package:
        raw = package.read(VARIANT)
        translations = {row['LOINC_NUM']: row for row in
                        csv.DictReader(io.StringIO(raw.decode('utf-8-sig')))}
        main = {row['LOINC_NUM']: row for row in csv.DictReader(io.StringIO(
            package.read('LoincTable/Loinc.csv').decode('utf-8-sig')))}
    used, missing = [], []
    for entry in data['entries']:
        if entry['system'] != 'http://loinc.org':
            continue
        code = entry['code']
        label = translations.get(code, {}).get('LONG_COMMON_NAME', '')
        if not label:
            # Do not silently retain an official translation removed in a new edition.
            if any(t['source'] == SOURCE for t in entry['translations']):
                raise ValueError('Previously used official translation missing: ' + code)
            missing.append(code)
            continue
        if code not in main:
            raise ValueError('Translation absent from main LOINC table: ' + code)
        for text in entry['translations']:
            text.update(de=label, source=SOURCE, review='official-translation')
        entry['loinc'] = {'version': match[1], 'status': main[code]['STATUS'],
                          'externalCopyrightNotice': main[code]['EXTERNAL_COPYRIGHT_NOTICE'],
                          'externalCopyrightLink': main[code]['EXTERNAL_COPYRIGHT_LINK']}
        used.append(code)
    data['loincNotice'] = {'copyright': NOTICE, 'conditionsUrl': 'https://loinc.org/license'}
    data['sources'][SOURCE] = {
        'version': match[1], 'language': 'de-DE', 'url': 'https://loinc.org/translations/de-de',
        'archive': archive.name, 'archiveSha256': hashlib.sha256(archive.read_bytes()).hexdigest(),
        'file': VARIANT, 'sha256': hashlib.sha256(raw).hexdigest(), 'field': 'LONG_COMMON_NAME',
        'translatedCodes': len(used), 'codesWithoutOfficialTranslation': sorted(missing),
        'use': 'Official German long common names, unchanged; other entries retain project reading texts.'}
    data['description'] = ('Deutsche Lesetexte für synthetische Testdaten: verfügbare offizielle '
                           'LOINC-Langbezeichnungen und projektinterne Texte mit Herkunft je Eintrag. Codes bleiben unverändert.')
    return len(used), len(missing)


if __name__ == '__main__':
    data = json.loads(PATH.read_text())
    used, missing = update(data, sys.argv[1])
    PATH.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')
    print(f'{used} official German names; {missing} existing reading texts retained')
