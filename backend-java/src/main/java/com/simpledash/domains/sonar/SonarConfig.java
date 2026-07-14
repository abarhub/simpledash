package com.simpledash.domains.sonar;

import com.simpledash.domains.Resource;
import com.simpledash.lib.Env;
import java.util.List;

// SonarQube self-hébergé, auth par User Token en Basic Auth (token en nom
// d'utilisateur, mot de passe vide — convention Sonar, pas de Bearer).
// Renseigne backend-java/.env (voir .env.example) : SONAR_BASE_URL,
// SONAR_TOKEN.
public final class SonarConfig {

    public static final String BASE_URL = Env.get("SONAR_BASE_URL");
    public static final String TOKEN = Env.get("SONAR_TOKEN");

    private static final boolean CONFIGURED =
        BASE_URL != null && !BASE_URL.isBlank() && TOKEN != null && !TOKEN.isBlank();

    // Une ressource = une clé de projet Sonar.
    public static final List<Resource> RESOURCES = CONFIGURED
        ? List.of(
            // Exemple à dupliquer/adapter :
            // new Resource("mon-projet", "Mon projet", List.of("sonar"),
            //     Map.of("projectKey", "com.example:mon-projet"))
        )
        : List.of();

    private SonarConfig() {}
}
