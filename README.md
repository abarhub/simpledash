# simpledash

Dashboard modulaire : le front sélectionne un domaine, des ressources (ou un
groupe) et les infos voulues ; le backend les traite (appel distant, lecture
du FS, etc.) et renvoie une ou plusieurs "cards" par ressource × info.

Le backend est en Java + [Javalin](https://javalin.io/) (`backend-java/`) —
voir [backend-java/README.md](backend-java/README.md) pour son
fonctionnement, sa configuration et ses notes de portage en détail.
(Historique : le projet a démarré avec un backend Node/Express, remplacé une
fois le portage Java terminé.)

## Concepts

- **Domaine** — une catégorie de choses interrogeables : `système`,
  `projets`, `serveurs`, `jira`, `bitbucket`, `bamboo`, `sonar`. Chaque
  domaine a sa propre config, ses ressources et ses extracteurs.
- **Ressource** — une instance d'un domaine (un projet précis, un serveur
  précis, une requête JQL nommée, un dépôt Bitbucket), avec un ou plusieurs
  `types` (ex: `npm`, `maven`, `http`, `jira`). Les ressources peuvent être
  regroupées en **groupes** (par id, ou par chemin pour le domaine
  `projects` dont les ressources sont découvertes dynamiquement).
- **Extracteur** — la logique qui va chercher une info sur une ressource. Il
  déclare des types compatibles : seuls les extracteurs compatibles avec le
  type des ressources sélectionnées sont proposés au front.
- **Widget** — le résultat d'un extracteur pour une ressource (une card).
  Un extracteur peut retourner plusieurs widgets (ex: mémoire/CPU/uptime).
  Un widget affiche soit `data` (clé/valeur, pour décrire une seule chose :
  une version, un statut), soit `table` (`{ columns, rows }`, pour une liste
  d'éléments similaires : tickets Jira, PR Bitbucket — chaque ligne peut
  avoir son propre `url`). Un widget peut aussi avoir un `url` global (lien
  "Ouvrir ↗" affiché sur la card).

La sélection finale est un produit **ressources × extracteurs compatibles**.

## Structure

- `backend-java/` — backend Java/Javalin ; voir son
  [README](backend-java/README.md) pour l'architecture domaine/ressource/extracteur
  (`Domain`, `Extractor`, `DomainRegistry`), la configuration (`.env`,
  `projects.yml`) et les notes de portage.
- `frontend/` — React + Vite : onglets de domaines, sélecteur de
  ressources/groupes, sélecteur d'infos (filtré dynamiquement), grille de
  cards avec rafraîchissement individuel.

Le contrat HTTP entre les deux (`/api/domains`, `/api/domains/{id}/resources`,
`/api/domains/{id}/extractors`, `POST /api/data`) est stable ; le frontend
n'a besoin d'aucune modification pour pointer vers un backend qui le
respecte.

## Lancer en local

```bash
cd backend-java && mvn compile exec:java  # http://localhost:3008
cd frontend && npm install && npm run dev # http://localhost:5173, proxy /api -> :3008
```

## Déployer (front + back sur un seul serveur)

Le backend sert le frontend buildé s'il le trouve (`frontend/dist`) — un
seul process, un seul port, pas de CORS en prod.

```bash
cd frontend && npm install && npm run build   # build le frontend dans frontend/dist
cd backend-java && mvn package                # compile le jar
java -jar backend-java/target/simpledash-backend.jar
```

Le tout écoute sur `PORT` (défaut `3008`). En dev, le frontend continue de
tourner séparément via `npm run dev` (proxy Vite vers le backend) ; en prod
c'est uniquement le backend qui tourne. Détails dans la section
["Déploiement mono-serveur" du README du backend](backend-java/README.md#déploiement-mono-serveur).

## Domaines Jira / Bitbucket / Bamboo / Sonar (serveurs on-premise)

Ces domaines appellent des instances **Server / Data Center** (pas Cloud) et
nécessitent des credentials — voir la section
["Credentials (`.env`)" du backend](backend-java/README.md#credentials-env)
pour la configuration, et
["Certificat TLS interne"](backend-java/README.md#certificat-tls-interne)
si l'un de ces serveurs utilise une CA interne (cas courant en on-premise).

Auth : Jira, Bitbucket et Bamboo utilisent un Personal Access Token en
Bearer (`Authorization: Bearer <token>`). **SonarQube fait exception** : le
token s'envoie en Basic Auth (token comme nom d'utilisateur, mot de passe
vide) — pas de Bearer.

Non testé contre de vrais serveurs (pas d'accès réseau depuis
l'environnement de développement) — à vérifier après configuration.

## Ajouter une nouvelle info sur un domaine existant

1. Créer une classe dans `backend-java/src/main/java/com/simpledash/domains/<domaine>/`
   implémentant `Extractor` (`id()`, `name()`, `description()`,
   `compatibleTypes()`, `fetch(Resource)`).
2. L'enregistrer dans la liste `extractors` du `Domain` correspondant.
3. Rien à faire côté front : les cases à cocher et les cards s'adaptent
   automatiquement selon le type des ressources sélectionnées.

## Ajouter un nouveau domaine

1. Créer un package `backend-java/src/main/java/com/simpledash/domains/<nouveau>/`
   avec une classe implémentant `Domain` (`id()`, `name()`,
   `listResources()`, `extractors()`) et ses `Extractor`s (voir `projects/`
   ou `servers/` comme exemples).
2. L'ajouter à la liste passée à `DomainRegistry` dans `Main.java`.
