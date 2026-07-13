import config from '../config.js';

function isApprovedByMe(pr) {
  return (pr.reviewers ?? []).some((r) => r.user?.slug === config.username && r.approved);
}

export default {
  id: 'bitbucket-prs',
  name: 'Pull requests',
  description: 'Liste des PR ouvertes du dépôt — un tableau (titre, auteur, à moi, validée, date)',
  compatibleTypes: ['bitbucket'],

  async fetch(resource) {
    const url = new URL(
      `/rest/api/1.0/projects/${resource.project}/repos/${resource.repo}/pull-requests`,
      config.baseUrl
    );
    url.searchParams.set('state', 'OPEN');

    const res = await fetch(url, {
      headers: { Authorization: `Bearer ${config.token}` },
    });
    if (!res.ok) {
      throw new Error(`Erreur Bitbucket: ${res.status}`);
    }
    const json = await res.json();

    const rows = (json.values ?? []).map((pr) => ({
      url: pr.links?.self?.[0]?.href,
      cells: [
        pr.title,
        pr.author?.user?.displayName ?? '?',
        pr.author?.user?.slug === config.username ? 'Oui' : 'Non',
        isApprovedByMe(pr) ? 'Oui' : 'Non',
        pr.createdDate ? new Date(pr.createdDate).toLocaleDateString('fr-FR') : '?',
      ],
    }));

    if (rows.length === 0) {
      return [{ id: 'pull-requests', title: 'Pull requests', data: { 'Pull requests': 'aucune PR ouverte' } }];
    }

    return [
      {
        id: 'pull-requests',
        title: 'Pull requests',
        table: { columns: ['Titre', 'Auteur', 'À moi', 'Validée', 'Date'], rows },
      },
    ];
  },
};
