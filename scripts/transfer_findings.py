"""Account for import notes separately from configuration-aware output comparisons."""
import json
from functools import lru_cache
from pathlib import Path
from check_output_selection import selected


@lru_cache(maxsize=1)
def dar_fields():
    root = Path(__file__).resolve().parents[1] / 'web/catalog'
    fields = json.loads((root / 'dar/generated/catalog.json').read_text())['fields']
    names = json.loads((root / 'options/contract.json').read_text())['propertiesFormat']['darProperties']
    return fields, names


def notes(source, report, options):
    resources = {(e['resource']['resourceType'], e['resource'].get('id')): e['resource']
                 for e in source.get('entry', []) if 'resource' in e}
    findings, configured = [], []
    fields, names = dar_fields()
    for loss in report.get('losses', []):
        resource = resources.get((loss.get('resourceType'), loss.get('id')),
                                 {'resourceType': loss.get('resourceType', '')})
        intentional = not selected(resource, options)
        # Only whole overwritten fields cover an import note; missing-only rules do not.
        for field in fields:
            if field['resourceType'] != resource['resourceType']:
                continue
            key = names[field['id']]
            if resource['resourceType'] == 'Encounter':
                scope = {'AMB': 'ambulatory', 'IMP': 'inpatient'}.get(resource.get('class', {}).get('code'))
                scoped = names.get(field['id'].replace('Encounter.', 'Encounter.' + str(scope) + '.'))
                if options.get(scoped, 'unchanged') != 'unchanged':
                    key = scoped
            path = field['field']
            if (options.get(key, 'unchanged') != 'unchanged'
                    and str(options.get(key + '_ONLY_WHEN_MISSING', False)).lower() != 'true'
                    and (loss.get('path') == path or loss.get('path', '').startswith(path + '.'))):
                intentional = True
        (configured if intentional else findings).append(loss)
    return {'importNotes': findings, 'configuredOmissions': configured}
