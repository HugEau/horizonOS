#!/usr/bin/env python3
"""Serveur d'aperçu : sert out/tizen et relaie les requêtes externes (/proxy?u=URL) pour contourner CORS.

    python3 tizen/build.py --preview
    python3 tools/devserver.py        # puis ouvre http://localhost:8765/index.html?proxy=/proxy

Uniquement pour prévisualiser dans un navigateur de bureau : sur la TV, le widget n'a pas besoin de proxy.
"""
import functools
import http.server
import os
import subprocess
import urllib.parse

ROOT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'out', 'tizen')


class Handler(http.server.SimpleHTTPRequestHandler):
    def translate_path(self, path):
        # NAS_DIR=/chemin : expose ce dossier sur /nas/ pour tester la navigation des films (index HTML).
        nas = os.environ.get('NAS_DIR')
        if nas and urllib.parse.urlparse(path).path.startswith('/nas/'):
            return os.path.join(nas, urllib.parse.unquote(urllib.parse.urlparse(path).path[len('/nas/'):]))
        return super().translate_path(path)

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        if parsed.path != '/proxy':
            return super().do_GET()
        target = urllib.parse.parse_qs(parsed.query).get('u', [''])[0]
        if not target.startswith(('http://', 'https://')):
            return self._send(400, b'u=? manquant')
        # curl plutôt que urllib : il utilise les certificats du système (le Python de python.org n'en a pas).
        run = subprocess.run(['curl', '-sL', '-m', '15', '-A', 'Mozilla/5.0', target], capture_output=True, timeout=20)
        if run.returncode != 0:
            return self._send(502, run.stderr or b'curl a echoue')
        self._send(200, run.stdout, 'application/xml' if run.stdout.lstrip()[:5] == b'<?xml' else 'text/html')

    def _send(self, status, body=b'', ctype='text/plain'):
        self.send_response(status)
        self.send_header('Content-Type', ctype)
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


if __name__ == '__main__':
    handler = functools.partial(Handler, directory=ROOT)
    http.server.ThreadingHTTPServer(('127.0.0.1', 8765), handler).serve_forever()
