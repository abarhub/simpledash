import config from '../config.js';

const RATING_LETTERS = { 1: 'A', 2: 'B', 3: 'C', 4: 'D', 5: 'E' };

function authHeaders() {
  // Convention Sonar : token en nom d'utilisateur, mot de passe vide.
  const encoded = Buffer.from(`${config.token}:`).toString('base64');
  return { Authorization: `Basic ${encoded}` };
}

function ratingLetter(value) {
  if (!value) return '?';
  return RATING_LETTERS[Math.trunc(Number(value))] ?? '?';
}

export default {
  id: 'sonar-quality',
  name: 'Qualité',
  description: 'Notes (fiabilité/sécurité/maintenabilité), quality gate, couverture, duplication',
  compatibleTypes: ['sonar'],

  async fetch(resource) {
    const measuresUrl = new URL('/api/measures/component', config.baseUrl);
    measuresUrl.searchParams.set('component', resource.projectKey);
    measuresUrl.searchParams.set(
      'metricKeys',
      'reliability_rating,security_rating,sqale_rating,coverage,duplicated_lines_density,alert_status'
    );

    const analysesUrl = new URL('/api/project_analyses/search', config.baseUrl);
    analysesUrl.searchParams.set('project', resource.projectKey);
    analysesUrl.searchParams.set('ps', '1');

    const [measuresRes, analysesRes] = await Promise.all([
      fetch(measuresUrl, { headers: authHeaders() }),
      fetch(analysesUrl, { headers: authHeaders() }),
    ]);
    if (!measuresRes.ok) {
      throw new Error(`Erreur SonarQube: ${measuresRes.status}`);
    }
    if (!analysesRes.ok) {
      throw new Error(`Erreur SonarQube: ${analysesRes.status}`);
    }

    const measuresJson = await measuresRes.json();
    const analysesJson = await analysesRes.json();

    const metric = (key) => measuresJson.component?.measures?.find((m) => m.metric === key)?.value;
    const analysis = analysesJson.analyses?.[0];

    return [
      {
        id: 'quality',
        title: 'Qualité',
        url: new URL(`/dashboard?id=${resource.projectKey}`, config.baseUrl).toString(),
        data: {
          Fiabilité: ratingLetter(metric('reliability_rating')),
          Sécurité: ratingLetter(metric('security_rating')),
          Maintenabilité: ratingLetter(metric('sqale_rating')),
          'Quality Gate': metric('alert_status') ?? '?',
          Couverture: metric('coverage') ? `${metric('coverage')}%` : '?',
          Duplication: metric('duplicated_lines_density') ? `${metric('duplicated_lines_density')}%` : '?',
          Commit: analysis?.revision ? analysis.revision.slice(0, 8) : '?',
          Date: analysis?.date ? new Date(analysis.date).toLocaleString('fr-FR') : '?',
        },
      },
    ];
  },
};
