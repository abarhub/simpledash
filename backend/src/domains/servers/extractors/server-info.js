export default {
  id: 'server-info',
  name: 'Infos configurées',
  description: 'Métadonnées déclarées dans la config (sans appel réseau)',
  compatibleTypes: ['http', 'ssh'],

  async fetch(resource) {
    return [
      {
        id: 'info',
        title: 'Infos',
        data: {
          Type: resource.type,
          ...(resource.url ? { URL: resource.url } : {}),
          ...(resource.host ? { Host: resource.host } : {}),
        },
      },
    ];
  },
};
