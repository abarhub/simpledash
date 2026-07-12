export default {
  id: 'joke',
  name: 'Blague (appel distant)',
  description: 'Récupère une blague via une API publique (démo d\'appel réseau)',

  async fetch() {
    const res = await fetch('https://icanhazdadjoke.com/', {
      headers: { Accept: 'application/json' },
    });
    if (!res.ok) {
      throw new Error(`Erreur API: ${res.status}`);
    }
    const json = await res.json();
    return [
      {
        id: 'joke-random',
        title: 'Blague du moment',
        data: { Blague: json.joke },
      },
    ];
  },
};
