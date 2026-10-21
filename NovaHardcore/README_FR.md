# NovaHardcore — Paper 1.21.11

Plugin Java gratuit pour un monde Hardcore indépendant avec Multiverse-Core.

## Fonctionnalités
- Monde configuré par `world-name` (par défaut `hardcore`)
- Élimination persistante après une mort dans le monde Hardcore
- Joueur éliminé renvoyé au monde de secours, sans être banni du SMP
- Annonce globale des éliminations
- Classement des participants survivants
- Bordure du monde configurable
- Réinitialisation du monde et du classement avec confirmation

## Compilation automatique
Chaque modification du dossier `NovaHardcore/` déclenche GitHub Actions. Ouvre l'onglet **Actions**, sélectionne le dernier workflow **Build NovaHardcore**, puis télécharge l'artefact `NovaHardcore-1.0.0` une fois la compilation terminée.

## Installation
1. Fais une sauvegarde du serveur.
2. Vérifie que Paper 1.21.11 et Multiverse-Core sont installés.
3. Dépose `NovaHardcore-1.0.0.jar` dans `plugins/` et redémarre.
4. Dans la console, crée le monde si nécessaire : `mv create hardcore NORMAL`.
5. Règle la difficulté : `mv modify set difficulty hard hardcore`.
6. Les joueurs utilisent `/hardcore join`.

## Commandes
- `/hardcore join`
- `/hardcore leave`
- `/hardcore classement`
- `/hardcore status`
- `/hardcore setborder <diamètre>` (admin)
- `/hardcore reset` puis `/hardcore reset confirm` (admin)

Permission d'administration : `novahardcore.admin` (op par défaut).  
Permission de jeu : `novahardcore.play` (accordée par défaut).

## Important
Le workflow doit réussir avant de télécharger l'artefact. Fais une sauvegarde avant la commande de reset : elle supprime le dossier du monde Hardcore pour le recréer. Teste d'abord sur une copie du serveur.
