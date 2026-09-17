# Virtual NAS

Vrai serveur de stockage prive accessible depuis un navigateur sur PC, Android ou iPhone.

## Fonctions

- compte administrateur cree au premier demarrage
- comptes utilisateurs et administrateurs
- espace de stockage prive pour chaque compte
- trois gestionnaires de fichiers independants par utilisateur
- ajout de plusieurs fichiers, photos et videos
- apercu des images et videos
- telechargement et suppression
- recherche de fichiers
- les fichiers restent sur le serveur et ne sont pas commites dans Git

## Installation sur le serveur

```bash
cd nas
npm install
npm start
```

Puis ouvrir `http://ADRESSE_DU_SERVEUR:3000` depuis un PC ou un telephone connecte au reseau.

Au premier lancement, l'interface demande de creer le compte administrateur. Les comptes et les fichiers sont ensuite conserves dans `nas/data/`.

## Acces depuis Internet

GitHub heberge le code, mais GitHub Pages ne peut pas heberger ce serveur de fichiers. Pour un vrai NAS distant, lance ce dossier sur un PC/NAS/VPS avec Node.js, puis utilise HTTPS et un nom de domaine ou un VPN. Ne rends jamais `nas/data/` public.

## Securite

Les mots de passe sont hashes avec `scrypt` et un sel aleatoire. La session utilise un cookie HTTP-only. Pour une exposition Internet, configure `SESSION_SECRET`, HTTPS, une limite de stockage et une sauvegarde du dossier `data/`. Pour un deploiement important, remplace aussi le stockage JSON et le MemoryStore de session par une vraie base de donnees et un store de sessions persistant.
