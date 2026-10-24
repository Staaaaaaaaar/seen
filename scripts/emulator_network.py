#!/usr/bin/env python3
"""Optional emulator CONNECT proxy. TLS stays end-to-end; no credential logging."""
import argparse
import contextlib
import ipaddress
import json
import os
from pathlib import Path
import select
import signal
import socket
import socketserver
import subprocess
import threading
import time
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
TMDB_HOSTS = {'api.themoviedb.org', 'image.tmdb.org'}


class Resolver:
    def __init__(self, fetch=None):
        self.fetch = fetch or self._fetch
        self.cache = {}
        self.lock = threading.Lock()

    @staticmethod
    def _fetch(host):
        # urllib uses the developer's existing environment proxy, if configured.
        url = 'https://cloudflare-dns.com/dns-query?' + urllib.parse.urlencode({'name': host, 'type': 'A'})
        request = urllib.request.Request(url, headers={'Accept': 'application/dns-json'})
        with urllib.request.urlopen(request, timeout=10) as response:
            body = response.read(65537)
            if len(body) > 65536:
                raise OSError('DNS response too large')
            return json.loads(body)

    def resolve(self, host):
        if host not in TMDB_HOSTS:
            return [host]
        with self.lock:
            cached = self.cache.get(host)
            if cached and cached[0] > time.monotonic():
                return cached[1]
            data = self.fetch(host)
            if data.get('Status') != 0:
                raise OSError('DNS lookup failed')
            answers = [x for x in data.get('Answer', []) if x.get('type') == 1]
            addresses = []
            for answer in answers:
                address = ipaddress.IPv4Address(answer['data'])
                if not address.is_global:
                    raise OSError('DNS returned a non-public address')
                if str(address) not in addresses:
                    addresses.append(str(address))
            if not addresses:
                raise OSError('DNS returned no IPv4 address')
            ttl = min(300, min(max(1, int(x.get('TTL', 30))) for x in answers))
            self.cache[host] = (time.monotonic() + ttl, addresses)
            return addresses


def destination(authority):
    parsed = urllib.parse.urlsplit('//' + authority)
    if parsed.username or parsed.password or parsed.path or parsed.query or parsed.fragment:
        raise ValueError('Invalid CONNECT authority')
    if not parsed.hostname or parsed.port != 443:
        raise ValueError('Only HTTPS CONNECT on port 443 is supported')
    return parsed.hostname.lower()


class Tunnel(socketserver.StreamRequestHandler):
    def handle(self):
        self.connection.settimeout(15)
        try:
            first = self.rfile.readline(8192).decode('ascii').split()
            if len(first) != 3 or first[0] != 'CONNECT':
                raise ValueError('HTTPS CONNECT required')
            host = destination(first[1])
            size = 0
            while True:
                line = self.rfile.readline(8192)
                size += len(line)
                if size > 32768:
                    raise ValueError('Headers too large')
                if line in (b'\r\n', b'\n', b''):
                    break
            upstream = None
            for address in self.server.resolver.resolve(host):
                try:
                    upstream = socket.create_connection((address, 443), timeout=5)
                    break
                except OSError:
                    continue
            if upstream is None:
                raise OSError('Connection failed')
        except (OSError, ValueError, UnicodeError, KeyError):
            # Never log request headers, credentials, URLs or exception messages.
            with contextlib.suppress(OSError):
                self.wfile.write(b'HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\n\r\n')
            return
        with upstream:
            try:
                self.wfile.write(b'HTTP/1.1 200 Connection Established\r\n\r\n')
                self.wfile.flush()
                self.connection.settimeout(None)
                upstream.settimeout(None)
                while True:
                    ready, _, _ = select.select([self.connection, upstream], [], [], 60)
                    if not ready:
                        return
                    for source in ready:
                        body = source.recv(65536)
                        if not body:
                            return
                        (upstream if source is self.connection else self.connection).sendall(body)
            except OSError:
                return


class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True
    resolver = None


def adb(serial, *args):
    return subprocess.check_output(['adb', '-s', serial, *args], text=True, stderr=subprocess.PIPE).strip()


def restore(serial, configured, original):
    # Don't overwrite a proxy the user changed while this helper was running.
    if adb(serial, 'shell', 'settings', 'get', 'global', 'http_proxy') == configured:
        if original == 'null':
            adb(serial, 'shell', 'settings', 'delete', 'global', 'http_proxy')
        else:
            adb(serial, 'shell', 'settings', 'put', 'global', 'http_proxy', original)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--stop', action='store_true')
    args = parser.parse_args()
    if not args.serial.startswith('emulator-') or not args.serial.removeprefix('emulator-').isdigit():
        parser.error('Only an explicitly selected emulator is supported')
    state = ROOT / '.tools' / ('emulator-network-' + args.serial + '.json')
    if args.stop:
        if not state.exists():
            print('没有本工具的运行记录。'); return
        saved = json.loads(state.read_text())
        pid = saved['pid']
        process = Path('/proc') / str(pid) / 'cmdline'
        if process.exists():
            words = process.read_bytes().split(b'\0')
            if str(Path(__file__).resolve()).encode() not in words or args.serial.encode() not in words:
                raise SystemExit('PID 已被复用，未停止其他进程。')
            os.kill(pid, signal.SIGTERM)
            print('已请求停止；工具将恢复原代理。')
        else:
            restore(args.serial, saved['configured'], saved['original'])
            state.unlink()
            print('已恢复上次异常退出前的代理。')
        return
    if state.exists():
        raise SystemExit('已有运行记录；请先使用 --stop，不重复覆盖代理。')
    avd = adb(args.serial, 'emu', 'avd', 'name').splitlines()[0]
    if avd != 'Seen_API36':
        raise SystemExit('仅对项目专用 Seen_API36 配置，不修改其他模拟器。')
    resolver = Resolver()
    resolver.resolve('api.themoviedb.org')  # Fail before changing device settings.
    original = adb(args.serial, 'shell', 'settings', 'get', 'global', 'http_proxy')
    with Server(('127.0.0.1', 0), Tunnel) as server:
        server.resolver = resolver
        configured = '10.0.2.2:' + str(server.server_address[1])
        state.parent.mkdir(exist_ok=True)
        with state.open('x') as output:
            json.dump({'pid': os.getpid(), 'original': original, 'configured': configured}, output)
        state.chmod(0o600)
        def terminate(*_):
            raise KeyboardInterrupt
        signal.signal(signal.SIGTERM, terminate)
        try:
            adb(args.serial, 'shell', 'settings', 'put', 'global', 'http_proxy', configured)
            print('TMDB 模拟器联网辅助已启用；HTTPS 证书仍由应用校验。', flush=True)
            print('保持此终端运行；Ctrl+C 或同命令加 --stop 恢复原代理。', flush=True)
            server.serve_forever()
        except KeyboardInterrupt:
            pass
        finally:
            try:
                restore(args.serial, configured, original)
            except subprocess.CalledProcessError:
                print('设备不可达；保留恢复记录，设备恢复后请执行 --stop。', flush=True)
            else:
                state.unlink(missing_ok=True)
                print('已恢复原代理。', flush=True)


if __name__ == '__main__':
    main()
