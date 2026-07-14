import config from './config.js';
import buildStatus from './extractors/build-status.js';

export default {
  id: 'bamboo',
  name: 'Bamboo',
  async listResources() {
    return { resources: config.resources, groups: config.groups ?? [] };
  },
  extractors: [buildStatus],
};
