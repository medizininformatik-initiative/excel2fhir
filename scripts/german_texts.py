"""Offline German reading texts for the pinned Synthea inventory.

Only designated human-readable columns are translated. Codes, quantities,
identifiers, dates and enum values are never passed through text replacement.
Unknown input is retained and explicitly listed in the generation report.
"""
from collections import Counter
from functools import lru_cache
import hashlib
import json
from pathlib import Path
import re

PATH = Path(__file__).parent / 'mappings/synthea-german-texts.json'
SYSTEMS = {'SNOMED CT (Version nicht angegeben)': 'http://snomed.info/sct',
           'LOINC': 'http://loinc.org', 'RxNorm': 'http://www.nlm.nih.gov/research/umls/rxnorm',
           'CVX': 'http://hl7.org/fhir/sid/cvx'}


@lru_cache(maxsize=1)
def catalog():
    data = json.loads(PATH.read_text())
    codes, words = {}, {}
    # Prefer an editorial correction when identical uncoded source texts occur
    # in multiple systems. Coded cells always use their own system/code entry.
    items = data['literals'] + [t for e in data['entries'] for t in e['translations']]
    for t in sorted(items, key=lambda t: t['source'] != 'opus-mt-draft'):
        words[t['original'].casefold()] = t['de']
    for e in data['entries']:
        codes[e['system'], e['code']] = {t['original'].casefold():t['de'] for t in e['translations']}
    phrases = {s.strip().casefold():v.strip() for s,v in data['notePhrases'].items()}
    combined = {**words, **phrases}
    pattern = re.compile(r'(?<!\w)(?:' + '|'.join(re.escape(s) for s in sorted(combined, key=len, reverse=True)) + r')(?!\w)', re.I)
    return data, codes, words, combined, pattern


class GermanTexts:
    def __init__(self, address=None):
        self.data, self.codes, self.words, self.combined, self.pattern = catalog()
        self.missing = Counter()
        self.translated = 0
        self.address = address

    def observation_text(self, value, system, code, context='Observation.Wert'):
        if SYSTEMS.get(system, system) == 'http://loinc.org' and code == '56799-0' and self.address:
            self.translated += 1
            return ', '.join(self.address['line'])
        return self.text(value, context)

    def missing_text(self, value, context):
        if value.strip(): self.missing[context, value] += 1
        return value

    def text(self, value, context='text', system=None, code=None):
        if not value and not code: return value
        choices = self.codes.get((SYSTEMS.get(system, system), str(code)), {}) if code else self.words
        result = choices.get(str(value).casefold())
        if result is None and not value and choices: result = next(iter(choices.values()))
        if result is None: return self.missing_text(value, context)
        self.translated += 1
        return result

    def fragments(self, value, context):
        """Replace whole known phrases once; audit every unmatched text span."""
        parts, start = [], 0
        for m in self.pattern.finditer(value):
            tail = value[start:m.start()]
            if re.search(r'[^\W\d_]', tail): self.missing_text(tail.strip(), context)
            parts.extend([tail, self.combined[m[0].casefold()]])
            start = m.end()
            self.translated += 1
        tail = value[start:]
        if re.search(r'[^\W\d_]', tail): self.missing_text(tail.strip(), context)
        return ''.join(parts) + tail

    def document(self, value, context):
        lines = []
        for line in value.splitlines(keepends=True):
            # Names and ages are parameters, never vocabulary for replacement.
            m = re.match(r'^(.*?) is a (newborn|\d+ (?:year-old|month-old)) (nonhispanic|hispanic) (white|black|asian|native|hawaiian|other) (male|female)\.(.*?)(\n?)$', line)
            if m:
                name, age, ethnicity, race, sex, rest, newline = m.groups()
                age = 'neugeboren' if age == 'newborn' else age.replace('year-old','Jahre alt').replace('month-old','Monate alt')
                race = {'white':'weiß','black':'schwarz','asian':'asiatisch','native':'indigen','hawaiian':'hawaiianisch','other':'andere'}[race]
                ethnicity = 'nicht hispanisch' if ethnicity == 'nonhispanic' else 'hispanisch'
                sex = 'männlich' if sex == 'male' else 'weiblich'
                lines.append(f'{name}: {age}, {sex}. Ethnische Zuordnung laut US-Quelle: {ethnicity}, {race}.' + self.fragments(rest, context) + newline)
                continue
            m = re.fullmatch(r'Patient currently has (.*?)\.(\n?)', line.strip('\r'))
            if m:
                payer, newline = m.groups()
                lines.append(('Keine Krankenversicherung laut Quelle.' if payer == 'NO INSURANCE' else 'Krankenversicherung laut Quelle: ' + payer + '.') + newline)
                continue
            # Split dynamic smoking age from the surrounding fixed sentences.
            segments = re.split(r'(Patient quit smoking at age \d+)', line)
            rendered = []
            for part in segments:
                m = re.fullmatch(r'Patient quit smoking at age (\d+)', part)
                rendered.append(f'Rauchstopp im Alter von {m[1]} Jahren' if m else self.fragments(part, context))
            lines.append(''.join(rendered))
        return ''.join(lines)

    def report(self):
        return {'id':self.data['id'], 'sha256':hashlib.sha256(PATH.read_bytes()).hexdigest(),
                'translatedTexts':self.translated,
                'missing':[{'context':c,'original':s,'occurrences':n} for (c,s),n in sorted(self.missing.items())],
                'quality':'Project reading texts; editorial and machine-draft provenance in the table; medical review pending',
                'scope':'Clinical labels, text values and Synthea note templates; technical enum values remain unchanged'}


def localize_rows(rows, address=None):
    tr = GermanTexts(address)
    def coded(sheet, label, code, system):
        for row in rows.get(sheet, []):
            if sheet == 'Prozedur' and row[system].startswith('OPS '):
                continue  # Original detail translated before national classification.
            if sheet == 'Medikation' and row[system] in ('PZN', ''):
                continue  # Product mapping translated before removing source coding.
            if sheet == 'Impfung' and (row[system].startswith('ATC ') or row[system] == ''):
                continue  # Detailed vaccine text was translated before removing CVX.
            row[label] = tr.text(row[label], sheet, row[system], row[code])
    for args in [('Diagnose',2,3,4), ('Prozedur',2,3,5), ('Laborbefund',3,2,16),
                 ('Klinische Dokumentation',2,3,16), ('Medikation',3,4,5),
                 ('DocumentReference',9,7,8)]: coded(*args)
    for sheet in ['Impfung','Befundbericht','Behandlungsplan','Hilfsmittel']:
        coded(sheet,3,4,5)
    for sheet in ['Laborbefund','Klinische Dokumentation']:
        for row in rows.get(sheet, []):
            if row[7] == 'Text':
                row[4] = tr.observation_text(row[4], row[16], row[2] if sheet=='Laborbefund' else row[3], sheet+'.Wert')
            elif row[7] == 'Code':
                row[4] = tr.text(row[4], sheet+'.Wert', row[9], row[8])
    for sheet, col in [('Medikation',19),('Befundbericht',10),('Behandlungsplan',10)]:
        for row in rows.get(sheet, []):
            if row[col]: row[col] = tr.text(row[col], sheet+'.Freitext')
    for row in rows.get('DocumentReference', []): row[4] = tr.document(row[4], 'DocumentReference.Text')
    return tr.report()
