# MaClasse

[![Licence : GPL v3](https://img.shields.io/badge/Licence-GPLv3-blue.svg)](LICENSE)
[![Plateforme](https://img.shields.io/badge/plateforme-Android%2010%2B-brightgreen.svg)](#pr%C3%A9requis)

Une application Android pour gérer sa journée de cours : listes d'élèves, plans de classe,
tirage au sort équitable, notes et rappels par élève, et un emploi du temps qui sait dans
quelle salle vous êtes. Tout reste sur le téléphone — pas de compte, pas de serveur, aucune
permission réseau.

Pensée pour les professeurs de collège et de lycée : semaines A/B, import des exports CSV
Pronote/ENT, interface en français.

*English version: [README.md](README.md).*

| Aujourd'hui | Élèves | Plan de classe |
|---|---|---|
| ![Aujourd'hui](docs/playstore/screenshots/phone/fr/01-today.png) | ![Élèves](docs/playstore/screenshots/phone/fr/03-class-students.png) | ![Plan](docs/playstore/screenshots/phone/fr/04-class-seating.png) |

## Fonctionnalités

**Classes et élèves** — créez vos classes, ajoutez les élèves avec une photo si vous voulez,
ou importez une liste depuis un export CSV Pronote/ENT (l'encodage et le séparateur sont
détectés ; vous choisissez les colonnes nom et prénom et vérifiez l'aperçu avant d'importer).

**Aujourd'hui** — les cours du jour sur une frise, celui en cours mis en avant, et les
rappels du jour. Toucher un cours ouvre la classe directement sur le plan de la salle où il
a lieu.

**Emploi du temps** — des créneaux hebdomadaires par classe, avec semaines A/B, chacun
rattaché à une salle. Modifiable semaine par semaine : annuler une séance, la rétablir, ou
ajouter un cours ponctuel. Une vue globale affiche toutes les classes sur la semaine.
Synchronisation possible vers un calendrier local « MaClasse » du téléphone.

**Salles et plans de classe** — dessinez la salle en touchant pour poser une table et en
faisant glisser pour la placer ; tables de 1 ou 2 places, pivotables pour les dispositions
en U. Les salles sont partagées entre les classes, et chaque classe peut garder plusieurs
plans nommés par salle. Les contraintes de séparation s'affichent en rouge quand deux élèves
qui ne doivent pas être ensemble se retrouvent à la même table.

**Listes de suivi** — pointer toute une classe pour une même chose : autorisations signées,
paiements de sortie, livres rendus. Date limite facultative, nombre de manquants affiché, et
on coche au fur et à mesure des retours.

**Tirage au sort** — tire un élève sans le reprendre tant que toute la classe n'est pas
passée, en excluant les absents du jour.

**Groupes** — génère des groupes équilibrés qui respectent les contraintes « ces deux-là pas
ensemble », et vous prévient quand elles ne peuvent pas toutes être satisfaites. Les
répartitions peuvent être enregistrées sous un nom.

**Fiche élève** — notes, informations personnalisées, et rappels qui arrivent en
notification : à une date et heure précise, dans le récapitulatif du matin, ou avant le
prochain cours avec cette classe.

**Sauvegarde et restauration** — toute la base, les photos et les réglages dans un seul
fichier, enregistré où le sélecteur du système le permet (Drive, carte SD…). Chiffrable par
mot de passe, puisque le fichier contient des données d'élèves.

**Langue et apparence** — français ou anglais, thème clair ou sombre, au choix dans
l'application, indépendamment des réglages du téléphone.

## Confidentialité

L'application n'a pas la permission `INTERNET`. Pas de compte, pas de statistiques, pas de
remontée de plantages : aucune donnée ne quitte l'appareil, sauf si vous exportez vous-même
une sauvegarde ou synchronisez votre calendrier. Les sauvegardes peuvent être chiffrées en
AES-256-GCM (PBKDF2-SHA256, 200 000 itérations). Voir [PRIVACY.md](PRIVACY.md).

## Prérequis

- Android 10 (API 29) ou plus récent
- Android Studio récent, ou un JDK pour compiler en ligne de commande — le démon Gradle est
  fixé à Java 25 dans `gradle/gradle-daemon-jvm.properties` et le télécharge au besoin
- SDK Android niveau 37 (`compileSdk`)

## Compilation

```bash
git clone https://github.com/nosari20/class-manager.git
cd class-manager
./gradlew assembleDebug          # APK dans app/build/outputs/apk/debug/
./gradlew installDebug           # compile et installe sur l'appareil connecté
./gradlew testDebugUnitTest      # tests unitaires
```

Sous Windows, utilisez `gradlew.bat`. Si plusieurs appareils sont connectés, définissez
`ANDROID_SERIAL` pour choisir la cible.

## À faire

- [ ] **Importer l'emploi du temps depuis un export `.ics` Pronote** — jours, horaires, salles
  et classes en un seul fichier, avec le rythme A/B déduit au lieu d'être saisi, et un écran de
  confirmation avant d'écrire quoi que ce soit. **En attente d'un vrai export** : le format du
  champ `SUMMARY` change d'un établissement à l'autre et détermine la reconnaissance des classes.
- [ ] **Découper un CSV d'élèves en plusieurs classes** — les exports Pronote contiennent en
  général une colonne « Classe » : un seul import pourrait créer toutes les classes.
- [ ] **Recevoir les fichiers depuis le menu Partager** — pour envoyer un `.ics` ou un `.csv`
  directement depuis Pronote ou une pièce jointe.
- [ ] **Modèles de salles** — démarrer d'une grille ou d'un U plutôt que poser chaque table.
- [ ] **Abonnement à l'emploi du temps** (volontairement reporté) — le même import depuis une
  URL iCal Pronote, actualisé tout seul. Coûte la permission `INTERNET` et stocke un jeton
  d'accès sur l'appareil.

Avant toute publication :

- [ ] Réactiver l'optimisation des builds de release, puis vérifier une restauration et une
  notification sur le bundle signé.
- [ ] Incrémenter le `versionCode` à chaque envoi.

Les bundles sont signés depuis Android Studio (*Generate Signed App Bundle*) : aucun
`signingConfig` dans les fichiers Gradle, aucun keystore près du dépôt.

L'`applicationId` est arrêté : `edu.fnosari.classmanager`, conservé tel quel. Il n'est jamais
visible par les utilisateurs, et le changer après publication créerait une application
distincte, sans mise à jour possible depuis l'ancienne.

## Contribuer

Les rapports de bugs et les correctifs sont les bienvenus : voir
[CONTRIBUTING.md](CONTRIBUTING.md) pour les conventions, et
[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) pour les règles de conduite. Faille de sécurité :
[SECURITY.md](SECURITY.md).

## Licence

Copyright (C) 2026 Florent Nosari.

Ce programme est un logiciel libre : vous pouvez le redistribuer et/ou le modifier selon les
termes de la GNU General Public License telle que publiée par la Free Software Foundation,
soit la version 3, soit (à votre choix) toute version ultérieure. Il est distribué dans
l'espoir qu'il sera utile, mais **sans aucune garantie**. Voir la [licence](LICENSE).

## Marques

Sans lien avec Index Éducation, ni approuvée par eux. « Pronote » est leur marque, citée
uniquement pour décrire les formats de fichiers que cette application sait importer.
