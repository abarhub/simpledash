import config from '../config.js';

export default {
  id: 'jira-issues',
  name: 'Tickets',
  description: 'Résultat de la requête JQL (ID, statut, titre) — un widget par ticket',
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

    return (json.issues ?? []).map((issue) => ({
      id: issue.key,
      title: issue.fields?.summary ?? issue.key,
      url: new URL(`/browse/${issue.key}`, config.baseUrl).toString(),
      data: {
        ID: issue.key,
        Statut: issue.fields?.status?.name ?? '?',
        'Mise à jour': issue.fields?.updated
          ? new Date(issue.fields.updated).toLocaleDateString('fr-FR')
          : '?',
      },
    }));
  },
};
