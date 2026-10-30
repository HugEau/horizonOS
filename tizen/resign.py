
#!/usr/bin/env python3
"""Resigne un paquet WGT avec la méthode de build.py.

Usage :
    python3 tizen/resign.py TizenBrew.wgt --sign PROFIL

Le fichier original est sauvegardé avant remplacement.
"""

import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

def tizen_cli():
    name = "tizen.bat" if os.name == "nt" else "tizen"
    home = os.path.expanduser("~")

    for base in (
        os.environ.get("TIZEN_STUDIO", ""),
        os.path.join(home, "tizen-studio"),
        os.path.join(home, "tizen-sdk"),
    ):
        path = os.path.join(base, "tools", "ide", "bin", name)
        if base and os.path.exists(path):
            return path

    return shutil.which("tizen")

def sign(wgt, profile):
    cli = tizen_cli()

    if not cli:
        sys.exit(
            "La signature demande les outils Tizen de Samsung "
            "(Tizen Studio). Introuvables : définis TIZEN_STUDIO."
        )

    wgt = Path(wgt).expanduser().resolve()

    if not wgt.is_file():
        sys.exit(f"Fichier WGT introuvable : {wgt}")

    if wgt.suffix.lower() != ".wgt":
        sys.exit("Le fichier fourni doit avoir l'extension .wgt.")

    # Sauvegarde du paquet original.
    backup = wgt.with_name(wgt.stem + ".original.wgt")

    if not backup.exists():
        shutil.copy2(wgt, backup)
        print(f"Sauvegarde : {backup}")

    out = tempfile.mkdtemp(prefix="tizen-resign-")

    try:
        # Même méthode que dans le build.py fourni.
        run = subprocess.run(
            [
                cli,
                "package",
                "-t", "wgt",
                "-s", profile,
                "-o", out,
                "--", str(wgt),
            ],
            capture_output=True,
            text=True,
        )

        made = [
            name for name in os.listdir(out)
            if name.endswith(".wgt")
        ]

        if run.returncode != 0 or not made:
            sys.exit(
                "`tizen package` n'a pas signé le paquet :\n"
                + (run.stdout + run.stderr).strip()
            )

        signed = os.path.join(out, made[0])

        # Évite de remplacer le fichier si la sortie est vide.
        if os.path.getsize(signed) == 0:
            sys.exit("Le paquet signé est vide.")

        # Remplacement seulement après réussite de la signature.
        replacement = wgt.with_name(wgt.stem + ".signed.wgt")
        shutil.copy2(signed, replacement)

        print(f"Paquet signé créé : {replacement}")
        print(f"Original conservé : {backup}")

    finally:
        shutil.rmtree(out, ignore_errors=True)


def main():
    args = sys.argv[1:]

    if len(args) < 3 or "--sign" not in args:
        sys.exit(
            "Usage : python3 tizen/resign.py FICHIER.wgt --sign PROFIL"
        )

    wgt = args[0]
    index = args.index("--sign")

    if index + 1 >= len(args):
        sys.exit("Il manque le nom du profil après --sign.")

    sign(wgt, args[index + 1])


if __name__ == "__main__":
    main()
