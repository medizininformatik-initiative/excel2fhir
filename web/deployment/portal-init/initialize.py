"""Initialize the loopback HTTP portal login and retain it across restarts."""
import json
import os
from pathlib import Path

root = Path('/setup')
root.mkdir(exist_ok=True)
if (root / 'realm.json').exists():
    realm = json.loads((root / 'realm.json').read_text())
else:
    realm = json.loads(Path('/upstream/realm.json').read_text())
    password = 'password'
    realm['users'] = [{'username': 'dataportaluser', 'enabled': True,
                       'email': 'dataportaluser@example.invalid', 'emailVerified': True,
                       'firstName': 'Data Portal', 'lastName': 'User',
                       'credentials': [{'type': 'password', 'value': password, 'temporary': False}],
                       'realmRoles': ['DataportalUser', 'DataportalPowerUser', 'DataportalAdmin']}]
    (root / 'credentials.txt').write_text(json.dumps({'username': 'dataportaluser', 'password': password}) + '\n')
realm['sslRequired'] = 'none'
for client in realm['clients']:
    if client['clientId'] == 'dataportal':
        client['redirectUris'] = ['http://localhost:5192/*']
        client['webOrigins'] = ['http://localhost:5192']
temporary = root / 'realm.json.tmp'
temporary.write_text(json.dumps(realm))
os.replace(temporary, root / 'realm.json')
