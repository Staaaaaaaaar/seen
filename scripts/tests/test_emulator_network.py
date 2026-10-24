"""DNS response handling and restoration of a user's emulator proxy settings."""
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import Mock, patch

spec = importlib.util.spec_from_file_location('emulator_network', Path(__file__).resolve().parents[1] / 'emulator_network.py')
network = importlib.util.module_from_spec(spec)
spec.loader.exec_module(network)


class EmulatorNetworkTest(unittest.TestCase):
    def test_only_tmdb_uses_encrypted_dns_and_cache_respects_ttl(self):
        fetch = Mock(return_value={'Status': 0, 'Answer': [{'type': 1, 'data': '1.1.1.1', 'TTL': 2}]})
        resolver = network.Resolver(fetch)
        with patch.object(network.time, 'monotonic', return_value=100):
            self.assertEqual(resolver.resolve('api.themoviedb.org'), ['1.1.1.1'])
            self.assertEqual(resolver.resolve('api.themoviedb.org'), ['1.1.1.1'])
            self.assertEqual(resolver.resolve('example.com'), ['example.com'])
        self.assertEqual(fetch.call_count, 1)
        with patch.object(network.time, 'monotonic', return_value=103):
            resolver.resolve('api.themoviedb.org')
        self.assertEqual(fetch.call_count, 2)

    def test_invalid_dns_answers_fail_without_caching(self):
        for reply in [{'Status': 3}, {'Status': 0, 'Answer': []},
                      {'Status': 0, 'Answer': [{'type': 1, 'data': '127.0.0.1'}]}]:
            resolver = network.Resolver(lambda _: reply)
            with self.assertRaises(OSError):
                resolver.resolve('api.themoviedb.org')
            self.assertFalse(resolver.cache)

    def test_connect_authority_does_not_accept_urls_or_credentials(self):
        self.assertEqual(network.destination('api.themoviedb.org:443'), 'api.themoviedb.org')
        for value in ['api.themoviedb.org:80', 'user:secret@api.themoviedb.org:443',
                      'api.themoviedb.org:443/path', 'api.themoviedb.org:443?token=secret']:
            with self.assertRaises(ValueError):
                network.destination(value)

    def test_stop_deletes_previously_unset_proxy(self):
        with patch.object(network, 'adb', return_value='10.0.2.2:1234') as adb:
            network.restore('emulator-5556', '10.0.2.2:1234', 'null')
            self.assertEqual(adb.call_args.args, ('emulator-5556', 'shell', 'settings', 'delete', 'global', 'http_proxy'))

    def test_stop_restores_existing_proxy(self):
        with patch.object(network, 'adb', return_value='10.0.2.2:1234') as adb:
            network.restore('emulator-5556', '10.0.2.2:1234', 'original.example:8888')
            self.assertEqual(adb.call_args.args[-1], 'original.example:8888')

    def test_stop_preserves_proxy_changed_by_user(self):
        with patch.object(network, 'adb', return_value='changed.example:8080') as adb:
            network.restore('emulator-5556', '10.0.2.2:1234', 'null')
            self.assertEqual(adb.call_count, 1)
