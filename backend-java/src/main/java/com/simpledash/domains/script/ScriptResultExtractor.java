package com.simpledash.domains.script;

import com.simpledash.domains.Extractor;
import com.simpledash.domains.ExtractorWidget;
import com.simpledash.domains.Resource;
import com.simpledash.domains.WidgetRow;
import com.simpledash.domains.WidgetTable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Undefined;

public class ScriptResultExtractor implements Extractor {

    private static final int DEFAULT_TIMEOUT_SECONDS = 10;

    private final List<Path> readFileRoots;

    public ScriptResultExtractor() {
        this(ScriptConfig.READ_FILE_ROOTS);
    }

    ScriptResultExtractor(List<Path> readFileRoots) {
        this.readFileRoots = readFileRoots;
    }

    public String id() {
        return "script-result";
    }

    public String name() {
        return "Résultat du script";
    }

    public String description() {
        return "Exécute le script JavaScript configuré (Rhino) et affiche le résultat qu'il retourne";
    }

    public List<String> compatibleTypes() {
        return List.of("script");
    }

    public List<ExtractorWidget> fetch(Resource resource) throws Exception {
        String inlineScript = (String) resource.get("script");
        String file = (String) resource.get("file");
        boolean hasInline = inlineScript != null && !inlineScript.isBlank();
        boolean hasFile = file != null && !file.isBlank();
        if (hasInline == hasFile) {
            throw new RuntimeException(
                "La ressource doit définir exactement un de 'script' (inline) ou 'file' (chemin), pas les deux ni aucun"
            );
        }

        String source = hasInline ? inlineScript : Files.readString(Path.of(file));
        String sourceName = hasInline ? resource.id() : file;

        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) resource.get("params");
        Integer timeoutSeconds = (Integer) resource.get("timeoutSeconds");
        int timeout = timeoutSeconds != null ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;

        ScriptApi api = new ScriptApi(readFileRoots);

        Object result = runWithTimeout(source, sourceName, api, params == null ? Map.of() : params, timeout);

        return List.of(toWidget(resource, result));
    }

    // Double filet de sécurité contre un script qui ne termine jamais :
    // le comptage d'instructions de ScriptRunner (côté Rhino) attrape une
    // boucle JS pure, ce timeout externe (côté JVM) attrape le cas où le
    // script est bloqué dans un appel Java bloquant comme ScriptApi.fetch,
    // que le comptage d'instructions ne peut pas intercepter.
    private Object runWithTimeout(
        String source, String sourceName, ScriptApi api, Map<String, Object> params, int timeoutSeconds
    ) throws Exception {
        long deadlineNanos = System.nanoTime() + Duration.ofSeconds(timeoutSeconds).toNanos();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Object> future = executor.submit(() -> ScriptRunner.run(source, sourceName, api, params, deadlineNanos));
            try {
                return future.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new RuntimeException("Erreur script: dépassement du délai (" + timeoutSeconds + "s)");
            } catch (ExecutionException e) {
                switch (e.getCause()) {
                    case RuntimeException re -> throw re;
                    case Exception ex -> throw ex;
                    case null, default -> throw e;
                }
            }
        }
    }

    private ExtractorWidget toWidget(Resource resource, Object result) {
        if (!(result instanceof Map<?, ?> resultMap)) {
            throw new RuntimeException("Le script doit retourner un objet {data:...} ou {table:...}");
        }

        Object dataValue = valueOrNull(resultMap.get("data"));
        Object tableValue = valueOrNull(resultMap.get("table"));
        if ((dataValue == null) == (tableValue == null)) {
            throw new RuntimeException("Le script doit retourner exactement un de 'data' ou 'table'");
        }

        Object titleValue = valueOrNull(resultMap.get("title"));
        String title = titleValue != null ? Context.toString(titleValue) : resource.name();
        Object urlValue = valueOrNull(resultMap.get("url"));
        String url = urlValue != null ? Context.toString(urlValue) : null;

        if (dataValue != null) {
            if (!(dataValue instanceof Map<?, ?> dataMap)) {
                throw new RuntimeException("'data' doit être un objet");
            }
            Map<String, String> data = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : dataMap.entrySet()) {
                data.put(String.valueOf(entry.getKey()), Context.toString(entry.getValue()));
            }
            return url != null
                ? ExtractorWidget.data("result", title, url, data)
                : ExtractorWidget.data("result", title, data);
        }

        if (!(tableValue instanceof Map<?, ?> tableMap)) {
            throw new RuntimeException("'table' doit être un objet {columns, rows}");
        }
        WidgetTable table = new WidgetTable(toStringList(tableMap.get("columns")), toRows(tableMap.get("rows")));
        return url != null
            ? ExtractorWidget.table("result", title, url, table)
            : ExtractorWidget.table("result", title, table);
    }

    private static Object valueOrNull(Object value) {
        return (value == null || Undefined.isUndefined(value)) ? null : value;
    }

    private static List<String> toStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            throw new RuntimeException("'columns' et les cellules de 'rows' doivent être des tableaux");
        }
        return list.stream().map(Context::toString).toList();
    }

    private static List<WidgetRow> toRows(Object rowsValue) {
        if (!(rowsValue instanceof List<?> rows)) {
            throw new RuntimeException("'rows' doit être un tableau");
        }
        List<WidgetRow> result = new ArrayList<>();
        for (Object row : rows) {
            result.add(toRow(row));
        }
        return result;
    }

    private static WidgetRow toRow(Object rowValue) {
        if (rowValue instanceof Map<?, ?> rowMap) {
            List<String> cells = toStringList(rowMap.get("cells"));
            Object urlValue = valueOrNull(rowMap.get("url"));
            return urlValue != null ? new WidgetRow(Context.toString(urlValue), cells) : new WidgetRow(cells);
        }
        if (rowValue instanceof List<?>) {
            return new WidgetRow(toStringList(rowValue));
        }
        throw new RuntimeException("Chaque ligne de 'rows' doit être un tableau de cellules ou un objet {cells, url}");
    }
}
