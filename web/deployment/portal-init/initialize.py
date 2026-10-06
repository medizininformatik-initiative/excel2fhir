"""Initialize local portal TLS and login once; retain them across restarts."""
import json
import os
from pathlib import Path
import secrets
import subprocess

root = Path('/setup')
root.mkdir(exist_ok=True)
if not (root / 'cert.pem').exists():
    subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes',
                    '-keyout', str(root / 'key.pem'), '-out', str(root / 'cert.pem'),
                    '-days', '365', '-subj', '/CN=localhost',
                    '-addext', 'subjectAltName=DNS:localhost,IP:127.0.0.1'], check=True)
    (root / 'key.pem').chmod(0o644)
if not (root / 'realm.json').exists():
    realm = json.loads(Path('/upstream/realm.json').read_text())
    password = secrets.token_urlsafe(24)
    for client in realm['clients']:
        if client['clientId'] == 'dataportal':
            client['redirectUris'] = ['https://localhost:5192/*']
            client['webOrigins'] = ['https://localhost:5192']
    realm['users'] = [{'username': 'workbench', 'enabled': True,
                       'email': 'workbench@example.invalid', 'emailVerified': True,
                       'firstName': 'Local', 'lastName': 'Workbench',
                       'credentials': [{'type': 'password', 'value': password, 'temporary': False}],
                       'realmRoles': ['DataportalUser', 'DataportalPowerUser', 'DataportalAdmin']}]
    (root / 'credentials.txt').write_text(json.dumps({'username': 'workbench', 'password': password}) + '\n')
    temporary = root / 'realm.json.tmp'
    temporary.write_text(json.dumps(realm))
    os.replace(temporary, root / 'realm.json')
