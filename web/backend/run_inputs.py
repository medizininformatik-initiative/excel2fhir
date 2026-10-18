"""Compact, temporary archives of the inputs for Excel-to-FHIR conversion."""
import hashlib
import json
from pathlib import Path
import tempfile
import zipfile

import store


def archive(job_id):
    with store.run_lock(job_id):
        job = store.get(job_id)
        if not job:
            raise FileNotFoundError('Run not found')
        if job['state'] not in {'succeeded', 'failed'}:
            raise store.SubmissionConflict('Run has not ended')
        root = store.ROOT / 'jobs' / job_id
        snapshot = json.loads((root / 'snapshot.json').read_text())
        kind = snapshot.get('inputKind', 'workbook')
        files = [root / 'snapshot.json', root / 'default.config']
        if kind == 'workbook':
            files.append(root / 'input.xlsx')
        elif kind == 'workbook-set':
            files.append(root / 'workbooks.json')
            files.extend(sorted((root / 'workbooks').glob('*.xlsx')))
        elif kind.startswith('synthea') and snapshot['profile']['id'] != 'synthea':
            files.extend(sorted((root / 'output').glob('run-*/excel/*.xlsx')))
        if not any(path.suffix == '.xlsx' and path.is_file() for path in files):
            raise store.SubmissionConflict('No Excel workbooks are available for this run')
        for path in sorted((root / 'output').rglob('*')):
            relative = path.relative_to(root / 'output')
            if path.suffix == '.config' and 'details' in relative.parts and any(
                    part in {'options', 'options-input'} for part in relative.parts):
                files.append(path)
            elif path.name == 'environment.json' and path.parent.name == 'reports':
                files.append(path)
        # Never follow a workbook reference or a symlink outside the saved run.
        for path in files:
            if not path.is_file() or path.is_symlink() or not path.resolve().is_relative_to(root.resolve()):
                raise store.SubmissionConflict('A saved conversion input is unavailable')
        temporary = tempfile.NamedTemporaryFile(prefix='excel2fhir-inputs-', suffix='.zip', delete=False)
        destination = Path(temporary.name)
        temporary.close()
        try:
            inventory = []
            with zipfile.ZipFile(destination, 'w', zipfile.ZIP_DEFLATED) as output:
                for path in files:
                    name = path.relative_to(root).as_posix()
                    digest = hashlib.sha256()
                    with path.open('rb') as source, output.open(name, 'w', force_zip64=True) as member:
                        while chunk := source.read(1024 * 1024):
                            digest.update(chunk)
                            member.write(chunk)
                    inventory.append({'path': name, 'sha256': digest.hexdigest()})
                output.writestr('manifest.json', json.dumps({
                    'schemaVersion': 1, 'runId': job_id, 'state': job['state'],
                    'scope': 'Excel-to-FHIR conversion inputs',
                    'converterSha256': snapshot.get('converterSha256'),
                    'files': inventory,
                    'limitations': ['External document files referenced by a workbook are not included.',
                                    'Upstream generation and source comparison require the original source data.',
                                    'A failed run may contain only some of the expected workbooks.'],
                }, indent=2))
                output.writestr('README.txt',
                    'Excel-to-FHIR conversion inputs\n\n'
                    'Use the converter identified by converterSha256 in snapshot.json; its JAR also contains the converter catalogues.\n'
                    'Use default.config with --converter-options and convert each workbook with -f.\n'
                    'snapshot.json records output formats, validation and patientsPerFile.\n'
                    'Saved effective configuration files and environment reports are included where available.\n'
                    'For Synthea-derived workbooks use the Europe/Berlin clinical time zone.\n'
                    'External document files referenced by a workbook must be supplied separately at their expected paths.\n'
                    'This archive supports conversion from Excel; source comparison requires the original sources.\n'
                    'Failed runs may have produced only part of their workbooks.\n')
            return destination
        except BaseException:
            destination.unlink(missing_ok=True)
            raise
