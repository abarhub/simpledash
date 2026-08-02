package com.simpledash.domains.script;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.mozilla.javascript.Scriptable;

// Objet exposé aux scripts sous le nom "api" (voir ScriptRunner). Toute
// méthode qui échoue lève une RuntimeException classique — elle remonte
// telle quelle à travers Rhino (WrappedException extends RhinoException
// extends RuntimeException), sans traitement spécial nécessaire côté
// ScriptResultExtractor.
public final class ScriptApi {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private final List<Path> allowedReadRoots;

    // Un java.util.Map brut, wrappé par LiveConnect en NativeJavaMap,
    // n'expose PAS ses entrées en accès par point/crochet JS (vérifié :
    // api.params.xxx renvoie undefined, seul api.params.get("xxx")
    // fonctionne) — ScriptRunner construit donc un vrai objet JS natif
    // (via Context.newObject) et le pousse ici par ce setter, pour que
    // api.params.xxx marche comme un objet JS normal.
    private Scriptable params;

    ScriptApi(List<Path> allowedReadRoots) {
        this.allowedReadRoots = allowedReadRoots;
    }

    public Scriptable getParams() {
        return params;
    }

    void setParams(Scriptable params) {
        this.params = params;
    }

    public String fetch(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        try {
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("Erreur script fetch: " + response.statusCode());
            }
            return response.body();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Erreur script fetch: " + e.getMessage(), e);
        }
    }

    public String readFile(String path) {
        Path resolved = Path.of(path).toAbsolutePath().normalize();
        boolean allowed = allowedReadRoots != null
            && allowedReadRoots.stream().anyMatch(resolved::startsWith);
        if (!allowed) {
            throw new RuntimeException("Erreur script readFile: chemin hors des répertoires autorisés (" + path + ")");
        }
        try {
            return Files.readString(resolved);
        } catch (IOException e) {
            throw new RuntimeException("Erreur script readFile: " + e.getMessage(), e);
        }
    }
}
