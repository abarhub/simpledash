import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../../../../');

// Répertoires racines scannés par findProjects pour découvrir les projets.
// Par défaut, le repo lui-même — adapte vers tes vrais dossiers de projets
// (ex: "D:/projet"). Le repo racine a son propre package.json (déploiement
// single-server), donc le scan s'y arrête sans descendre plus loin ; on
// ajoute backend/ et frontend/ en scanRoots explicites pour les retrouver
// comme ressources séparées (utile pour la démo des groupes ci-dessous).
//
// Les groupes se définissent ici à la main, par chemin (pas par id, qui est
// généré dynamiquement) : chaque chemin listé dans "paths" doit correspondre
// à un répertoire trouvé par le scan pour apparaître dans le groupe. Un
// groupe "Tous les projets" est toujours ajouté automatiquement en plus.
export default {
  scanRoots: [repoRoot, path.join(repoRoot, 'backend'), path.join(repoRoot, 'frontend')],
  groups: [
    {
      id: 'node-projects',
      name: 'Projets Node',
      paths: [path.join(repoRoot, 'backend'), path.join(repoRoot, 'frontend')],
    },
    // Exemples à dupliquer/adapter vers tes vrais projets :
    // {
    //   id: 'angular-projects',
    //   name: 'Projets Angular',
    //   paths: ['D:/projet/mon-app-angular'],
    // },
    // {
    //   id: 'autres-projets',
    //   name: 'Autres projets',
    //   paths: ['D:/projet/mon-autre-projet'],
    // },
  ],
};
