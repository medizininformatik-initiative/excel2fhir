"""Verify real FDE -> ontology -> Elasticsearch in two memory-bounded stages.

Run inside the derived updater image with this script mounted read-only.
collect WORKDIR ONTOLOGY_DIR EXPECTED_COUNT uses the local Compose Blaze.
publish WORKDIR uses the local Compose Elasticsearch after Blaze is stopped.
Requires an isolated test Blaze and an ontology index with zero availability.
"""
import hashlib
import json
import logging
from pathlib import Path
import sys
import uuid

sys.path.insert(0, '/opt/availability-updater/src/py')
import requests
from generate_availability import download_availability_reports, download_and_unzip, update_availability_in_es
from elastic_availability_generator import ElasticAvailabilityGenerator

logging.basicConfig(level=logging.INFO)
phase, directory = sys.argv[1:3]
root = Path(directory)
root.mkdir(parents=True, exist_ok=True)
summary_path = root / 'verification.json'
with requests.Session() as session:
    if phase == 'collect':
        ontology, expected = sys.argv[3], int(sys.argv[4])
        response = session.get('http://blaze:8080/fhir/Patient?_summary=count', timeout=30)
        response.raise_for_status()
        assert response.json()['total'] == expected
        input_dir = root / 'input'
        download_and_unzip(session,
            'https://github.com/medizininformatik-initiative/fhir-ontology-generator/releases/download/v5.0.0/availability.zip',
            input_dir)
        assert download_availability_reports(session, input_dir, 'http://blaze:8080/fhir',
                                             'fdpg-data-availability-report') == 1
        report = json.loads(next(input_dir.glob('*availability_report*')).read_text())
        stratifiers = [s for g in report['group'] for s in g['stratifier']
                       if s['code'][0]['coding'][0]['code'] == 'patient-gender']
        assert len(stratifiers) == 1
        assert sum(s['measureScore']['value'] for s in stratifiers[0]['stratum']) == expected
        context = json.loads((input_dir / 'stratum-to-context.json').read_text())['patient-gender']
        raw = f"{context['system']}{context['code']}{context.get('version', '')}http://snomed.info/sct263495000"
        node = str(uuid.uuid3(uuid.UUID(int=0), raw))
        bucket = max(b for b in [0, 10, 100, 1000, 10000, 100000, 1000000] if b <= expected)
        assert bucket > 0, 'Use at least ten Patients to distinguish successful availability from zero'
        hashes = []
        for run in ['updates', 'repeat']:
            generator = ElasticAvailabilityGenerator(str(input_dir), str(root / run), ontology)
            generator.generate()
            assert generator.availability[node] == expected
            count = len(generator.availability)
            del generator
            hashes.append({p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                           for p in sorted((root / run).glob('*.json'))})
        assert hashes[0] == hashes[1], 'Update files differ between repeated generation'
        summary = {'patients': expected, 'node': node, 'bucket': bucket,
                   'ontology_nodes': count, 'update_sha256': hashes[0]}
        summary_path.write_text(json.dumps(summary, indent=2))
        print(json.dumps(summary))
    elif phase == 'publish':
        summary = json.loads(summary_path.read_text())
        base = 'http://dataportal-elastic:9200/ontology'
        def request(method, path, **kwargs):
            response = session.request(method, base + path, timeout=120, **kwargs)
            response.raise_for_status()
            return response.json()
        positive = {'query': {'range': {'availability': {'gt': 0}}}}
        assert request('POST', '/_count', json=positive)['count'] == 0, 'Use an index with zero availability'
        original = request('GET', '/_doc/' + summary['node'])['_source']['availability']
        def check_bulk(response, *args, **kwargs):
            if response.request.url.endswith('/_bulk'):
                response.raise_for_status()
                result = response.json()
                assert not result.get('errors'), result
        session.hooks['response'].append(check_bulk)
        try:
            update_availability_in_es(session, 'http://dataportal-elastic:9200', 'ontology', root / 'updates')
            request('POST', '/_refresh')
            actual = request('GET', '/_doc/' + summary['node'])['_source']['availability']
            assert actual == summary['bucket'], (summary['bucket'], actual)
            summary['elasticsearch_availability'] = actual
            summary['positive_nodes'] = request('POST', '/_count', json=positive)['count']
            assert summary['positive_nodes'] > 0
            summary_path.write_text(json.dumps(summary, indent=2))
            print(json.dumps(summary))
        finally:
            # The precondition establishes zero availability for every existing node.
            request('POST', '/_update_by_query?refresh=true', json={**positive,
                'script': {'source': 'ctx._source.availability = 0', 'lang': 'painless'}})
            assert request('POST', '/_count', json=positive)['count'] == 0
            assert request('GET', '/_doc/' + summary['node'])['_source']['availability'] == original
    else:
        raise ValueError('Expected collect or publish')
