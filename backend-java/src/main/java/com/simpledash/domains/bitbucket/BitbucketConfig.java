package com.simpledash.domains.bitbucket;

import com.simpledash.domains.Resource;
import com.simpledash.lib.Env;
import java.util.List;

// Bitbucket Server / Data Center (pas Bitbucket Cloud), auth par Personal
// Access Token. Renseigne backend-java/.env (voir .env.example) :
// BITBUCKET_BASE_URL, BITBUCKET_TOKEN, BITBUCKET_USERNAME (le "slug" du
// compte, pour "à moi"/"validée par moi" — champ requis depuis Bitbucket
// Data Center 8.x).
public final class BitbucketConfig {

    public static final String BASE_URL = Env.get("BITBUCKET_BASE_URL");
    public static final String TOKEN = Env.get("BITBUCKET_TOKEN");
    public static final String USERNAME = Env.get("BITBUCKET_USERNAME");

    private static final boolean CONFIGURED =
        BASE_URL != null && !BASE_URL.isBlank()
            && TOKEN != null && !TOKEN.isBlank()
            && USERNAME != null && !USERNAME.isBlank();

    // Une ressource = un dépôt à surveiller.
    public static final List<Resource> RESOURCES = CONFIGURED
        ? List.of(
            // Exemple à dupliquer/adapter :
            // new Resource("mon-repo", "mon-repo", List.of("bitbucket"),
            //     Map.of("project", "PROJ", "repo", "mon-repo"))
        )
        : List.of();

    private BitbucketConfig() {}
}
