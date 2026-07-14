import config from './config.js';
import quality from './extractors/quality.js';

export default {
  id: 'sonar',
  name: 'SonarQube',
  async listResources() {
    return { resources: config.resources, groups: config.groups ?? [] };
  },
  extractors: [quality],
};
