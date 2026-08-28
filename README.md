# ZombieLogin

Plugin Paper pour Minecraft 1.21.10 ajoutant un système `/register` + `/login`.

## Pré-requis

- Paper 1.21.10
- Java 21
- Maven 3.9+

## Compilation

```bash
mvn clean package
```

Le JAR est généré dans `target/ZombieLogin.jar`.

## Installation

1. Compile le projet.
2. Place `target/ZombieLogin.jar` dans le dossier `plugins/` de ton serveur Paper.
3. Redémarre le serveur.
4. À la première connexion : `/register <motdepasse> <motdepasse>`.
5. Aux connexions suivantes : `/login <motdepasse>`.

Tant que le joueur n'est pas authentifié, les commandes autres que `/login` et `/register` sont bloquées et le joueur dispose d'une protection temporaire.

## Sécurité

Les mots de passe sont stockés sous forme de hash SHA-256 avec un sel aléatoire. Pour une production exposée sur Internet, une évolution vers Argon2id ou bcrypt est recommandée.

## Dépôt

https://github.com/ted30600/zombie-mod
