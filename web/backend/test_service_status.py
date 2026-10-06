import unittest
from unittest.mock import patch, MagicMock
import service_status


class ServiceStatusTests(unittest.TestCase):
    def test_health_requires_up_and_handles_network_and_invalid_json(self):
        for content, expected in [(b'{"status":"UP"}', True), (b'{"status":"DOWN"}', False), (b'not json', False)]:
            response = MagicMock()
            response.__enter__.return_value = response
            response.status = 200
            response.read.return_value = content
            with patch.object(service_status.urllib.request, 'urlopen', return_value=response):
                self.assertEqual(expected, service_status.reachable('http://torch:8080/actuator/health', health=True))
        with patch.object(service_status.urllib.request, 'urlopen', side_effect=OSError()):
            self.assertFalse(service_status.reachable('http://torch:8080/actuator/health', health=True))

    def test_portal_requires_backend_ui_auth_and_proxy(self):
        for checks in ([False], [True, False], [True, True, False]):
            with patch.object(service_status, 'reachable', side_effect=checks), patch.object(service_status.socket, 'create_connection') as connect:
                self.assertFalse(service_status.portal())
                connect.assert_not_called()
        with patch.object(service_status, 'reachable', return_value=True):
            with patch.object(service_status.socket, 'create_connection', side_effect=OSError()):
                self.assertFalse(service_status.portal())
            with patch.object(service_status.socket, 'create_connection'):
                self.assertTrue(service_status.portal())

    def test_services_report_independent_states(self):
        with patch.object(service_status, 'portal', return_value=False), patch.object(service_status, 'reachable', return_value=True):
            self.assertEqual([{'id': 'portal', 'available': False}, {'id': 'torch', 'available': True}], service_status.services())
