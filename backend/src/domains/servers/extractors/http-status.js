export default {
  id: 'http-status',
  name: 'Statut HTTP',
  description: "Vérifie que l'URL répond (appel distant)",
  compatibleTypes: ['http'],

  async fetch(resource) {
    const start = Date.now();
    const res = await fetch(resource.url, { method: 'HEAD' });
    return [
      {
        id: 'status',
        title: 'Statut',
        data: {
          URL: resource.url,
          Statut: res.status,
          'Temps de réponse': `${Date.now() - start} ms`,
        },
      },
    ];
  },
};
