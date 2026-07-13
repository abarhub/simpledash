import { analyzeProject } from '../../../lib/analyzeProject.js';

export default {
  id: 'npm-dependencies',
  name: 'Dépendances (package.json)',
  description: 'Liste des dépendances et leur version déclarée',
  compatibleTypes: ['npm'],

  async fetch(resource) {
    const { npm } = await analyzeProject(resource.path);
    const deps = { ...npm?.dependencies, ...npm?.devDependencies };
    return [{ id: 'dependencies', title: 'Dépendances', data: deps }];
  },
};
