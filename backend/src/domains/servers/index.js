import config from './config.js';
import httpStatus from './extractors/http-status.js';
import serverInfo from './extractors/server-info.js';

export default {
  id: 'servers',
  name: 'Serveurs',
  async listResources() {
    return { resources: config.resources, groups: config.groups };
  },
  extractors: [httpStatus, serverInfo],
};
