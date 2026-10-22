#!/usr/bin/env python3
"""Installe Horizon sur une TV Samsung en mode développeur, en Wi-Fi, sans le sdb de Samsung.

    python3 tizen/install.py 192.168.1.50              # installe ou met à jour, puis l'ouvre
    python3 tizen/install.py 192.168.1.50 --uninstall  # le retire

Construis et signe d'abord le paquet : python3 tizen/build.py --sign <profil>. Avant la première installation, sur la
TV : ouvre Apps, tape 1 2 3 4 5 à la télécommande, active le mode développeur avec l'IP de cet ordinateur comme
Host PC IP, puis redémarre la TV. (Sans certificat, utilise plutôt TizenBrew Installer : voir le README.)

Il parle SDB (l'ADB de Samsung, port 26101) lui-même : seul Python 3 est requis. Il copie le paquet dans le dossier
indiqué par la TV et l'installe avec `0 vd_appinstall`, la commande de TizenBrew Installer ; `0 was_execute` l'ouvre.
Repris du projet Free My TV (licence MIT, voir LICENSE-free-my-tv).
"""
import os
import re
import socket
import struct
import sys
import time
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WGT = os.path.join(ROOT, 'Horizon-tizen.wgt')
PORT = 26101
MAX_DATA = 4096   # what this side announces; sdbd keeps each packet within both sides' maximum
ON_TV = 'Apps, Paramètres, Horizon, Supprimer'   # le retirer depuis la TV elle-même


class Sdb:
    """The pieces of the SDB wire protocol this needs: ADB's framing (a 24-byte header whose checksum is a byte sum),
    streams opened with OPEN, and every WRTE answered with OKAY before the next one."""

    def __init__(self, ip):
        self.sock = socket.create_connection((ip, PORT), timeout=15)
        self.buf = b''
        self.next_id = 1
        self.okay, self.inbox, self.closed = {}, {}, set()
        self.send(b'CNXN', 0x01000000, MAX_DATA, b'host::\0')
        cmd, _, max_data, data = self.read()
        if cmd != b'CNXN':
            raise ConnectionError('the TV answered %r instead of connecting' % cmd)
        self.max = min(max_data, MAX_DATA)
        self.banner = data.rstrip(b'\0').decode('utf-8', 'replace')

    def send(self, cmd, arg0, arg1, data=b''):
        c = struct.unpack('<I', cmd)[0]
        self.sock.sendall(struct.pack('<6I', c, arg0, arg1, len(data), sum(data) & 0xFFFFFFFF, c ^ 0xFFFFFFFF) + data)

    def _recv(self, n):
        while len(self.buf) < n:
            chunk = self.sock.recv(65536)
            if not chunk:
                raise ConnectionError('the TV closed the connection')
            self.buf += chunk
        out, self.buf = self.buf[:n], self.buf[n:]
        return out

    def read(self):
        cmd, arg0, arg1, n, _, _ = struct.unpack('<6I', self._recv(24))
        return struct.pack('<I', cmd), arg0, arg1, self._recv(n) if n else b''

    def pump(self):
        """Reads one packet and files it under its stream: OKAY, data (answered with OKAY) or close."""
        cmd, remote, local, data = self.read()
        if cmd == b'OKAY':
            self.okay[local] = remote
        elif cmd == b'WRTE':
            self.inbox[local] = self.inbox.get(local, b'') + data
            self.send(b'OKAY', local, remote)
        elif cmd == b'CLSE':
            self.closed.add(local)

    def open(self, service):
        local, self.next_id = self.next_id, self.next_id + 1
        self.send(b'OPEN', local, 0, service.encode() + b'\0')
        while local not in self.okay:
            if local in self.closed:
                raise ConnectionError('the TV refused ' + service)
            self.pump()
        return local, self.okay.pop(local)

    def write(self, stream, data):
        local, remote = stream
        self.send(b'WRTE', local, remote, data)
        while local not in self.okay:
            if local in self.closed:
                raise ConnectionError('the TV closed the stream')
            self.pump()
        del self.okay[local]

    def read_until(self, stream, done, seconds):
        """The stream's text until done(text) is true, the TV closes it, or time runs out."""
        local = stream[0]
        end = time.time() + seconds
        while time.time() < end and local not in self.closed and not done(self.inbox.get(local, b'')):
            self.sock.settimeout(max(0.1, end - time.time()))
            try:
                self.pump()
            except socket.timeout:
                break
        self.sock.settimeout(15)
        return self.inbox.pop(local, b'')

    def shell(self, command, until=None, seconds=20):
        stream = self.open('shell:' + command)
        out = self.read_until(stream, lambda t: bool(until and re.search(until, t.decode('utf-8', 'replace'))), seconds)
        return out.decode('utf-8', 'replace')

    def capabilities(self):
        text = self.read_until(self.open('capability:'), lambda t: False, 5).decode('utf-8', 'replace')
        return dict(line.split(':', 1) for line in text.splitlines() if ':' in line)

    def push(self, path, data):
        """Copies data to path on the TV (sync SEND), then checks the TV's answer."""
        stream = self.open('sync:')
        spec = ('%s,%d' % (path, 0o100644)).encode()
        self.write(stream, b'SEND' + struct.pack('<I', len(spec)) + spec)
        step = self.max - 8
        for i in range(0, len(data), step):
            part = data[i:i + step]
            self.write(stream, b'DATA' + struct.pack('<I', len(part)) + part)
        self.write(stream, b'DONE' + struct.pack('<I', int(time.time())))
        answer = self.read_until(stream, lambda t: len(t) >= 8, 60)
        if answer[:4] != b'OKAY':
            raise ConnectionError('the TV didn\'t take the file: ' + answer[8:].decode('utf-8', 'replace'))
        self.send(b'WRTE', stream[0], stream[1], b'QUIT' + struct.pack('<I', 0))


def this_computer(ip):
    """This computer's address on the TV's network (what Host PC IP must be)."""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect((ip, PORT))
        return s.getsockname()[0]
    except OSError:
        return 'this computer\'s IP'


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    if len(args) != 1:
        sys.exit(__doc__)
    ip = args[0]
    uninstall = '--uninstall' in sys.argv
    if uninstall:
        # Removing needs only the app's ids: the manifest the package is built from has them, so no build is needed.
        with open(os.path.join(ROOT, 'tizen', 'config.xml'), encoding='utf-8') as f:
            manifest = f.read()
    else:
        if not os.path.exists(WGT):
            sys.exit('Build and sign it first: python3 tizen/build.py --sign <profile>')
        with zipfile.ZipFile(WGT) as z:
            if 'author-signature.xml' not in z.namelist():
                sys.exit('Horizon-tizen.wgt n\'est pas signé, et la TV n\'installe que des apps signées : '
                         'python3 tizen/build.py --sign <profil>')
            manifest = z.read('config.xml').decode('utf-8')
    tag = re.search(r'<tizen:application[^>]*>', manifest).group(0)
    app_id = re.search(r'\bid="([^"]+)"', tag).group(1)
    package = re.search(r'\bpackage="([^"]+)"', tag).group(1)

    try:
        sdb = Sdb(ip)
    except (OSError, ConnectionError) as e:
        sys.exit('Couldn\'t reach the TV on port %d (%s). On the TV: Apps, 1 2 3 4 5, Developer mode on with Host PC '
                 'IP %s, then restart the TV.' % (PORT, e, this_computer(ip)))
    caps = sdb.capabilities()
    print('Connected: %s, Tizen %s.' % (sdb.banner.split(':')[2] if sdb.banner.count(':') >= 2 else sdb.banner,
                                         caps.get('platform_version', '?')))

    if uninstall:
        out = sdb.shell('0 vd_appuninstall ' + package, r'spend time', 120).strip()
        if not re.search(r'uninstall completed', out):
            # Some TVs' sdbd refuses this command and answers nothing.
            sys.exit(('The TV said:\n' + out if out else 'The TV didn\'t answer.') + '\nRemove it on the TV instead: ' + ON_TV + '.')
        print('Removed.')
        return

    remote = caps.get('sdk_toolpath', '/home/owner/share/tmp/sdk_tools').rstrip('/') + '/Horizon-tizen.wgt'
    with open(WGT, 'rb') as f:
        data = f.read()
    print('Copying the app to the TV ...')
    sdb.push(remote, data)
    print('Installing ...')
    out = sdb.shell('0 vd_appinstall %s %s' % (package, remote), r'spend time', 120)
    if not re.search(r'install completed', out):
        hint = ''
        if 'different certificate' in out:
            hint = ('\nL\'Horizon installé sur la TV a été signé avec un autre certificat, et la TV ne le met à jour qu\'avec le '
                    'même. Retire-le sur la TV (' + ON_TV + '), puis relance.')
        sys.exit('The TV didn\'t finish installing. It said:\n' + out.strip() + hint)
    sdb.shell('0 was_execute ' + app_id, r'launched|spend time', 20)
    print('Installed and open on the TV.')
    print('Astuce : active « Autorun Last App » dans les réglages de la TV (Général, Fonctions Smart ; l\'emplacement '
          'change selon l\'année) et la TV s\'allumera directement sur Horizon.')


if __name__ == '__main__':
    main()
