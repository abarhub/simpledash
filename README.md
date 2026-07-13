# simpledash

Dashboard modulaire : le front sélectionne un domaine, des ressources (ou un
groupe) et les infos voulues ; le backend les traite (appel distant, lecture
du FS, etc.) et renvoie une ou plusieurs "cards" par ressource × info.

## Concepts

- **Domaine** — une catégorie de choses interrogeables : `système`,
  `projets`, `serveurs`, `jira`, `bitbucket`. Chaque domaine vit dans
  `backend/src/domains/<nom>/` et a sa propre config, ses ressources et ses
  extracteurs.
- **Ressource** — une instance d'un domaine (un projet précis, un serveur
  précis, une requête JQL nommée, un dépôt Bitbucket), déclarée dans
  `config.js` avec un ou plusieurs `types` (ex: `npm`, `maven`, `http`,
  `jira`). Les ressources peuvent être regroupées en **groupes**, définis à
  la main dans `config.js` (par `resourceIds`, ou par `paths` pour le
  domaine `projects` dont les ressources sont découvertes dynamiquement).
- **Extracteur** — la logique qui va chercher une info sur une ressource. Il
  déclare `compatibleTypes` : seuls les extracteurs compatibles avec le type
  des ressources sélectionnées sont proposés au front.
- **Widget** — le résultat d'un extracteur pour une ressource (une card).
  Un extracteur peut retourner plusieurs widgets (ex: mémoire/CPU/uptime, ou
  un ticket Jira par widget). Un widget peut avoir un `url` (lien "Ouvrir ↗"
  vers le serveur distant affiché sur la card).

La sélection finale est un produit **ressources × extracteurs compatibles**.

## Structure

- `backend/src/domains/<nom>/config.js` — ressources + groupes (statique)
- `backend/src/domains/<nom>/extractors/*.js` — un fichier par extracteur :
  `{ id, name, description, compatibleTypes, async fetch(resource) }`
- `backend/src/domains/<nom>/index.js` — assemble config + extracteurs
- `backend/src/domains/index.js` — registre central, calcule le produit
  ressources × extracteurs et gère les erreurs par widget
- `frontend/` — React + Vite : onglets de domaines, sélecteur de
  ressources/groupes, sélecteur d'infos (filtré dynamiquement), grille de
  cards avec rafraîchissement individuel.

## Lancer en local

```bash
cd backend && npm install && npm run dev   # http://localhost:3008
cd frontend && npm install && npm run dev  # http://localhost:5173
```

## Domaines Jira / Bitbucket (serveurs on-premise)

Ces deux domaines appellent des instances **Server / Data Center** (pas
Cloud) en Personal Access Token. Copier `backend/.env.example` vers
`backend/.env` et renseigner les URLs/tokens ; un domaine dont les variables
ne sont pas renseignées reste simplement vide (pas d'erreur au démarrage).
Les ressources (requêtes JQL nommées, dépôts Bitbucket) se déclarent à la
main dans `backend/src/domains/jira/config.js` et
`backend/src/domains/bitbucket/config.js`.

Non testé contre de vrais serveurs (pas d'accès réseau depuis l'environnement
de développement) — à vérifier après configuration, notamment côté
certificat TLS si le serveur utilise une CA interne.

## Ajouter une nouvelle info sur un domaine existant

1. Créer un fichier dans `backend/src/domains/<domaine>/extractors/`,
   exportant `{ id, name, description, compatibleTypes, async fetch(resource) }`.
2. L'enregistrer dans `backend/src/domains/<domaine>/index.js`.
3. Rien à faire côté front : les cases à cocher et les cards s'adaptent
   automatiquement selon le type des ressources sélectionnées.

## Ajouter un nouveau domaine

1. Créer `backend/src/domains/<nouveau>/config.js` (ressources + groupes),
   `extractors/*.js`, et `index.js` qui assemble le tout (voir `projects/`
   ou `servers/` comme exemples).
2. L'ajouter au tableau `domains` dans `backend/src/domains/index.js`.
