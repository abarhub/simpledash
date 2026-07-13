import { analyzeProject } from '../../../lib/analyzeProject.js';

export default {
  id: 'npm-version',
  name: 'Version (package.json)',
  description: 'Champ "version" du package.json',
  compatibleTypes: ['npm'],

  async fetch(resource) {
    const { npm } = await analyzeProject(resource.path);
    return [{ id: 'version', title: 'Version', data: { Version: npm?.version ?? 'introuvable' } }];
  },
};
