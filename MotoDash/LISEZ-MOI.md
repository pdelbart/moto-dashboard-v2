# Moto Dash : obtenir l'APK

Ce dossier contient le projet complet. GitHub le compile gratuitement et vous rend l'APK.

## 1. Compiler l'APK avec GitHub (une seule fois)

1. Créez un compte gratuit sur github.com, puis **New repository** (nom : `MotoDash`, public ou privé).
2. Dézippez ce dossier sur votre ordinateur.
3. Dans votre dépôt : **uploading an existing file**, puis glissez **tout le contenu** du dossier `MotoDash`
   (y compris le dossier caché `.github`). Cliquez **Commit changes**.
   - Si `.github` n'a pas été envoyé : **Add file > Create new file**, tapez le nom
     `.github/workflows/build.yml` et collez le contenu du fichier `build.yml` fourni.
4. Onglet **Actions** : la ligne « Build APK » démarre toute seule (3 à 6 minutes).
   Sinon : Actions > Build APK > **Run workflow**.
5. Quand elle est verte, ouvrez-la et téléchargez **MotoDash-apk** en bas de page (un zip contenant `app-debug.apk`).
6. Si la ligne devient rouge, ouvrez-la et envoyez-moi le message d'erreur : je corrige.

## 2. Installer sur le Galaxy S21

1. Envoyez `app-debug.apk` sur le téléphone, ouvrez-le, autorisez « Installer des applis inconnues » pour l'appli utilisée.
2. Lancez **Moto Dash** et acceptez : position, Bluetooth.
3. Appuyez sur **Autoriser** dans la colonne Notifications, puis activez Moto Dash.
   Si l'option est grisée : Réglages > Applications > Moto Dash > menu ⋮ > **Autoriser les paramètres restreints**,
   puis recommencez.
4. Sur le guidon : téléphone en paysage, branché sur une alimentation.

## 3. Utilisation

- **Itinéraire** : tapez une destination et OK, choisissez dans la liste, ou faites un **appui long sur la carte**.
  L'appli calcule la route et la suit (virages, distance, heure d'arrivée, recalcul si vous sortez de la route).
- **Musique** : Deezer (ou autre lecteur) s'affiche dans la barre du bas, avec précédent, lecture, suivant.
- **Notifications** : touchez une notification pour la faire disparaître.
- **Zoom** : boutons + et -. La carte reste centrée sur vous, sauf 8 secondes après un geste sur la carte.

## Limites à connaître

- Google Maps ne permet pas à une autre appli de lire l'itinéraire chargé dans l'appli Google Maps.
  La carte est donc OpenStreetMap, avec un calcul d'itinéraire voiture (serveur public OSRM, sans guidage vocal).
- La batterie de l'appareil Bluetooth ne s'affiche que si Android la fournit (souvent non : l'appli affiche alors ON et le nom).
- Les données internet (carte, itinéraire, température) demandent une connexion.
