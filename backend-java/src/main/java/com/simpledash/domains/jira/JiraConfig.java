package com.simpledash.domains.jira;

import com.simpledash.domains.Resource;
import com.simpledash.lib.Env;
import java.util.List;
import java.util.Map;

// Jira Server / Data Center (pas Jira Cloud), auth par Personal Access
// Token. Renseigne backend-java/.env (voir .env.example) : JIRA_BASE_URL,
// JIRA_TOKEN.
public final class JiraConfig {

    public static final String BASE_URL = Env.get("JIRA_BASE_URL");
    public static final String TOKEN = Env.get("JIRA_TOKEN");

    private static final boolean CONFIGURED =
        BASE_URL != null && !BASE_URL.isBlank() && TOKEN != null && !TOKEN.isBlank();

    // Une ressource = une requête JQL nommée. maxResults par défaut : 5.
    public static final List<Resource> RESOURCES = CONFIGURED
        ? List.of(
            new Resource(
                "my-issues",
                "Mes tickets en cours",
                List.of("jira"),
                Map.of(
                    "jql", "assignee = currentUser() AND resolution = Unresolved ORDER BY updated DESC",
                    "maxResults", 5
                )
            )
            // Exemple à dupliquer/adapter :
            // new Resource("sprint", "Sprint en cours", List.of("jira"),
            //     Map.of("jql", "project = PROJ AND sprint in openSprints()", "maxResults", 10))
        )
        : List.of();

    private JiraConfig() {}
}
