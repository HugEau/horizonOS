#!/usr/bin/env python3
"""Construit Horizon-tizen.wgt, le paquet que la TV Samsung installe.

    python3 tizen/build.py                 # -> Horizon-tizen.wgt (non signé : TizenBrew Installer le signe)
    python3 tizen/build.py --sign PROFIL   # signé avec un profil de certificat des outils Tizen de Samsung
    python3 tizen/build.py --preview       # laisse aussi les fichiers dans out/tizen pour les ouvrir dans un navigateur
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WEB = os.path.join(ROOT, 'web')
OUT = os.path.join(ROOT, 'out', 'tizen')
WGT = os.path.join(ROOT, 'Horizon-tizen.wgt')


def tizen_cli():
    name = 'tizen.bat' if os.name == 'nt' else 'tizen'
    home = os.path.expanduser('~')
    for base in (os.environ.get('TIZEN_STUDIO', ''), os.path.join(home, 'tizen-studio'), os.path.join(home, 'tizen-sdk')):
        path = os.path.join(base, 'tools', 'ide', 'bin', name)
        if base and os.path.exists(path):
            return path
    return shutil.which('tizen')


def sign(profile):
    cli = tizen_cli()
    if not cli:
        sys.exit("La signature demande les outils Tizen de Samsung (Tizen Studio). Introuvables : définis TIZEN_STUDIO.")
    out = tempfile.mkdtemp()
    try:
        run = subprocess.run([cli, 'package', '-t', 'wgt', '-s', profile, '-o', out, '--', WGT], capture_output=True, text=True)
        made = [n for n in os.listdir(out) if n.endswith('.wgt')]
        if run.returncode != 0 or not made:
            sys.exit('`tizen package` n\'a pas signé le paquet :\n' + (run.stdout + run.stderr).strip())
        shutil.move(os.path.join(out, made[0]), WGT)
    finally:
        shutil.rmtree(out, ignore_errors=True)


def collect():
    files = {}
    for folder, _, names in os.walk(WEB):
        for name in names:
            if name.startswith('.'):
                continue
            path = os.path.join(folder, name)
            with open(path, 'rb') as f:
                files[os.path.relpath(path, WEB).replace(os.sep, '/')] = f.read()
    for name in ('config.xml', 'icon.png'):
        with open(os.path.join(ROOT, 'tizen', name), 'rb') as f:
            files[name] = f.read()
    return files


def main():
    args = sys.argv[1:]
    profile = args[args.index('--sign') + 1] if '--sign' in args else None
    if not os.path.exists(os.path.join(ROOT, 'tizen', 'icon.png')):
        subprocess.check_call([sys.executable, os.path.join(ROOT, 'tools', 'make_icon.py')])
    files = collect()
    with open(os.path.join(ROOT, 'tizen', 'config.xml'), encoding='utf-8') as f:
        manifest = f.read()
    app_id = re.search(r'<tizen:application id="([^"]+)"', manifest).group(1)
    version = re.search(r'<widget [^>]*version="([^"]+)"', manifest).group(1)

    with zipfile.ZipFile(WGT, 'w', zipfile.ZIP_DEFLATED) as z:
        for path in sorted(files):
            z.writestr(path, files[path])
    if profile:
        sign(profile)
    if '--preview' in args:
        if os.path.isdir(OUT):
            shutil.rmtree(OUT)
        for path, content in files.items():
            target = os.path.join(OUT, path)
            os.makedirs(os.path.dirname(target), exist_ok=True)
            with open(target, 'wb') as f:
                f.write(content)
        print('Aperçu : sers out/tizen (python3 -m http.server -d out/tizen) et ouvre-le dans un navigateur.')
    print('Construit %s (%s %s, %d Ko, %s)' % (os.path.basename(WGT), app_id, version, os.path.getsize(WGT) // 1024,
                                              'signé avec "%s"' % profile if profile else 'non signé'))


if __name__ == '__main__':
    main()
