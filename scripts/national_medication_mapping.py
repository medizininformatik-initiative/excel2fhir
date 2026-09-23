"""Offline German classifications and explicitly selected representative packs.

RxNorm remains provenance only. Classification is independent of product choice;
unmapped products retain their description and all medication events.
"""
import copy
import hashlib
import json
from pathlib import Path
import re

PATH = Path(__file__).parent / 'mappings/synthea-medications-de-2026.json'
RXNORM = 'http://www.nlm.nih.gov/research/umls/rxnorm'


def source_dose_form(display):
    """Translate only an explicit source form; Injection alone is ambiguous."""
    for source, target in [('Extended Release Oral Tablet', 'Retardtablette'),
                           ('Extended Release Oral Capsule', 'Retardkapsel'),
                           ('Chewable Tablet', 'Kautablette'),
                           ('Sublingual Tablet', 'Sublingualtablette'),
                           ('Oral Tablet', 'Tablette'), ('Oral Capsule', 'Hartkapsel'),
                           ('Injectable Suspension', 'Injektionssuspension'),
                           ('Injectable Solution', 'Injektionslösung'),
                           ('Ophthalmic Solution', 'Augentropfen, Lösung'),
                           ('Inhalation Solution', 'Inhalationslösung'),
                           ('Inhalation Suspension', 'Suspension zur Inhalation'),
                           ('Dry Powder Inhaler', 'Pulver zur Inhalation'),
                           ('Metered Dose Inhaler', 'Dosieraerosol'),
                           ('Topical Cream', 'Creme'), ('Oral Gel', 'Gel zur Anwendung in der Mundhöhle'),
                           ('Drug Implant', 'Implantat'), ('Intrauterine System', 'Intrauterinsystem'),
                           ('Mucosal Spray', 'Spray zur Anwendung in der Mundhöhle')]:
        if source.casefold() in display.casefold():
            return target
    return ''


class NationalMedicationMapping:
    def __init__(self):
        raw = PATH.read_bytes()
        data = json.loads(raw)
        self.entries = {}
        for entry in data['entries']:
            source = entry['source']
            key = (source['system'], source['code'])
            if key in self.entries:
                raise ValueError('Doppelte nationale Medikationszuordnung: ' + str(key))
            atc = entry['atc']
            if atc and (not re.fullmatch(r'[A-Z][0-9]{2}[A-Z]{2}[0-9]{2}', atc['code'])
                        or atc['version'] != '2026'):
                raise ValueError('Invalid ATC mapping: ' + str(key))
            product = entry['product']
            ingredients = entry.get('ingredients', [])
            if any(i.get('system') != 'http://fdasis.nlm.nih.gov'
                   or not re.fullmatch(r'[A-Z0-9]{10}', i.get('code', '')) for i in ingredients):
                raise ValueError('Invalid public ingredient mapping: ' + str(key))
            if product:
                pzn = product['code']
                if (not re.fullmatch(r'[0-9]{8}', pzn)
                        or sum(int(n) * i for i, n in enumerate(pzn[:7], 1)) % 11 != int(pzn[-1])
                        or product['doseCompatibility'] not in ('unchanged', 'synthetic-representative')
                        or product['source'] not in data['productSources']):
                    raise ValueError('Invalid product mapping: ' + str(key))
                if product['doseCompatibility'] == 'synthetic-representative':
                    enrichment = entry.get('enrichment') or {}
                    if not (product.get('selectionReason') and product.get('dosePolicy') in
                            ('preserve-source-regimen', 'explicit-synthetic-regimen')):
                        raise ValueError('Synthetic product selection requires a rationale and dosage decision: ' + str(key))
                    if product.get('sourceCountUnit') not in (None, '{tbl}', '{caps}'):
                        raise ValueError('Unsupported addition of a unit dose: ' + str(key))
                    if product['dosePolicy'] == 'explicit-synthetic-regimen' and not enrichment.get('resetDose'):
                        raise ValueError('Synthetic dosage regimen is missing: ' + str(key))
                    if enrichment.get('resetDose') and not all(
                            k in enrichment.get('dose', {}) for k in ('value', 'unit', 'frequency', 'text')):
                        raise ValueError('Incomplete synthetic dosage regimen: ' + str(key))
            self.entries[key] = entry
        self.metadata = {'id': data['id'], 'sha256': hashlib.sha256(raw).hexdigest(),
                         'atcSource': data['atcSource'], 'productSources': data['productSources']}

    def select(self, coding):
        entry = self.entries.get((coding.get('system'), coding.get('code')))
        result = {'status': 'unmapped', 'source': copy.deepcopy(coding), 'target': None,
                  'atc': None, 'provider': 'public-source',
                  'reason': 'Quellcode oder Quellbezeichnung nicht im geprüften Bestand; deutsche Zuordnung offen.'}
        if (entry is None or coding.get('version')
                or (coding.get('display') and coding['display'] not in entry['source']['displays'])):
            return result
        result.update(status=entry['status'], atc=copy.deepcopy(entry['atc']),
                      target=copy.deepcopy(entry['product']), reason=entry['reason'],
                      review=entry['review'], ingredients=copy.deepcopy(entry.get('ingredients', [])),
                      enrichment=copy.deepcopy(entry.get('enrichment')))
        if result['target']:
            result['status'] = 'public-product'
        return result
