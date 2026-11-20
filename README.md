# ZoomScanner

Application Android de numérisation de documents (scanner, OCR, PDF), du Dr Ruphin LIBOTE MAETA.

## Télécharger l'application

Dernière version (APK) :
https://github.com/ruphinlibote120-spec/ZoomScanner/releases/latest/download/ZoomScanner.apk

Chaque mise à jour s'installe **par-dessus** la version existante, sans désinstaller
(clé de signature fixe + numéro de version qui augmente à chaque compilation).

## Fonctions de la version 1 (fonctionnent sur le téléphone)

- Scanner avec caméra en direct, détection des bords, recadrage, filtres, import depuis la galerie
- Cartes d'identité (recto + verso sur une page), horodatage
- Extraire le texte (OCR, hors ligne)
- Importer des images et des fichiers PDF
- PDF en images, PDF en image longue
- Signature, filigrane, fusionner, extraire des pages, réorganiser, verrouiller (mot de passe), compresser
- Imprimer, scanner un QR code

Les outils d'IA (Word/Excel/PPT, gomme intelligente, restauration de photo, Solver AI…) viendront dans les versions suivantes.

## Compilation

Le fichier `.github/workflows/build.yml` compile l'APK automatiquement à chaque envoi sur `main`
et le publie dans l'onglet **Releases**.
