# RandomTeleport

Plugin Paper pour Minecraft 1.21.10.

## Menu

La commande `/rtp` ouvre un menu 3 lignes inspiré de l'interface de la capture :

- emplacement 11 : Overworld
- emplacement 13 : Nether
- emplacement 15 : The End
- emplacement 17 : Fermer

Les emplacements sont configurables dans `config.yml`.

## Téléportation

Le plugin cherche un emplacement sûr à une distance configurable du point 0,0 :

- distance minimale : 500 blocs
- distance maximale : 5000 blocs
- 40 essais
- cooldown : 0 seconde par défaut

Pour le Nether, le plugin recherche une zone avec un sol solide et deux blocs d'air.

## Commande

`/rtp`

Alias : `/randomtp`, `/randomteleport`

Permission : `randomteleport.use`

## Compilation

Dans le dossier `randomteleport` :

`mvn package`

Le JAR est généré dans `target/RandomTeleport-1.0.0.jar`.

Le workflow GitHub Actions construit automatiquement le plugin à chaque modification du dossier.
