"""Human-readable clinical rows; explicit unsupported-property accounting.
Original codes are the working default. Optional national terminology/product
selection has a stable result contract and does not block the clinical import.
"""
import copy
import hashlib
import re
from pathlib import Path
from medication_products import ProductCatalog, select_german_product, INGREDIENT_SYSTEMS
from german_texts import GermanTexts
from national_medication_mapping import RXNORM, source_dose_form
from procedure_mapping import metadata as procedure_metadata
from procedure_projection import project as project_procedures

SNOMED = 'http://snomed.info/sct'
SYSTEMS = {SNOMED: 'SNOMED CT (Version nicht angegeben)', 'http://loinc.org': 'LOINC',
           'http://www.nlm.nih.gov/research/umls/rxnorm': 'RxNorm', 'http://hl7.org/fhir/sid/cvx': 'CVX'}
OBS_HEADERS = {
    'Laborbefund': ['Patient-ID', 'Fall-Nr', 'LOINC', 'Codesystem', 'Zusatzcode', 'Zusatzcodesystem', 'Parameter'],
    'Klinische Dokumentation': ['Patient-ID', 'Fall-Nr', 'Bezeichner', 'Untersuchungscode', 'Codesystem', 'Zusatzcode', 'Zusatzcodesystem'],
}
for sheet, headers in OBS_HEADERS.items():
    headers.extend(['Messwert' if sheet == 'Laborbefund' else 'Wert', 'Einheit',
                    'Zeitstempel (Abnahme)' if sheet == 'Laborbefund' else 'Zeitstempel',
                    'Werttyp', 'Wertcode', 'Wertcodesystem', 'Kategorie', 'Status', 'Untersuchung ID',
                    'Komponente von', 'Ausgabezeitpunkt', 'Einheitencode'])
PROCEDURE_EXTRA = ['Codesystem', 'Zusatzcode', 'Zusatzcodesystem', 'Ende', 'Status', 'Kategorie']
MEDICATION_HEADERS = ['Patient-ID', 'Fall-Nr', 'Medikationstyp', 'Präparatbezeichnung', 'Präparatcode', 'Präparatcodesystem', 'ATC-Code', 'ATC-Version', 'Darreichungsform', 'Wirkstoffcode', 'Wirkstoffcodesystem', 'Status', 'Absicht', 'Dokumentationszeitpunkt', 'Beginn', 'Ende', 'Einzeldosis', 'Dosiereinheit', 'Dosen pro Tag', 'Dosierungstext']

SUPPORTED = {'Observation', 'Procedure', 'MedicationRequest', 'MedicationAdministration', 'Medication'}

def mapping_metadata():
    registry = Path(__file__).parent / 'mappings/synthea-source-code-registry.json'
    return {'id': 'synthea-clinical-v1', 'sourceRegistrySha256': hashlib.sha256(registry.read_bytes()).hexdigest(), 'ops': procedure_metadata()}


class UnsupportedValue(ValueError):
    pass


def coding(cc):
    codes = cc.get('coding', [])
    if not codes or codes[0].get('system') not in SYSTEMS or codes[0].get('version'):
        raise UnsupportedValue('Unsupported coding structure or explicit version')
    c = codes[0]
    if not c.get('code'):
        raise UnsupportedValue('Code is missing')
    return str(c['code']), SYSTEMS[c['system']], cc.get('text') or c.get('display', '')


def observation_value(r):
    values = [k for k in r if k.startswith('value')]
    if len(values) > 1:
        raise UnsupportedValue('Mehrere value[x]-Werte')
    if r.get('dataAbsentReason'):
        codes = r['dataAbsentReason'].get('coding', [])
        if not codes or codes[0].get('system') != 'http://terminology.hl7.org/CodeSystem/data-absent-reason':
            raise UnsupportedValue('Unknown Data Absent Reason')
        return ['!dar:' + codes[0]['code'], '', 'Fehlend', '', '', '']
    if not values:
        return ['', '', 'Komponenten', '', '', '']
    key = values[0]; v = r[key]
    if key == 'valueQuantity':
        if v.get('system') not in (None, 'http://unitsofmeasure.org') or 'value' not in v or v.get('comparator'):
            raise UnsupportedValue('Quantity requires a number, no comparator and a supported unit system')
        return [str(v['value']), v.get('unit', ''), 'Zahl', '', '', v.get('code', '')]
    if key == 'valueString': return [v, '', 'Text', '', '', '']
    if key == 'valueBoolean': return [str(v).lower(), '', 'Ja/Nein', '', '', '']
    if key == 'valueCodeableConcept':
        code, system, text = coding(v)
        return [text, '', 'Code', code, system, '']
    raise UnsupportedValue('Unsupported value type: ' + key)


def prepare_clinical(entries, pid, encounter_numbers):
    products = ProductCatalog()
    product_texts = GermanTexts()
    index = {}
    for e in entries:
        r = e.get('resource', {})
        if r.get('id'):
            index[r['resourceType'] + '/' + r['id']] = r
            if e.get('fullUrl'): index[e['fullUrl']] = r
    procedures = project_procedures(entries)
    rows = {'Prozedur': [], 'Laborbefund': [], 'Klinische Dokumentation': [], 'Medikation': []}
    losses, mappings, imported = [], [], []
    def ref(r, field, expected):
        value = r.get(field, {}).get('reference')
        if value is None: return None
        target = index.get(value)
        if target is None or target['resourceType'] != expected:
            raise ValueError('Unresolved clinical reference: ' + str(value))
        if expected == 'Patient' and target['id'] != pid:
            raise ValueError('Cross-patient clinical reference')
        return target
    for e in entries:
        r = e.get('resource', {}); typ = r.get('resourceType')
        if typ not in SUPPORTED: continue
        def loss(path, reason):
            losses.append({'resourceType': typ, 'id': r.get('id'), 'path': path, 'reason': reason})
        if typ == 'Medication':
            # Medication is consumed through MedicationRequest references; the
            # target converter creates a shared definition per source code.
            for key in r.keys() - {'resourceType', 'id', 'code'}:
                loss(key, 'Nicht separat in der Präparatdefinition übernommen')
            continue
        ref(r, 'subject', 'Patient')
        encounter = ref(r, 'context' if typ == 'MedicationAdministration' else 'encounter', 'Encounter')
        nr = encounter_numbers[encounter['id']] if encounter else ''
        handled = {'resourceType', 'id', 'subject', 'encounter', 'context', 'status'}
        try:
            if typ == 'Procedure':
                coding(r['code'])  # Validate the supported source coding structure.
                category = ''
                if r.get('category'):
                    category, category_system, _ = coding(r['category'])
                    if category_system != SYSTEMS[SNOMED]: raise UnsupportedValue('Procedure category must use SNOMED')
                decision = procedures[r['id']]
                if decision['status'] in ('excluded', 'context-conflict'):
                    mappings.append({'sourceId': r['id'], **decision})
                    loss('$', decision['reason'])
                    continue
                if decision.get('internationalReplacement'):
                    loss('code.coding', 'Quellkonzept durch dokumentierten internationalen Prozedurbegriff ersetzt; vollständiges Detail im Text und Mappingbericht.')
                for number, projected in enumerate(decision['outputs']):
                    main, *extras = projected['codings']
                    main_system = 'OPS ' + main['version'] if main['system'].endswith('/ops') else SYSTEMS[main['system']]
                    extra_code = extras[0]['code'] if extras else ''
                    extra_system = SYSTEMS[extras[0]['system']] if extras else ''
                    rows['Prozedur'].append([pid, nr, projected['label'], main['code'], projected['start'],
                        main_system, extra_code, extra_system, projected['end'], projected['status'], category])
                    if decision.get('internationalReplacement') and main['system'] == SNOMED:
                        rows['Prozedur'][-1][3] = decision['source']['code']
                    projected['row'] = len(rows['Prozedur']) + 1
                    imported.append({'sourceId': r['id'], 'resourceType': typ, 'outputIndex': number})
                mappings.append({'sourceId': r['id'], **decision})
                handled.update(['code', 'performedPeriod', 'performedDateTime', 'category'])
            elif typ == 'Observation':
                code, system, label = coding(r['code'])
                categories = [c['code'] for cc in r.get('category', []) for c in cc.get('coding', [])
                              if c.get('system') == 'http://terminology.hl7.org/CodeSystem/observation-category']
                if len(categories) != 1: raise UnsupportedValue('Exactly one observation category is required')
                category = categories[0]
                sheet = 'Laborbefund' if category == 'laboratory' else 'Klinische Dokumentation'
                data = []
                # Stage the entire parent and its components before adding any rows.
                for item, parent in [(r, '')] + [(v, r['id']) for v in r.get('component', [])]:
                    c, sy, text = coding(item['code'])
                    value, unit, kind, vc, vs, ucum = observation_value(item)
                    if sheet == 'Laborbefund' and kind == 'Ja/Nein':
                        raise UnsupportedValue('The KDS laboratory profile requires a coded answer for boolean results; a coded answer is required')
                    extra_code, extra_system = '', ''
                    codings = item['code'].get('coding', [])
                    if len(codings) > 1:
                        extra_code, extra_system, _ = coding({'coding': [codings[1]]})
                    if len(codings) > 2:
                        loss(('component.' if parent else '') + 'code.coding[2:]', 'Weitere Untersuchungscodings nicht in zwei Codepaaren darstellbar')
                    if len(item.get('valueCodeableConcept', {}).get('coding', [])) > 1:
                        loss(('component.' if parent else '') + 'valueCodeableConcept.coding[1:]', 'Weitere Ergebniscodings nicht dargestellt')
                    base = ([pid, nr, c, sy, extra_code, extra_system, text] if sheet == 'Laborbefund'
                            else [pid, nr, text, c, sy, extra_code, extra_system])
                    data.append(base + [value, unit, r.get('effectiveDateTime', '')] +
                                [kind, vc, vs, category, r.get('status', ''), r['id'] if not parent else '',
                                 parent, r.get('issued', '') if not parent else '', ucum])
                    if parent:
                        for key in item.keys() - {'code', 'valueQuantity', 'valueCodeableConcept', 'valueString', 'valueBoolean', 'dataAbsentReason'}:
                            loss('component.' + key, 'Komponenten-Eigenschaft nicht übernommen')
                rows[sheet].extend(data)
                handled.update(['category', 'code', 'effectiveDateTime', 'issued', 'component', 'dataAbsentReason',
                                'valueQuantity', 'valueCodeableConcept', 'valueString', 'valueBoolean'])
            else:
                cc = r.get('medicationCodeableConcept')
                if cc is None:
                    medication = ref(r, 'medicationReference', 'Medication')
                    if medication is None: raise UnsupportedValue('Medication is missing')
                    cc = medication['code']
                code, system, label = coding(cc)
                dosage = r.get('dosage', {}) if typ == 'MedicationAdministration' else (r.get('dosageInstruction') or [{}])[0]
                if len(r.get('dosageInstruction', [])) > 1: loss('dosageInstruction[1:]', 'Weitere Dosierungsanweisungen nicht übernommen')
                dose = dosage.get('dose', {}) if typ == 'MedicationAdministration' else next(
                    (d['doseQuantity'] for d in dosage.get('doseAndRate', []) if 'doseQuantity' in d), {})
                repeat = dosage.get('timing', {}).get('repeat', {})
                daily = str(repeat['frequency']) if repeat.get('period') == 1 and repeat.get('periodUnit') == 'd' and 'frequency' in repeat else ''
                period = r.get('effectivePeriod', {})
                timestamp = r.get('authoredOn', r.get('effectiveDateTime', period.get('start', '')))
                row = [pid, nr, {'MedicationRequest': 'Verordnung', 'MedicationAdministration': 'Verabreichung',
                                     'MedicationStatement': 'Medikationsaussage'}[typ], label, code, system, '', '', '',
                       '!dar:unknown', SYSTEMS[SNOMED], r.get('status', ''), r.get('intent', ''),
                       timestamp if typ == 'MedicationRequest' else r.get('dateAsserted', ''),
                       '' if typ == 'MedicationRequest' else (timestamp or '!dar:unknown'),
                       period.get('end', ''), str(dose.get('value', '')), dose.get('code', dose.get('unit', '')), daily,
                       dosage.get('text', '')]
                decision = products.select(cc['coding'][0])
                if cc['coding'][0]['system'] == RXNORM:
                    # Keep the detailed source description, not the US coding or
                    # US brand, when no proven German pack is available.
                    row[3] = re.sub(r'\s*\[[^\]]+\]', '', product_texts.text(label, 'Medikation', system, code))
                    row[4], row[5] = '', ''
                    row[8] = source_dose_form(cc['coding'][0].get('display', ''))
                    if decision['target'] is None:
                        row[3] += ' (PZN-Zuordnung offen)'
                    if decision.get('atc') is None:
                        row[3] += ' (ATC-Zuordnung offen)'
                if decision.get('atc'):
                    row[6], row[7] = decision['atc']['code'], decision['atc']['version']
                if decision.get('ingredients'):
                    ingredients = decision['ingredients']
                    systems = {i['system'] for i in ingredients}
                    if len(systems) != 1:
                        raise ValueError('Ingredient list requires a shared code system')
                    row[9] = '; '.join(i['code'] for i in ingredients)
                    row[10] = INGREDIENT_SYSTEMS[ingredients[0]['system']]
                if decision.get('enrichment'):
                    enrichment = decision['enrichment']
                    row[3] = enrichment['display']
                    row[8] = enrichment['doseForm']
                    if enrichment.get('resetDose'):
                        replacement = enrichment['dose']
                        decision['sourceDosage'] = copy.deepcopy(dosage)
                        row[16:20] = [replacement['value'], replacement['unit'],
                                      replacement['frequency'], replacement['text']]
                if decision['target'] is not None:
                    product = decision['target']
                    row[3], row[8] = product['display'], product['doseForm']
                    row[4], row[5] = product['code'], 'PZN'
                    if row[16] and not row[17] and product.get('sourceCountUnit'):
                        row[17] = product['sourceCountUnit']
                        decision['doseUnitEnrichment'] = row[17]
                    if product.get('ingredient'):
                        ingredient = product['ingredient']
                        if ingredient['system'] != RXNORM:
                            row[9], row[10] = ingredient['code'], INGREDIENT_SYSTEMS[ingredient['system']]
                        else:
                            decision['ingredientOmitted'] = 'RxNorm-Wirkstoff bleibt nur in der Herkunft; nationale Zuordnung offen.'
                rows['Medikation'].append(row)
                mappings.append({'sourceId': r['id'], **decision})
                handled.update(['medicationCodeableConcept', 'medicationReference', 'authoredOn', 'effectiveDateTime', 'effectivePeriod', 'intent'])
                # Explicitly report partial dosage transfer, including route,
                # additional dose/rate entries, bounds and non-daily timing.
                if dosage: loss('dosage' if typ == 'MedicationAdministration' else 'dosageInstruction',
                                'Teilweise übernommen: Text, erste Dosis und einfache Tagesfrequenz; weitere Dosierungsdetails fehlen')
            if typ != 'Procedure':
                imported.append({'sourceId': r['id'], 'resourceType': typ})
            def extra_codings(value, path=''):
                if isinstance(value, dict):
                    if len(value.get('coding', [])) > 1:
                        loss(path + '.coding[1:]', 'Erstes Coding übernommen; weitere Codings im Quellbestand erhalten')
                    for key, item in value.items(): extra_codings(item, path + '.' + key)
                elif isinstance(value, list):
                    for i, item in enumerate(value): extra_codings(item, path + '[' + str(i) + ']')
            extra_codings(r)
            for key in r.keys() - handled:
                loss(key, 'Eigenschaft nicht übernommen')
        except (UnsupportedValue, KeyError) as ex:
            loss('$', 'Ressource ausgelassen: ' + str(ex))
    has_local_products = any(m.get('provider') == 'mmi-local' for m in mappings)
    medication_mappings = [m for m in mappings if m['source'].get('system') == RXNORM]
    from contextual_procedures import summary as procedure_summary
    return rows, {'procedureMappingSummary': procedure_summary(procedures),
                  'productCatalog': products.metadata, 'productTexts': product_texts.report(),
                  'medicationMappingSummary': {
                      'events': len(medication_mappings),
                      'withAtc': sum(bool(m.get('atc')) for m in medication_mappings),
                      'withPzn': sum(bool(m.get('target')) for m in medication_mappings),
                      'atcUnmappedCodes': sorted({m['source']['code'] for m in medication_mappings if not m.get('atc')}),
                      'pznUnmappedCodes': sorted({m['source']['code'] for m in medication_mappings if not m.get('target')}),
                      'withIngredients': sum(bool(m.get('ingredients')) for m in medication_mappings),
                      'ingredientMapping': 'Öffentliche UNII-Zuordnung; Kombinationswirkstoffe einzeln ausgewiesen'},
                  'productDataUsage': {'containsLocalProductData': has_local_products,
                                       'redistribution': 'not-cleared' if has_local_products else 'no-local-product-data'},
                  'clinicalMapping':mapping_metadata(), 'clinicalImports': imported, 'clinicalMappings': mappings, 'losses': losses}
