import config from './config.js';
import datetime from './extractors/datetime.js';
import systemInfo from './extractors/system-info.js';
import joke from './extractors/joke.js';

export default {
  id: 'system',
  name: 'Système',
  async listResources() {
    return { resources: config.resources, groups: config.groups };
  },
  extractors: [datetime, systemInfo, joke],
};
