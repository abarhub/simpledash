package com.simpledash;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simpledash.domains.DomainRegistry;
import com.simpledash.domains.bamboo.BambooDomain;
import com.simpledash.domains.bitbucket.BitbucketDomain;
import com.simpledash.domains.jira.JiraDomain;
import com.simpledash.domains.projects.ProjectsDomain;
import com.simpledash.domains.script.ScriptDomain;
import com.simpledash.domains.servers.ServersDomain;
import com.simpledash.domains.sonar.SonarDomain;
import com.simpledash.domains.system.SystemDomain;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class Main {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "3008"));

        DomainRegistry registry = new DomainRegistry(List.of(
            new SystemDomain(),
            new ServersDomain(),
            new ProjectsDomain(),
            new JiraDomain(),
            new BitbucketDomain(),
            new BambooDomain(),
            new SonarDomain(),
            new ScriptDomain()
        ));

        // Sert le frontend buildé (npm run build côté frontend) s'il existe, pour
        // pouvoir déployer front + back en un seul process/port. En dev, le
        // frontend tourne séparément (npm run dev + proxy Vite) et ce dossier
        // n'existe pas encore. Suppose un lancement depuis backend-java/, comme
        // ProjectsConfig.
        Path frontendDist = Path.of("..", "frontend", "dist").normalize();
        boolean serveFrontend = Files.isDirectory(frontendDist);

        Javalin app = Javalin.create(config -> {
            if (serveFrontend) {
                config.staticFiles.add(staticFiles -> {
                    staticFiles.directory = frontendDist.toString();
                    staticFiles.location = Location.EXTERNAL;
                });
                config.spaRoot.addFile("/", frontendDist.resolve("index.html").toString(), Location.EXTERNAL);
            }
        });

        // CORS géré à la main plutôt qu'via le plugin Javalin, pour ne pas
        // dépendre d'une API de plugin dont je ne suis pas certain à 100%
        // sans pouvoir compiler ici.
        app.before(ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            ctx.header("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            ctx.header("Access-Control-Allow-Headers", "Content-Type");
        });
        app.options("/*", ctx -> ctx.status(204));

        app.get("/api/domains", ctx -> writeJson(ctx, registry.listDomains()));

        app.get("/api/domains/{domainId}/resources", ctx -> {
            var result = registry.listResources(ctx.pathParam("domainId"));
            if (result == null) {
                writeError(ctx, 404, "Domaine inconnu");
                return;
            }
            writeJson(ctx, result);
        });

        app.get("/api/domains/{domainId}/extractors", ctx -> {
            String raw = ctx.queryParam("resourceIds");
            List<String> resourceIds = (raw == null || raw.isBlank())
                ? List.of()
                : Arrays.asList(raw.split(","));
            var result = registry.listExtractors(ctx.pathParam("domainId"), resourceIds);
            if (result == null) {
                writeError(ctx, 404, "Domaine inconnu");
                return;
            }
            writeJson(ctx, result);
        });

        app.post("/api/data", ctx -> {
            DataRequest body = MAPPER.readValue(ctx.body(), DataRequest.class);
            if (body.domainId() == null || body.resourceIds() == null || body.extractorIds() == null) {
                writeError(ctx, 400, "domainId, resourceIds et extractorIds sont requis");
                return;
            }
            try {
                var widgets = registry.getWidgets(body.domainId(), body.resourceIds(), body.extractorIds());
                writeJson(ctx, Map.of("widgets", widgets));
            } catch (Exception e) {
                writeError(ctx, 400, e.getMessage());
            }
        });

        app.start(port);
        System.out.println("Backend démarré sur http://localhost:" + port);
    }

    private static void writeJson(Context ctx, Object body) throws Exception {
        ctx.contentType("application/json; charset=utf-8");
        ctx.result(MAPPER.writeValueAsString(body));
    }

    private static void writeError(Context ctx, int status, String message) throws Exception {
        ctx.status(status);
        writeJson(ctx, Map.of("error", message));
    }

    record DataRequest(String domainId, List<String> resourceIds, List<String> extractorIds) {}
}
