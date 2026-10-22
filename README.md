# Horizon

Un écran d'accueil pour TV Samsung (Tizen), inspiré de l'Apple TV : onglets, cartes qui grossissent au focus, ciel qui suit l'heure. Application web en ES5 pur, sans framework, packagée en `.wgt`.

| Besoin | Ce que fait Horizon |
|---|---|
| YouTube sans pub | Lance **TizenTube** (ou TizenBrew) s'il est installé, sinon explique comment l'installer. |
| Météo personnalisable | Widgets Open-Meteo : style (actuelle, heure par heure, 7 jours, détails), couleur, ordre, villes. |
| Spotify | Lance l'application Spotify de la TV. |
| Entrées HDMI | Bascule la TV sur TV / HDMI 1 à 4 (`tizen.tvwindow`). |
| Films du NAS | Navigation WebDAV ou index HTTP, lecteur natif Samsung (AVPlay), pistes audio, sous-titres, reprise. |
| Actus d'Honfleur | Honfleur-Infos, Le Pays d'Auge (actu.fr) et presse régionale, lecteur d'article intégré. |
| Facebook « Tu sais que tu es d'Honfleur » | QR code + ouverture dans le navigateur de la TV (pas de flux public). |

## Limites à connaître

- **Ce n'est pas un OS** : c'est une application qui se lance depuis Smart Hub, ou au démarrage avec *Autorun Last App*. Elle ne remplace pas l'écran d'accueil de Samsung.
- **VLC n'existe pas sur Tizen.** Les films passent par AVPlay, le décodeur de la TV. Il lit le HTTP, pas le SMB : le NAS doit exposer ses dossiers en **WebDAV** (Synology : paquet « Serveur WebDAV », `http://IP:5005/video/`) ou en index web.
- **YouTube sans pub = TizenTube**, un projet à part. Horizon ne peut pas intégrer le blocage de pubs lui-même ; il le lance.
- **Spotify** : lancement seulement. Une application web ne peut pas lire la session d'une autre app. Spotify Connect depuis le téléphone fonctionne vers la TV.
- Le comportement sur une vraie TV n'a pas été testé ici (aperçu navigateur uniquement). Les points à surveiller : lancement des apps, bascule HDMI, AVPlay avec identifiants dans l'URL.

## Construire

```bash
python3 tizen/build.py             # -> Horizon-tizen.wgt (non signé)
python3 tizen/build.py --preview   # + out/tizen pour un aperçu navigateur
```

Aperçu navigateur (1920×1080) : `python3 tools/devserver.py`, puis `http://localhost:8765/index.html?proxy=/proxy`. Le proxy ne sert qu'à contourner CORS hors de la TV.

## Installer sur la TV

1. **TV** : *Apps*, tape `1 2 3 4 5` à la télécommande, active **Developer mode**, saisis l'IP de l'ordinateur dans **Host PC IP**, redémarre la TV.
2. **GitHub** : pousse ce dépôt, puis `git tag v1.0.0 && git push --tags`. Le workflow `.github/workflows/release.yml` attache `Horizon-tizen.wgt` à la release.
3. **Ordinateur** : [TizenBrew Installer](https://github.com/reisxd/TizenBrewInstaller/releases/latest) › *Install from GitHub* › `ton-utilisateur/ton-depot`. Sur les TV 2023 et plus, il demande de te connecter à ton compte Samsung pour signer l'app.
4. **TV** : active *Autorun Last App* (réglages, Fonctions Smart) pour démarrer directement sur Horizon.

TV 2017 à 2022 : le paquet doit être signé avec un certificat Tizen (`python3 tizen/build.py --sign <profil>`, outils Tizen Studio) ; sans cela, la TV refuse l'installation. Sans GitHub : `python3 tizen/install.py <IP-de-la-TV>` installe un `.wgt` signé par SDB.

## Installer TizenTube (YouTube sans pub)

Voir le guide du projet : <https://github.com/reisxd/TizenTube> (`docs/README.md`). Deux voies : module TizenBrew `@foxreis/tizentube`, ou version autonome installée avec Apps2Samsung. Horizon le détecte par son nom et l'ouvre depuis la tuile YouTube.

## Structure

```
web/             l'application (index.html, css/, js/, icons/, fonts/, vendor/)
tizen/           config.xml, build.py (paquet .wgt), install.py (SDB), icon.png
tools/           devserver.py (aperçu), make_icon.py
android/         première tentative Android TV (Kotlin), abandonnée
```

Crédits : approche Tizen (liste des apps, entrées HDMI, installateur SDB) et icônes reprises de [Free My TV](https://github.com/simbasjungles/free-my-tv) (MIT, voir `LICENSE-free-my-tv`). Police Inter (OFL). QR code : qrcode-generator (MIT). Météo : Open-Meteo.
