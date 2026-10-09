# NovaAntiCheat — Paper 1.21.10

Première base du plugin anti-triche pour Paper 1.21.10 (Java 21).

## Détections prévues / première version
- Contrôles de déplacement anormal (heuristique configurable).
- Analyse heuristique des blocs rares minés pour signaler un possible X-Ray.
- Alertes au staff et journalisation des détections.
- Système de niveaux de suspicion (VL) conçu pour éviter les sanctions sur un seul événement.

## Important
Aucun anti-cheat ne peut détecter parfaitement toutes les triches. Le X-Ray se détecte indirectement : un ratio de minerais rares élevé est un indice, pas une preuve. Les faux positifs sont possibles (mines naturelles, chance, effets, latence, plugins de gameplay). Cette première version envoie des alertes et ne bannit pas automatiquement par défaut. Teste-la sur un serveur de test avant toute sanction automatique.

## Compilation
Depuis le dossier `NovaAntiCheat`, avec Java 21 et Maven :
```bash
mvn clean package
```
Le JAR sera dans `target/NovaAntiCheat.jar`.

## Installation
1. Copie `target/NovaAntiCheat.jar` dans le dossier `plugins/` du serveur Paper 1.21.10.
2. Redémarre le serveur.
3. Configure `plugins/NovaAntiCheat/config.yml`.
4. Permission staff : `novaanticheat.alerts` (OP par défaut).

Ce projet est ajouté dans un sous-dossier séparé afin de ne pas remplacer les autres plugins du dépôt parent.
