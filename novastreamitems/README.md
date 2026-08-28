# NovaStreamItems

Plugin Paper 1.21.10 / Java 21 pour créer des items custom avec resource pack.

## Item exemple

`paladium_pickaxe` : pioche en diamant technique avec modèle custom `NovaStreamItems`.

Commande :

`/novaitems give paladium_pickaxe [joueur] [quantité]`

## Resource pack

Le dossier `resourcepack/` contient la base du pack. Le serveur doit distribuer ce pack aux joueurs (ou les joueurs peuvent l'installer manuellement). Pour que le modèle de la pioche soit visible, le pack doit être installé.

## Ajouter d'autres items

1. Ajouter un identifiant dans le plugin.
2. Créer une définition d'item dans `resourcepack/assets/novastreamitems/items/`.
3. Créer le modèle dans `resourcepack/assets/novastreamitems/models/item/`.
4. Ajouter la texture PNG dans `resourcepack/assets/novastreamitems/textures/item/`.
