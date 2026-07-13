import config from './config.js';
import pullRequests from './extractors/pull-requests.js';

export default {
  id: 'bitbucket',
  name: 'Bitbucket',
  async listResources() {
    return { resources: config.resources, groups: config.groups ?? [] };
  },
  extractors: [pullRequests],
};
