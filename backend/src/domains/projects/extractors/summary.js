import { analyzeProject } from '../../../lib/analyzeProject.js';

function formatList(values) {
  return values && values.length > 0 ? values.join(', ') : null;
}

export default {
  id: 'summary',
  name: 'Résumé',
  description: 'Versions clés détectées (Java, Spring Boot, Angular, Rust, Go), y compris dans les sous-modules',
  compatibleTypes: ['npm', 'maven', 'rust', 'go'],

  async fetch(resource) {
    const { summary } = await analyzeProject(resource.path);

    const data = {
      Java: formatList(summary.javaVersion),
      'Spring Boot': formatList(summary.springBootVersion),
      Angular: formatList(summary.angularVersion),
      Rust: formatList(summary.rustVersion),
      Go: formatList(summary.goVersion),
    };
    for (const key of Object.keys(data)) {
      if (data[key] === null) delete data[key];
    }

    return [
      {
        id: 'summary',
        title: 'Résumé',
        data: Object.keys(data).length > 0 ? data : { Résumé: 'aucune info détectée' },
      },
    ];
  },
};
