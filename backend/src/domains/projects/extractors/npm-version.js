import fs from 'node:fs/promises';
import path from 'node:path';

export default {
  id: 'npm-version',
  name: 'Version (package.json)',
  description: 'Champ "version" du package.json',
  compatibleTypes: ['npm'],

  async fetch(resource) {
    const raw = await fs.readFile(path.join(resource.path, 'package.json'), 'utf-8');
    const pkg = JSON.parse(raw);
    return [{ id: 'version', title: 'Version', data: { Version: pkg.version } }];
  },
};
