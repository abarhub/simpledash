import config from './config.js';
import npmVersion from './extractors/npm-version.js';
import npmDependencies from './extractors/npm-dependencies.js';
import pomVersion from './extractors/pom-version.js';

export default {
  id: 'projects',
  name: 'Projets',
  resources: config.resources,
  groups: config.groups,
  extractors: [npmVersion, npmDependencies, pomVersion],
};
