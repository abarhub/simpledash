// SonarQube self-hébergé, auth par User Token en Basic Auth (token en nom
// d'utilisateur, mot de passe vide — convention Sonar, pas de Bearer).
// Renseigne backend/.env (voir .env.example) : SONAR_BASE_URL, SONAR_TOKEN.
const baseUrl = process.env.SONAR_BASE_URL;
const token = process.env.SONAR_TOKEN;
const configured = Boolean(baseUrl && token);

export default {
  baseUrl,
  token,
  // Une ressource = une clé de projet Sonar.
  resources: configured
    ? [
        // Exemple à dupliquer/adapter :
        // {
        //   id: 'mon-projet',
        //   name: 'Mon projet',
        //   projectKey: 'com.example:mon-projet',
        //   types: ['sonar'],
        // },
      ]
    : [],
  groups: [],
};
