"""Resolve a single external configuration using the converter defaults and parser."""

def resolve_config(path=None, patients=None):
    import json
    from pathlib import Path
    import subprocess
    root = Path(__file__).resolve().parents[1]
    request = {'text': Path(path).read_text(encoding='utf-8') if path is not None else '', 'defaults': {}}
    if path is not None:
        request['name'] = Path(path).stem
    if patients is not None:
        request['patients'] = patients
    result = subprocess.run(['java', '-cp', str(root / 'target/excel2fhir.jar'),
                             str(root / 'scripts/WorkflowOptions.java')],
                            input=json.dumps(request), capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError('Converter options could not be checked. '
                           'Rebuild the converter. ' + result.stderr.strip())
    resolved = json.loads(result.stdout)
    if resolved['errors']:
        raise ValueError('Invalid converter options:\n' + '\n'.join(resolved['errors']))
    return resolved


def selected_configs(files=(), patients=None):
    if len(files) > 1:
        raise ValueError("Choose one converter configuration per run")
    result = []
    names = set()
    for path in files or [None]:
        resolved = resolve_config(path, patients)
        name = resolved.get('name', 'default')
        if name.lower() in names:
            raise ValueError('Option sets have the same output name: ' + name)
        names.add(name.lower())
        result.append({'name': name, 'path': str(path) if path is not None else None, **resolved})
    return result
