// Bamboo Server / Data Center, auth par Personal Access Token.
// Renseigne backend/.env (voir .env.example) : BAMBOO_BASE_URL, BAMBOO_TOKEN.
const baseUrl = process.env.BAMBOO_BASE_URL;
const token = process.env.BAMBOO_TOKEN;
const configured = Boolean(baseUrl && token);

export default {
  baseUrl,
  token,
  // Une ressource = un plan. maxBranches par défaut : 5.
  resources: configured
    ? [
        // Exemple à dupliquer/adapter :
        // {
        //   id: 'mon-plan',
        //   name: 'Mon plan',
        //   planKey: 'PROJ-PLAN',
        //   maxBranches: 5,
        //   types: ['bamboo'],
        // },
      ]
    : [],
  groups: [],
};
