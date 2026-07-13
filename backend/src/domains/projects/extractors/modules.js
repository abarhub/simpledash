import path from 'node:path';
import { analyzeProject } from '../../../lib/analyzeProject.js';

function flattenModules(project) {
  return project.modules.flatMap((m) => [m, ...flattenModules(m)]);
}

export default {
  id: 'modules',
  name: 'Modules',
  description: 'Un widget par sous-module détecté (Maven, npm workspaces, Cargo, Go)',
  compatibleTypes: ['npm', 'maven', 'rust', 'go'],

  async fetch(resource) {
    const project = await analyzeProject(resource.path);
    const modules = flattenModules(project);

    if (modules.length === 0) {
      return [{ id: 'none', title: 'Modules', data: { Modules: 'aucun sous-module détecté' } }];
    }

    return modules.map((m, index) => ({
      id: `module-${index}`,
      title: m.pom?.artifactId ?? m.npm?.name ?? m.rust?.name ?? m.go?.module ?? path.basename(m.dir),
      data: {
        Version: m.pom?.version ?? m.npm?.version ?? m.rust?.version ?? m.go?.goVersion ?? '?',
        Chemin: m.dir,
      },
    }));
  },
};
