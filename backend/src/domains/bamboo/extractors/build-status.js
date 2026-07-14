import config from '../config.js';

const STATE_LABELS = { Successful: 'OK', Failed: 'Erreur' };

function authHeaders() {
  return { Authorization: `Bearer ${config.token}` };
}

async function fetchLatestResult(planKey) {
  const url = new URL(`/rest/api/latest/result/${planKey}.json`, config.baseUrl);
  url.searchParams.set('max-results', '1');
  const res = await fetch(url, { headers: authHeaders() });
  if (!res.ok) {
    throw new Error(`Erreur Bamboo: ${res.status}`);
  }
  const json = await res.json();
  return json.results?.result?.[0] ?? null;
}

async function fetchBranches(planKey, maxBranches) {
  const url = new URL(`/rest/api/latest/plan/${planKey}.json`, config.baseUrl);
  url.searchParams.set('expand', 'branches');
  const res = await fetch(url, { headers: authHeaders() });
  if (!res.ok) {
    throw new Error(`Erreur Bamboo: ${res.status}`);
  }
  const json = await res.json();
  return (json.branches?.branch ?? []).slice(0, maxBranches);
}

function buildRow(name, planKey, result) {
  return {
    url: new URL(`/browse/${planKey}`, config.baseUrl).toString(),
    cells: [
      name,
      result?.lifeCycleState ?? '?',
      result ? (STATE_LABELS[result.state] ?? result.state) : '?',
      result?.buildStartedTime ? new Date(result.buildStartedTime).toLocaleString('fr-FR') : '?',
      result?.vcsRevisionKey ? result.vcsRevisionKey.slice(0, 8) : '?',
    ],
  };
}

export default {
  id: 'bamboo-build-status',
  name: 'Builds',
  description: 'Dernier build du plan et de ses branches (limité) — statut, résultat, date, commit',
  compatibleTypes: ['bamboo'],

  async fetch(resource) {
    const maxBranches = resource.maxBranches ?? 5;
    const [mainResult, branches] = await Promise.all([
      fetchLatestResult(resource.planKey),
      fetchBranches(resource.planKey, maxBranches),
    ]);

    const branchResults = await Promise.all(branches.map((branch) => fetchLatestResult(branch.key)));

    const rows = [
      buildRow(resource.name, resource.planKey, mainResult),
      ...branches.map((branch, i) => buildRow(branch.shortName, branch.key, branchResults[i])),
    ];

    return [
      {
        id: 'builds',
        title: 'Builds',
        table: { columns: ['Branche', 'Statut', 'Résultat', 'Date', 'Commit'], rows },
      },
    ];
  },
};
