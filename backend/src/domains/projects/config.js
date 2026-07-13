import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../../../../');

// Répertoires racines scannés par findProjects pour découvrir les projets.
// Par défaut, le repo lui-même (trouve backend/ et frontend/) — adapte vers
// tes vrais dossiers de projets (ex: "D:/projet").
export default {
  scanRoots: [repoRoot],
};
