import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../../../../');

export default {
  resources: [
    {
      id: 'proj-backend',
      name: 'simpledash-backend',
      type: 'npm',
      path: path.join(repoRoot, 'backend'),
    },
    {
      id: 'proj-frontend',
      name: 'simpledash-frontend',
      type: 'npm',
      path: path.join(repoRoot, 'frontend'),
    },
    {
      // Exemple pour montrer le type "maven" ; adapte le chemin vers un vrai
      // projet Maven pour le tester (sinon la card affichera une erreur
      // "fichier introuvable", ce qui reste un bon exemple de gestion d'erreur).
      id: 'proj-example-maven',
      name: 'Exemple Maven (à adapter)',
      type: 'maven',
      path: path.join(repoRoot, 'exemples', 'maven-demo'),
    },
  ],
  groups: [
    {
      id: 'all-projects',
      name: 'Tous les projets',
      resourceIds: ['proj-backend', 'proj-frontend', 'proj-example-maven'],
    },
    {
      id: 'node-projects',
      name: 'Projets Node',
      resourceIds: ['proj-backend', 'proj-frontend'],
    },
  ],
};
