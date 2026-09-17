# EventPlugin

Plugin Paper pour Minecraft 1.21.5.

## Fonctionnalites

- `/event` ouvre une interface GUI avec le nom, le titre et la description de l'evenement.
- `/évent` est disponible comme alias.
- Une actionbar est envoyee automatiquement a tous les joueurs toutes les 2 secondes.
- Configuration dans `plugins/EventPlugin/config.yml`.
- Les administrateurs peuvent modifier les informations sans editer le fichier :
  - `/event set name <texte>`
  - `/event set title <texte>`
  - `/event set description <texte>`
  - `/event set actionbar <texte>`
  - `/event reload`

Permission admin : `event.admin` (OP par defaut).

## Compilation

Depuis le dossier `event-plugin` :

```bash
mvn clean package
```

Le JAR est genere dans `event-plugin/target/EventPlugin.jar`.
