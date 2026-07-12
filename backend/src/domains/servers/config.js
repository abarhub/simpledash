export default {
  resources: [
    { id: 'srv-web-1', name: 'Serveur Web 1', type: 'http', url: 'https://example.com' },
    { id: 'srv-web-2', name: 'Serveur Web 2', type: 'http', url: 'https://example.org' },
    { id: 'srv-db-1', name: 'Serveur DB', type: 'ssh', host: 'db.internal.local' },
  ],
  groups: [
    {
      id: 'all-servers',
      name: 'Tous les serveurs',
      resourceIds: ['srv-web-1', 'srv-web-2', 'srv-db-1'],
    },
  ],
};
