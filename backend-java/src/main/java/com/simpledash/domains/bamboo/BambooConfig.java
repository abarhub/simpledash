package com.simpledash.domains.bamboo;

import com.simpledash.domains.Resource;
import com.simpledash.lib.Env;
import java.util.List;

// Bamboo Server / Data Center, auth par Personal Access Token.
// Renseigne backend-java/.env (voir .env.example) : BAMBOO_BASE_URL,
// BAMBOO_TOKEN.
public final class BambooConfig {

    public static final String BASE_URL = Env.get("BAMBOO_BASE_URL");
    public static final String TOKEN = Env.get("BAMBOO_TOKEN");

    private static final boolean CONFIGURED =
        BASE_URL != null && !BASE_URL.isBlank() && TOKEN != null && !TOKEN.isBlank();

    // Une ressource = un plan. maxBranches par défaut : 5.
    public static final List<Resource> RESOURCES = CONFIGURED
        ? List.of(
            // Exemple à dupliquer/adapter :
            // new Resource("mon-plan", "Mon plan", List.of("bamboo"),
            //     Map.of("planKey", "PROJ-PLAN", "maxBranches", 5))
        )
        : List.of();

    private BambooConfig() {}
}
