export default {
  resources: [
    { id: 'srv-web-1', name: 'Serveur Web 1', types: ['http'], url: 'https://example.com' },
    { id: 'srv-web-2', name: 'Serveur Web 2', types: ['http'], url: 'https://example.org' },
    { id: 'srv-db-1', name: 'Serveur DB', types: ['ssh'], host: 'db.internal.local' },
  ],
  groups: [
    {
      id: 'all-servers',
      name: 'Tous les serveurs',
      resourceIds: ['srv-web-1', 'srv-web-2', 'srv-db-1'],
    },
    {
      id: 'windows-servers',
      name: 'Serveurs Windows',
      resourceIds: ['srv-web-1'],
    },
    {
      id: 'linux-servers',
      name: 'Serveurs Linux',
      resourceIds: ['srv-web-2', 'srv-db-1'],
    },
  ],
};
