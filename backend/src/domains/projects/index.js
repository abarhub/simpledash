import path from 'node:path';
import crypto from 'node:crypto';
import config from './config.js';
import { findProjects, PROJECT_MARKERS } from '../../lib/findProjects.js';
import npmVersion from './extractors/npm-version.js';
import npmDependencies from './extractors/npm-dependencies.js';
import pomVersion from './extractors/pom-version.js';
import summary from './extractors/summary.js';
import modules from './extractors/modules.js';

function resourceId(dir) {
  const hash = crypto.createHash('sha1').update(dir).digest('hex').slice(0, 8);
  return `${path.basename(dir)}-${hash}`;
}

// Scanne les racines déclarées en config à chaque appel (pas de cache) :
// simple, et cohérent avec le reste de l'appli qui se rafraîchit à la
// demande plutôt qu'en arrière-plan.
async function listResources() {
  const found = (await Promise.all(config.scanRoots.map((root) => findProjects(root)))).flat();

  const resources = found.map(({ dir, files }) => ({
    id: resourceId(dir),
    name: path.basename(dir),
    types: [...new Set(files.map((file) => PROJECT_MARKERS[file]))],
    path: dir,
  }));

  const groups =
    resources.length > 0
      ? [{ id: 'all-projects', name: 'Tous les projets', resourceIds: resources.map((r) => r.id) }]
      : [];

  return { resources, groups };
}

export default {
  id: 'projects',
  name: 'Projets',
  listResources,
  extractors: [npmVersion, npmDependencies, pomVersion, summary, modules],
};
