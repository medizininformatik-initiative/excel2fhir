"""Read-only probes for fixed local Compose services."""
from concurrent.futures import ThreadPoolExecutor
import json
import socket
import urllib.request


def reachable(url, *, health=False):
    try:
        with urllib.request.urlopen(url, timeout=2) as response:
            return response.status == 200 and (not health or json.load(response).get('status') == 'UP')
    except (OSError, ValueError):
        return False


def portal():
    if not reachable('http://dataportal-backend:8090/api/v6/actuator/health', health=True):
        return False
    if not reachable('http://dataportal-ui:8080/'):
        return False
    if not reachable('http://auth:8080/auth/realms/dataportal/.well-known/openid-configuration'):
        return False
    try:
        with socket.create_connection(('dataportal-nginx', 8443), timeout=2):
            return True
    except OSError:
        return False


def services():
    with ThreadPoolExecutor(max_workers=2) as executor:
        portal_result = executor.submit(portal)
        torch_result = executor.submit(reachable, 'http://torch:8080/actuator/health', health=True)
        return [{'id': 'portal', 'available': portal_result.result()},
                {'id': 'torch', 'available': torch_result.result()}]
