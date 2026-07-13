import config from '../config.js';

export default {
  id: 'jira-issues',
  name: 'Tickets',
  description: 'Résultat de la requête JQL — un tableau (titre, ID, statut, mise à jour)',
  compatibleTypes: ['jira'],

  async fetch(resource) {
    const maxResults = resource.maxResults ?? 5;
    const url = new URL('/rest/api/2/search', config.baseUrl);
    url.searchParams.set('jql', resource.jql);
    url.searchParams.set('maxResults', String(maxResults));
    url.searchParams.set('fields', 'summary,status,updated');

    const res = await fetch(url, {
      headers: { Authorization: `Bearer ${config.token}` },
    });
    if (!res.ok) {
      throw new Error(`Erreur Jira: ${res.status}`);
    }
    const json = await res.json();

    const rows = (json.issues ?? []).map((issue) => ({
      url: new URL(`/browse/${issue.key}`, config.baseUrl).toString(),
      cells: [
        issue.fields?.summary ?? issue.key,
        issue.key,
        issue.fields?.status?.name ?? '?',
        issue.fields?.updated ? new Date(issue.fields.updated).toLocaleDateString('fr-FR') : '?',
      ],
    }));

    if (rows.length === 0) {
      return [{ id: 'issues', title: 'Tickets', data: { Tickets: 'aucun ticket trouvé' } }];
    }

    return [
      {
        id: 'issues',
        title: 'Tickets',
        table: { columns: ['Titre', 'ID', 'Statut', 'Mise à jour'], rows },
      },
    ];
  },
};
