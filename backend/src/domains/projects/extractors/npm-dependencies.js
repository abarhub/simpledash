import fs from 'node:fs/promises';
import path from 'node:path';

export default {
  id: 'npm-dependencies',
  name: 'Dépendances (package.json)',
  description: 'Liste des dépendances et leur version déclarée',
  compatibleTypes: ['npm'],

  async fetch(resource) {
    const raw = await fs.readFile(path.join(resource.path, 'package.json'), 'utf-8');
    const pkg = JSON.parse(raw);
    const deps = { ...pkg.dependencies, ...pkg.devDependencies };
    return [{ id: 'dependencies', title: 'Dépendances', data: deps }];
  },
};
