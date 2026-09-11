# UltraLogin

Plugin Paper pour Minecraft 1.21.10 ajoutant un système `/register` + `/login` et un système de lobby.

## Pré-requis

- Paper 1.21.10
- Java 21
- Maven 3.9+

## Fonctionnalités

- `/register <motdepasse> <motdepasse>` pour créer un compte.
- `/login <motdepasse>` pour se connecter.
- `/lobby` pour retourner au lobby.
- Téléportation automatique au lobby à chaque connexion.
- Après `/login` ou `/register`, le joueur reste au lobby.
- `/setlobby` pour définir la position actuelle comme lobby (permission `ultralogin.admin`, OP par défaut).
- Les commandes sont bloquées avant authentification, sauf `/login`, `/register` et `/lobby`.
- Le joueur est en aventure et invulnérable tant qu'il n'est pas authentifié.

## Installation

1. Compile le projet avec `mvn clean package`.
2. Place `target/UltraLogin.jar` dans le dossier `plugins/` de ton serveur Paper.
3. Redémarre le serveur.
4. Connecte-toi en tant qu'OP et place-toi à l'endroit souhaité.
5. Utilise `/setlobby` une fois.
6. Les joueurs seront automatiquement téléportés à cet endroit lorsqu'ils se connectent.

## Sécurité

Les mots de passe sont stockés sous forme de hash SHA-256 avec un sel aléatoire. Pour une production exposée sur Internet, une évolution vers Argon2id ou bcrypt est recommandée.
