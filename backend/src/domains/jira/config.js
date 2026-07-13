// Jira Server / Data Center (pas Jira Cloud), auth par Personal Access Token.
// Renseigne backend/.env (voir .env.example) : JIRA_BASE_URL, JIRA_TOKEN.
const baseUrl = process.env.JIRA_BASE_URL;
const token = process.env.JIRA_TOKEN;
const configured = Boolean(baseUrl && token);

export default {
  baseUrl,
  token,
  // Une ressource = une requête JQL nommée. maxResults par défaut : 5.
  resources: configured
    ? [
        {
          id: 'my-issues',
          name: 'Mes tickets en cours',
          jql: 'assignee = currentUser() AND resolution = Unresolved ORDER BY updated DESC',
          maxResults: 5,
          types: ['jira'],
        },
        // Exemple à dupliquer/adapter :
        // {
        //   id: 'sprint',
        //   name: 'Sprint en cours',
        //   jql: 'project = PROJ AND sprint in openSprints()',
        //   maxResults: 10,
        //   types: ['jira'],
        // },
      ]
    : [],
  groups: [],
};
