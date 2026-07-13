// Bitbucket Server / Data Center (pas Bitbucket Cloud), auth par Personal
// Access Token. Renseigne backend/.env (voir .env.example) : BITBUCKET_BASE_URL,
// BITBUCKET_TOKEN, BITBUCKET_USERNAME (ton identifiant, pour "à moi"/"validée
// par moi").
const baseUrl = process.env.BITBUCKET_BASE_URL;
const token = process.env.BITBUCKET_TOKEN;
const username = process.env.BITBUCKET_USERNAME;
const configured = Boolean(baseUrl && token && username);

export default {
  baseUrl,
  token,
  username,
  // Une ressource = un dépôt à surveiller.
  resources: configured
    ? [
        // Exemple à dupliquer/adapter :
        // {
        //   id: 'mon-repo',
        //   name: 'mon-repo',
        //   project: 'PROJ',
        //   repo: 'mon-repo',
        //   types: ['bitbucket'],
        // },
      ]
    : [],
  groups: [],
};
