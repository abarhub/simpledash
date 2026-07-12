import fs from 'node:fs/promises';
import path from 'node:path';

export default {
  id: 'pom-version',
  name: 'Version (pom.xml)',
  description: 'Balise <version> du pom.xml (projet Maven)',
  compatibleTypes: ['maven'],

  async fetch(resource) {
    const raw = await fs.readFile(path.join(resource.path, 'pom.xml'), 'utf-8');
    const match = raw.match(/<version>([^<]+)<\/version>/);
    return [{ id: 'version', title: 'Version', data: { Version: match ? match[1] : 'introuvable' } }];
  },
};
