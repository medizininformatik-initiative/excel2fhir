"""Generate the structural schema from the option and DAR catalogues."""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent


def generate():
    contract = json.loads((ROOT / 'contract.json').read_text())
    fields = json.loads((ROOT / contract['dar']['catalogue']).read_text())['fields']
    options = {}
    for option in contract['options']:
        definition = {
            'title': option['label'], 'description': option['help'],
            'default': option['default'],
        }
        kind = option['type']
        if kind == 'enum':
            definition['enum'] = option['choices']
        elif kind == 'set':
            definition.update(type='array', uniqueItems=True,
                              items={'enum': option['choices']})
        else:
            definition['type'] = kind
        if 'minimum' in option:
            definition['minimum'] = option['minimum']
        options[option['id']] = definition
    dar = {}
    for field in fields:
        dar[field['id']] = {
            'oneOf': [
                {'type': 'object', 'required': ['mode'],
                 'properties': {'mode': {'const': 'unchanged'}},
                 'additionalProperties': False},
                {'type': 'object', 'required': ['mode', 'code'],
                 'properties': {'mode': {'const': 'overwrite'},
                                'code': {'enum': field['allowedCodes']}},
                 'additionalProperties': False},
            ]
        }
    eligible = sorted({r['resourceType'] for r in contract['resources']
                       if r['identifierEligible']})
    return {
        '$schema': 'https://json-schema.org/draft/2020-12/schema',
        'title': 'Converter configuration (draft contract)',
        'type': 'object', 'required': ['schemaVersion'],
        'additionalProperties': False,
        'properties': {
            'schemaVersion': {'const': contract['schemaVersion']},
            'values': {'type': 'object', 'properties': options,
                       'additionalProperties': False, 'default': {}},
            'dar': {'type': 'object', 'properties': dar,
                    'additionalProperties': False, 'default': {}},
            'identifierRules': {
                'type': 'array', 'default': [],
                'items': {
                    'type': 'object', 'additionalProperties': False,
                    'required': ['id', 'enabled', 'resources', 'system', 'pattern'],
                    'properties': {
                        'id': {'type': 'string', 'format': 'uuid'},
                        'enabled': {'type': 'boolean'},
                        'resources': {'type': 'array', 'uniqueItems': True,
                                      'minItems': 1, 'items': {'enum': eligible}},
                        'system': {'type': 'string', 'minLength': 1},
                        'pattern': {'type': 'string', 'minLength': 1},
                    },
                },
            },
        },
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    expected = json.dumps(generate(), indent=2, ensure_ascii=False) + '\n'
    target = ROOT / 'configuration.schema.json'
    if args.check:
        if not target.exists() or target.read_text() != expected:
            raise SystemExit('Configuration schema is stale; regenerate it.')
    else:
        target.write_text(expected)


if __name__ == '__main__':
    main()
