"""Allowlisted lifecycle operations for workbench-owned upstream Data Nodes."""
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import threading
import time
from uuid import UUID, uuid4

ROOT = Path('/environments')
UPSTREAM = Path('/upstream/data-node')
LOCK_FILE = Path('/controller/data-node.lock.json')
SERVICES = ['fhir-server', 'torch', 'torch-nginx', 'rev-proxy']
MUTEX = threading.RLock()
EXECUTOR = ThreadPoolExecutor(max_workers=1)


def command(arguments, timeout=180, check=True):
    result = subprocess.run(arguments, capture_output=True, text=True, timeout=timeout)
    if check and result.returncode:
        raise ValueError((result.stderr or result.stdout)[-4000:] or 'Environment operation failed')
    return result.stdout


def container():
    return json.loads(command(['docker', 'inspect', os.environ['HOSTNAME']]))[0]


def host_root():
    mounts = [item for item in container()['Mounts'] if item['Destination'] == str(ROOT) and item['Type'] == 'bind']
    if len(mounts) != 1:
        raise ValueError('The controller requires its dedicated environment directory mount')
    return Path(mounts[0]['Source'])


def checked_id(identifier):
    try:
        parsed = str(UUID(identifier))
    except ValueError as error:
        raise FileNotFoundError('Environment not found') from error
    if parsed != identifier:
        raise FileNotFoundError('Environment not found')
    return parsed


def read(identifier):
    return json.loads((ROOT / checked_id(identifier) / 'environment.json').read_text())


def save(item):
    folder = ROOT / item['id']
    path = folder / 'environment.tmp'
    path.write_text(json.dumps(item, ensure_ascii=False, indent=2))
    path.replace(folder / 'environment.json')


def public(item):
    return {key: item.get(key) for key in ('id', 'name', 'state', 'created', 'error', 'ports', 'services', 'dataset', 'operation', 'upstream')}


def summaries():
    with MUTEX:
        return [public(json.loads(path.read_text())) for path in sorted(ROOT.glob('*/environment.json'))]


def verify_upstream():
    pinned = json.loads(LOCK_FILE.read_text())
    digest = hashlib.sha256()
    for path in sorted(UPSTREAM.rglob('*')):
        if path.is_file():
            digest.update(path.relative_to(UPSTREAM).as_posix().encode() + b'\0' + path.read_bytes() + b'\0')
    if digest.hexdigest() != pinned['dataNodeTreeSha256']:
        raise ValueError('The upstream Data Node differs from its pinned version')
    return pinned


def compose_text(item, host, proxy_port=0):
    folder = ROOT / item['id']
    node = folder / 'data-node'
    host_folder = host / item['id']
    host_node = host_folder / 'data-node'
    auth = host_node / 'auth'
    def mounts(values):
        return '\n'.join('      - ' + json.dumps(str(source) + ':' + target) for source, target in values)
    return f'''name: {item['project']}
services:
  fhir-server:
    extends:
      file: {json.dumps(str(node / 'fhir-server/docker-compose.yml'))}
      service: fhir-server
    ports: !override ["127.0.0.1::8080"]
    environment:
      JAVA_TOOL_OPTIONS: -Xmx768m
      DB_BLOCK_CACHE_SIZE: 128
      OPENID_PROVIDER_URL: ""
      LOG_LEVEL: info
    volumes: !override
{mounts([(host_node / 'fhir-server/custom-search-parameters.json', '/app/custom-search-parameters.json:ro'), ('blaze-data', '/app/data'), (auth / 'trust-store.p12', '/app/trust-store.p12:ro')])}
    mem_limit: 1280m
    cpus: 1
    restart: "no"
  torch:
    extends:
      file: {json.dumps(str(node / 'torch/docker-compose.yml'))}
      service: torch
    ports: !override []
    environment:
      JAVA_TOOL_OPTIONS: -Xmx2048m
      TORCH_FHIR_OAUTH_ISSUER_URI: ""
      TORCH_BASE_URL: https://localhost:{proxy_port}/torch
      TORCH_OUTPUT_FILE_SERVER_URL: https://localhost:{proxy_port}/torch/fileserver
      TORCH_FHIR_URL: http://fhir-server:8080/fhir
      TORCH_MAXCONCURRENCY: 1
      TORCH_BATCHSIZE: 10
    volumes: !override
{mounts([('triangle-torch-data-store', '/app/output'), (auth, '/app/certs:ro')])}
    mem_limit: 3072m
    cpus: 1
    restart: "no"
  torch-nginx:
    extends:
      file: {json.dumps(str(node / 'torch/docker-compose.yml'))}
      service: torch-nginx
    ports: !override []
    volumes: !override
{mounts([(host_node / 'torch/torch.nginx.conf', '/etc/nginx/nginx.conf:ro'), ('triangle-torch-data-store', '/app/output')])}
    mem_limit: 64m
    restart: "no"
  rev-proxy:
    extends:
      file: {json.dumps(str(node / 'rev-proxy/docker-compose.yml'))}
      service: rev-proxy
    ports: !override ["127.0.0.1:{proxy_port if proxy_port else ''}:8443"]
    volumes: !override
{mounts([(auth / 'cert.pem', '/etc/nginx/certs/cert.pem:ro'), (auth / 'cert.key', '/etc/nginx/certs/key.pem:ro'), (auth / '.htpasswd', '/etc/nginx/.htpasswd:ro'), (host_node / 'rev-proxy/context-paths.nginx.conf', '/etc/nginx/nginx.conf:ro'), (host_node / 'rev-proxy/conf.d', '/etc/nginx/templates:ro')])}
    mem_limit: 64m
    restart: "no"
volumes:
  blaze-data:
  triangle-torch-data-store:
'''


def write_compose(item, proxy_port=0):
    path = ROOT / item['id'] / 'compose.yml'
    path.write_text(compose_text(item, host_root(), proxy_port))
    item['composeSha256'] = hashlib.sha256(path.read_bytes()).hexdigest()
    save(item)


def compose(item, arguments, timeout=180):
    path = ROOT / item['id'] / 'compose.yml'
    if hashlib.sha256(path.read_bytes()).hexdigest() != item['composeSha256']:
        raise ValueError('Environment configuration changed outside the controller')
    return command(['docker', 'compose', '--project-name', item['project'], '-f', str(path), *arguments], timeout)


def create(name):
    name = name.strip()
    if not name or len(name) > 120 or any(ord(character) < 32 for character in name):
        raise ValueError('Use an environment name of 1–120 characters')
    with MUTEX:
        if any(item['name'].casefold() == name.casefold() for item in summaries()):
            raise ValueError('An environment with this name already exists')
        pinned = verify_upstream()
        identifier = str(uuid4())
        folder = ROOT / identifier
        folder.mkdir(parents=True, mode=0o700)
        try:
            shutil.copytree(UPSTREAM, folder / 'data-node')
            for template in (folder / 'data-node').rglob('.env.default'):
                shutil.copyfile(template, template.with_name('.env'))
            auth = folder / 'data-node/auth'
            command(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-keyout', str(auth / 'cert.key'),
                     '-out', str(auth / 'cert.pem'), '-days', '365', '-subj', '/CN=localhost',
                     '-addext', 'subjectAltName=DNS:localhost,IP:127.0.0.1'])
            command(['keytool', '-importcert', '-storetype', 'PKCS12', '-keystore', str(auth / 'trust-store.p12'),
                     '-storepass', 'insecure', '-alias', 'local-workbench', '-file', str(auth / 'cert.pem'), '-noprompt'])
            password = secrets.token_urlsafe(24)
            hashed = subprocess.run(['openssl', 'passwd', '-apr1', '-stdin'], input=password + '\n', capture_output=True, text=True, check=True).stdout.strip()
            (auth / '.htpasswd').write_text('workbench:' + hashed + '\n')
            (auth / 'cert.key').chmod(0o644)
            (folder / 'credentials.json').write_text(json.dumps({'username': 'workbench', 'password': password}))
            (folder / 'credentials.json').chmod(0o600)
            item = {'id': identifier, 'name': name, 'project': 'excel2fhir-diz-' + identifier.replace('-', ''),
                    'state': 'stopped', 'created': time.time(), 'upstream': pinned, 'ports': {}, 'services': [], 'dataset': None}
            write_compose(item)
            return public(item)
        except Exception:
            shutil.rmtree(folder)
            raise


def service_status(item):
    output = compose(item, ['ps', '--all', '--format', 'json'])
    entries = json.loads(output) if output.strip().startswith('[') else [json.loads(line) for line in output.splitlines() if line.strip()]
    return [{'name': entry.get('Service'), 'state': entry.get('State'), 'health': entry.get('Health', '')} for entry in entries]


def port(item, service, container_port):
    address = compose(item, ['port', service, str(container_port)]).strip()
    if not address.startswith('127.0.0.1:'):
        raise ValueError('Environment port is not bound to loopback')
    return int(address.rsplit(':', 1)[1])


def helper(item, action, timeout=240):
    current = container()
    name = item['project'] + '-probe'
    try:
        return command(['docker', 'run', '--rm', '--name', name, '--network', item['project'] + '_default',
                        '--memory=256m', '--cpus=0.5', current['Image'], 'python', '/controller/probe.py', action], timeout)
    finally:
        command(['docker', 'rm', '-f', name], check=False)


def operate(identifier, action):
    try:
        with MUTEX:
            item = read(identifier)
        if action == 'start':
            # Docker allocates free loopback ports; the proxy port then fixes TORCH's URLs.
            write_compose(item)
            compose(item, ['up', '-d', 'rev-proxy'], timeout=600)
            proxy = port(item, 'rev-proxy', 8443)
            write_compose(item, proxy)
            compose(item, ['up', '-d', *SERVICES], timeout=600)
            helper(item, 'ready')
            item['ports'] = {'fhir': port(item, 'fhir-server', 8080), 'torch': proxy}
            item['state'] = 'ready'
        elif action == 'stop':
            compose(item, ['stop', '--timeout', '20', *SERVICES])
            item['state'] = 'stopped'
        else:
            raise ValueError('Unknown environment action')
        item['services'] = service_status(item)
        item['operation'] = None
        item['error'] = None
        with MUTEX:
            save(item)
    except Exception as error:
        with MUTEX:
            item = read(identifier)
            item.update(state='error', error=str(error), operation=None)
            save(item)


def submit(identifier, action):
    if action not in {'start', 'stop'}:
        raise ValueError('Unknown environment action')
    with MUTEX:
        item = read(identifier)
        if item['state'] in {'starting', 'stopping', 'loading'}:
            raise ValueError('An operation is already running for this environment')
        item.update(state='starting' if action == 'start' else 'stopping', operation=action, error=None)
        save(item)
        EXECUTOR.submit(operate, identifier, action)
        return public(item)


def recover():
    ROOT.mkdir(parents=True, exist_ok=True)
    for path in ROOT.glob('*/environment.json'):
        item = json.loads(path.read_text())
        if item['state'] in {'starting', 'stopping', 'loading'}:
            item.update(state='error', operation=None, error='Controller restarted during the operation; start or stop the environment to reconcile its services')
            save(item)
