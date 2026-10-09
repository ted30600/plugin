# NovaAntiCheat — Paper 1.21.10

Plugin anti-triche en développement pour Paper 1.21.10, compilé avec Java 21. Ce projet est isolé dans ce sous-dossier pour ne pas remplacer les autres plugins du dépôt.

## Contrôles inclus dans cette base
- Speed : échantillonnage des déplacements horizontaux.
- Fly : signaux de suspension inhabituelle en l'air.
- Phase : contrôle de collision avec les blocs.
- AutoClicker : fréquence des animations de bras.
- Reach : distance entre l'œil de l'attaquant et le centre de la cible.
- KillAura : fréquence d'attaques anormalement élevée.
- AntiKnockback : heuristique de déplacement après recul.
- Scaffold : fréquence de placements de blocs sous les pieds (heuristique, alertes uniquement).
- X-Ray : ratio de minerais rares par rapport aux blocs de roche minés.
- Alertes staff/console, niveaux de violation (VL), configuration et commandes d'administration.

## Limites importantes
Ceci est une première version heuristique, pas un anti-cheat commercial ni une garantie de détection. Plusieurs contrôles reposent sur les événements Bukkit et ne voient pas directement tous les paquets réseau. Les cheats peuvent contourner ces heuristiques. Fly, Phase et AntiKnockback peuvent générer des faux positifs selon la latence, les effets, les mécaniques personnalisées et les plugins de déplacement.

Le X-Ray ne peut pas être prouvé par un simple ratio de minerais : la génération du monde, la fortune, les mines naturelles et les habitudes du joueur influencent les résultats. Baritone et certains aim-assists ne peuvent pas être identifiés de façon fiable par ces seuls événements.

Les bannissements automatiques sont désactivés par défaut. Ne les activez pas sur un serveur public sans tests, réglage des seuils et observation des journaux. Aucun anti-cheat ne peut garantir de détecter toutes les triches instantanément sans faux positifs.

## Compilation
Prérequis : Java 21 et Maven 3.9+. Dans le dossier `NovaAntiCheat` :
```bash
mvn clean package
```
Le fichier compilé sera `target/NovaAntiCheat.jar`.

Une GitHub Action est configurée pour compiler le projet et publier le JAR comme artefact de workflow quand les fichiers du sous-dossier sont modifiés.

## Installation
1. Compile le projet ou récupère l'artefact de la GitHub Action.
2. Dépose `NovaAntiCheat.jar` dans `plugins/` sur le serveur Paper 1.21.10.
3. Redémarre le serveur et ajuste `plugins/NovaAntiCheat/config.yml` après des tests.

## Commandes
- `/novaac status` — état du plugin.
- `/novaac reload` — recharge la configuration.
- `/novaac vl <joueur>` — affiche les violations du joueur.
- `/novaac reset <joueur> confirm` — réinitialise ses violations.

## Permissions
- `novaanticheat.admin` — commandes (OP par défaut).
- `novaanticheat.alerts` — alertes staff (OP par défaut).
- `novaanticheat.bypass` — ignore les contrôles (non attribuée par défaut).

## Sanctions automatiques
`punishments.auto-ban` vaut `false` par défaut. Le seuil se règle avec `punishments.violation-threshold`. Activez cette option uniquement après une période de test : un VL n'est pas une preuve absolue de triche.
