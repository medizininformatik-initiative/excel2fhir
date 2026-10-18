"""Short result names based on the patients actually present in an archive."""
from functools import lru_cache
import bz2
import gzip
import io
import json
from pathlib import PurePosixPath
import re
import xml.etree.ElementTree as ET
import zipfile


def filename(name, patients, fallback):
    label = re.sub(r'[^\w.-]+', '-', name or '', flags=re.UNICODE).strip('.-')[:120]
    return f'{label}-{patients}Pat.zip' if label else f'excel2fhir-{patients}Pat-{fallback}.zip'


def patient_count(path, *, result=False):
    stat = path.stat()
    return _patient_count(str(path), stat.st_mtime_ns, stat.st_size, result)


@lru_cache(maxsize=128)
def _patient_count(path, modified, size, result):
    patients = set()

    def resource(value):
        if not isinstance(value, dict):
            return
        if value.get('resourceType') == 'Patient' and value.get('id'):
            patients.add(value['id'])
        if value.get('resourceType') == 'Bundle':
            for entry in value.get('entry', []):
                resource(entry.get('resource'))

    def read(name, data):
        if name.endswith('.gz'):
            return read(name[:-3], gzip.decompress(data))
        if name.endswith('.bz2'):
            return read(name[:-4], bz2.decompress(data))
        if name.endswith('.zip'):
            with zipfile.ZipFile(io.BytesIO(data)) as nested:
                for member in nested.namelist():
                    read(member, nested.read(member))
        elif name.endswith('.json'):
            resource(json.loads(data))
        elif name.endswith('.ndjson'):
            for line in data.splitlines():
                if line.strip():
                    resource(json.loads(line))
        elif name.endswith('.xml'):
            root = ET.fromstring(data)
            ns = '{http://hl7.org/fhir}'
            def xml_resource(element):
                if element.tag == ns + 'Patient':
                    identifier = element.find(ns + 'id')
                    if identifier is not None and identifier.get('value'):
                        patients.add(identifier.get('value'))
                elif element.tag == ns + 'Bundle':
                    for entry in element.findall(ns + 'entry/' + ns + 'resource'):
                        for child in entry:
                            xml_resource(child)
            xml_resource(root)

    with zipfile.ZipFile(path) as archive:
        for member in archive.namelist():
            if not result or 'fhir' in PurePosixPath(member).parts:
                read(member, archive.read(member))
    return len(patients)
