import config from './config.js';
import issues from './extractors/issues.js';

export default {
  id: 'jira',
  name: 'Jira',
  async listResources() {
    return { resources: config.resources, groups: config.groups ?? [] };
  },
  extractors: [issues],
};
