# RealSolarEconomy

Plugin Paper **1.21.5** / Java 21 pour une économie Diamant + Netherite.

## Bedrock
Le plugin utilise uniquement l'API Paper côté serveur. Les joueurs Bedrock peuvent donc l'utiliser via **Geyser + Floodgate**. Aucune dépendance Java/Bedrock n'est embarquée dans le plugin.

## Commandes
- `/shop`
- `/bank`
- `/trade <joueur>`
- `/trade accept|deny|cancel`
- `/rich [diamond|netherite]`
- `/economy admin`
- `/economy balance <joueur>`
- `/economy give <joueur> <currency> <montant>`
- `/economy take <joueur> <currency> <montant>`
- `/economy set <joueur> <currency> <montant>`
- `/economy reload`

Aucune commande `/pay` n'est fournie : le paiement passe par l'interface `/bank` puis une saisie de confirmation dans le chat.

## Permissions
| Permission | Usage | Défaut |
|---|---|---|
| `realsolareconomy.shop` | `/shop` | true |
| `realsolareconomy.bank` | `/bank`, `/rich` | true |
| `realsolareconomy.trade` | `/trade` | true |
| `realsolareconomy.admin` | `/economy admin` | op |
| `realsolareconomy.admin.balance` | `/economy balance` | op |
| `realsolareconomy.admin.modify` | `give/take/set` | op |
| `realsolareconomy.admin.reload` | `/economy reload` | op |

Les permissions sont compatibles avec LuckPerms.

## Compilation
```bash
mvn clean package
```
Le résultat est `target/RealSolarEconomy.jar`. GitHub Actions construit également le JAR comme artifact à chaque push sur la branche du plugin.

## Données
Les comptes et transactions sont stockés dans `plugins/RealSolarEconomy/economy.db` (SQLite, mode WAL). Les opérations administratives possèdent un UUID de transaction dans la table `transactions`.

## Important
Cette première version constitue une base fonctionnelle. Le système `/trade` ouvre l'interface et gère les demandes, mais le transfert sécurisé des inventaires avec double-confirmation doit encore être finalisé avant une utilisation en production économique.