export default {
  id: 'datetime',
  name: 'Date et heure',
  description: "Date et heure actuelles du serveur",
  compatibleTypes: ['local'],

  async fetch() {
    const now = new Date();
    return [
      {
        id: 'now',
        title: 'Date et heure',
        data: {
          Date: now.toLocaleDateString('fr-FR'),
          Heure: now.toLocaleTimeString('fr-FR'),
        },
      },
    ];
  },
};
