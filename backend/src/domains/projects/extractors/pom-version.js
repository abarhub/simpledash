import { analyzeProject } from '../../../lib/analyzeProject.js';

export default {
  id: 'pom-version',
  name: 'Version (pom.xml)',
  description: 'Balise <version> du pom.xml (projet Maven)',
  compatibleTypes: ['maven'],

  async fetch(resource) {
    const { pom } = await analyzeProject(resource.path);
    return [{ id: 'version', title: 'Version', data: { Version: pom?.version ?? 'introuvable' } }];
  },
};
