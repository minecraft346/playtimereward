# PlaytimeRewards

Plugin de temps de jeu avec récompenses à débloquer, façon DonutSMP.
Compatible Aternos (Spigot / Paper, Minecraft 1.16 → 1.21, Java 17+).

## Compiler le .jar

Choisis une méthode :

**A. GitHub Actions (sans rien installer)**
1. Crée un dépôt GitHub et envoie tout ce dossier dedans.
2. Onglet *Actions* → le workflow « Build plugin » se lance tout seul.
3. Télécharge `PlaytimeRewards` dans les *Artifacts* du run.

**B. En local** (Maven + JDK 17 installés)

    mvn package

Le fichier sera dans `target/PlaytimeRewards.jar`.

**C. IntelliJ IDEA** : ouvre le dossier (projet Maven) → Maven → Lifecycle → `package`.

## Installer sur Aternos

1. Sur Aternos, choisis le logiciel **Paper** (ou Spigot).
2. Onglet *Plugins* (ou *Fichiers* → `plugins/`) → envoie `PlaytimeRewards.jar`.
3. Démarre le serveur : `plugins/PlaytimeRewards/config.yml` est créé.

## Commandes

| Commande | Effet |
|---|---|
| `/playtime` (`/pt`) | Voir son temps de jeu |
| `/playtime <joueur>` | Voir le temps d'un joueur |
| `/playtime rewards` | Ouvrir le menu des récompenses |
| `/playtime claim` | Récupérer toutes les récompenses disponibles |
| `/playtime top` | Classement des 10 plus gros temps de jeu |
| `/playtime admin <set\|add\|remove\|reset> <joueur> [temps]` | Modifier un temps (`playtime.admin`) |
| `/playtime reload` | Recharger la config (`playtime.admin`) |

## Shards

Les shards sont une monnaie gagnée automatiquement avec le temps de jeu actif
(même compteur que les récompenses, anti-AFK compris). Dans `config.yml` :

    shards:
      interval: 5m    # toutes les 5 minutes de jeu...
      amount: 1       # ...le joueur gagne 1 shard
      notify: true    # message à chaque gain

| Commande | Effet |
|---|---|
| `/shards` | Voir sa balance |
| `/shards <joueur>` | Voir celle d'un joueur |
| `/shards top` | Top 10 des shards |
| `/shards give\|take\|set <joueur> <nombre>` | Admin (`playtime.admin`) |

Les récompenses peuvent aussi donner des shards avec la commande
`shards give %player% 50`. La balance est visible dans le menu `/playtime rewards`.

## Récompenses

Tout se règle dans `config.yml` : temps requis, nom, icône, description et
**commandes exécutées par la console** (`%player%` = nom du joueur).
Les exemples utilisent `give` (vanilla) et `eco give` (nécessite un plugin
d'économie comme EssentialsX : retire ou adapte ces lignes sinon).

L'anti-AFK (`afk-timeout-seconds`) arrête le compteur d'un joueur immobile.
Avec `auto-claim: true`, les récompenses sont données automatiquement.
