"""Versioned synthetic identities for German review cases, not anonymization.

Municipality/postcode pairs are real; streets and households are invented.
No ethnicity, citizenship, consent or insurance is inferred or generated.
"""
import hashlib
import json
from pathlib import Path
import re

DATA_PATH = Path(__file__).with_name('mappings') / 'german-demographics.json'


def identity(patient):
    raw = DATA_PATH.read_bytes()
    data = json.loads(raw)
    def pick(label, values):
        digest = hashlib.sha256((data['version']+'|'+patient['id']+'|'+label).encode()).digest()
        return values[int.from_bytes(digest, 'big') % len(values)]
    names = data['givenNames'].get(patient.get('gender'), data['givenNames']['unknown'])
    name = {'given':[pick('given', names)], 'family':pick('family', data['familyNames'])}
    place = pick('place', data['places'])
    address = {k:place[k] for k in ('postalCode','city','state')}
    address.update(country='DE', line=[pick('street', data['streets'])+' '+str(pick('number', list(range(1, 100))))])
    return {'version':data['version'], 'dataSha256':hashlib.sha256(raw).hexdigest(),
            'sourcePatient':patient['id'], 'name':name, 'address':address,
            'placeSource':place['source'], 'status':'synthetic-replacement',
            'limitations':['Invented street/household; not a deliverable postal address',
                           'Small unweighted name/place pools; not population-representative',
                           'One current address; no residential history']}


def localize_document_identity(text, patient):
    """Replace full names and unambiguous Synthea digit-suffixed name tokens.

    Ordinary isolated words such as May/Brown are deliberately not replaced.
    This is not translation, redaction or a general de-identification routine.
    """
    replacement = identity(patient)['name']
    aliases = {}
    for name in patient.get('name', []):
        given = name.get('given', [])
        family = name.get('family', '')
        if given and family:
            aliases[' '.join([*given, family])] = ' '.join([*replacement['given'], replacement['family']])
        for value, target in [(v, replacement['given'][0]) for v in given] + [(family, replacement['family'])]:
            if re.search(r'\D\d+$', value): aliases[value] = target
    if not aliases: return text, 0
    pattern = r'(?<!\w)(?:'+'|'.join(re.escape(k) for k in sorted(aliases, key=len, reverse=True))+r')(?!\w)'
    return re.subn(pattern, lambda match:aliases[match.group()], text)


def check_patient(source, target, report):
    expected = identity(source)
    assert report.get('demographics') == expected, 'Demographic replacement report changed'
    assert len(target.get('name', [])) == 1 and len(target.get('address', [])) == 1
    for key, value in expected['name'].items():
        assert target['name'][0].get(key) == value, 'Localized patient name changed'
    for key, value in expected['address'].items():
        assert target['address'][0].get(key) == value, 'Localized patient address changed'
    for key in ('birthDate','gender','deceasedDateTime','deceasedBoolean'):
        assert target.get(key) == source.get(key), 'Patient clinical fact changed: '+key
    return {'identity':'verified synthetic replacement', 'clinicalFacts':'preserved'}
