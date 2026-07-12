# simpledash

Dashboard modulaire : le front sélectionne des sources d'info, le backend les
traite (appel distant, lecture du FS, etc.) et renvoie une ou plusieurs
"cards" par source.

## Structure

- `backend/` — API Express. Chaque source d'info est un "provider" dans
  `backend/src/providers/` (ex: `datetime.js`, `system.js`, `joke.js`). Un
  provider expose `{ id, name, description, fetch() }`, où `fetch()` retourne
  un tableau de widgets (une source peut donc produire plusieurs cards).
- `frontend/` — React + Vite. Liste les providers, permet de les
  sélectionner, affiche les widgets reçus sous forme de cards avec un bouton
  de rafraîchissement individuel.

## Lancer en local

```bash
cd backend && npm install && npm run dev   # http://localhost:4001
cd frontend && npm install && npm run dev  # http://localhost:5173
```

## Ajouter une nouvelle source d'info

1. Créer un fichier dans `backend/src/providers/`, exportant
   `{ id, name, description, async fetch() }`.
2. L'enregistrer dans `backend/src/providers/index.js`.
3. Rien à faire côté front : la liste de sélection et les cards s'adaptent
   automatiquement.
