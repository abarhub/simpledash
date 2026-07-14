# simpledash

Dashboard modulaire : le front sélectionne un domaine, des ressources (ou un
groupe) et les infos voulues ; le backend les traite (appel distant, lecture
du FS, etc.) et renvoie une ou plusieurs "cards" par ressource × info.

## Concepts

- **Domaine** — une catégorie de choses interrogeables : `système`,
  `projets`, `serveurs`, `jira`, `bitbucket`, `bamboo`, `sonar`. Chaque
  domaine vit dans `backend/src/domains/<nom>/` et a sa propre config, ses
  ressources et ses extracteurs.
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
  Un extracteur peut retourner plusieurs widgets (ex: mémoire/CPU/uptime).
  Un widget affiche soit `data` (clé/valeur, pour décrire une seule chose :
  une version, un statut), soit `table` (`{ columns, rows }`, pour une liste
  d'éléments similaires : tickets Jira, PR Bitbucket — chaque ligne peut
  avoir son propre `url`). Un widget peut aussi avoir un `url` global (lien
  "Ouvrir ↗" affiché sur la card).

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
cd frontend && npm install && npm run dev  # http://localhost:5173, proxy /api -> :3008
```

## Déployer (front + back sur un seul serveur Node)

Le backend sert le frontend buildé s'il le trouve (`frontend/dist`) — un
seul process, un seul port, pas de CORS en prod. Depuis la racine du repo :

```bash
npm run install:all   # installe backend/ et frontend/
npm run build          # build le frontend dans frontend/dist
npm start               # démarre le backend, qui sert aussi le front
```

Le tout écoute sur `PORT` (défaut `3008`). En dev, le frontend continue de
tourner séparément via `npm run dev` (proxy Vite vers le backend) ; en prod
c'est uniquement le backend qui tourne.

## Domaines Jira / Bitbucket / Bamboo / Sonar (serveurs on-premise)

Ces domaines appellent des instances **Server / Data Center** (pas Cloud).
Copier `backend/.env.example` vers `backend/.env` et renseigner les
URLs/tokens ; un domaine dont les variables ne sont pas renseignées reste
simplement vide (pas d'erreur au démarrage). Les ressources (requêtes JQL
nommées, dépôts Bitbucket, plans Bamboo, projets Sonar) se déclarent à la
main dans le `config.js` de chaque domaine.

Auth : Jira, Bitbucket et Bamboo utilisent un Personal Access Token en
Bearer (`Authorization: Bearer <token>`). **SonarQube fait exception** : le
token s'envoie en Basic Auth (token comme nom d'utilisateur, mot de passe
vide) — pas de Bearer.

Non testé contre de vrais serveurs (pas d'accès réseau depuis l'environnement
de développement) — à vérifier après configuration.

**Certificat TLS interne** : si un de ces serveurs utilise une CA interne
(cas courant en on-premise), les appels `fetch` échoueront tant que Node ne
connaît pas cette CA. La bonne approche est la variable d'environnement
native `NODE_EXTRA_CA_CERTS` (fait confiance à la CA sans désactiver la
vérification TLS globalement) :

```bash
NODE_EXTRA_CA_CERTS=/chemin/vers/ca-interne.pem npm run dev
```

À définir dans l'environnement au lancement (pas dans `backend/.env` : ce
fichier est chargé par Node après le démarrage du module TLS, trop tard
pour cette variable). Éviter `NODE_TLS_REJECT_UNAUTHORIZED=0`, qui désactive
toute vérification de certificat pour le processus entier.

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
