import config from '../config.js';

function isApprovedByMe(pr) {
  return (pr.reviewers ?? []).some((r) => r.user?.name === config.username && r.approved);
}

export default {
  id: 'bitbucket-prs',
  name: 'Pull requests',
  description: 'Liste des PR ouvertes du dépôt — un widget par PR',
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

    return (json.values ?? []).map((pr) => ({
      id: String(pr.id),
      title: pr.title,
      url: pr.links?.self?.[0]?.href,
      data: {
        Auteur: pr.author?.user?.displayName ?? '?',
        'À moi': pr.author?.user?.name === config.username ? 'Oui' : 'Non',
        'Validée par moi': isApprovedByMe(pr) ? 'Oui' : 'Non',
        Date: pr.createdDate ? new Date(pr.createdDate).toLocaleDateString('fr-FR') : '?',
      },
    }));
  },
};
