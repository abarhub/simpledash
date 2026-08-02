package com.simpledash.domains.script;

import java.util.Map;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

// Mécanique Rhino pure : évalue la source, trouve la fonction run(api)
// définie par le script et l'appelle. Une ContextFactory dédiée par appel
// (jamais ContextFactory.initGlobal, qui serait globale au process et
// polluerait les exécutions concurrentes) porte la protection contre les
// boucles infinies par comptage d'instructions — un filet de sécurité
// complémentaire (pas un remplacement) au timeout externe posé par
// ScriptResultExtractor, qui lui seul peut interrompre un script bloqué
// dans un appel Java bloquant comme ScriptApi.fetch().
final class ScriptRunner {

    private ScriptRunner() {}

    static Object run(String source, String sourceName, ScriptApi api, Map<String, Object> params, long deadlineNanos) {
        DeadlineContextFactory factory = new DeadlineContextFactory(deadlineNanos);
        try (Context cx = factory.enterContext()) {
            // Mode interprété : nécessaire pour que l'observateur
            // d'instructions se déclenche de façon fiable (il ne l'est
            // pas garanti en mode compilé). setOptimizationLevel(-1) est
            // dépréciée depuis Rhino 1.8.0 au profit de cette méthode.
            cx.setInterpretedMode(true);
            cx.setInstructionObserverThreshold(10_000);

            // Par défaut, Rhino wrappe les String/Number/Boolean renvoyés
            // par un appel Java comme de vrais objets Java (accès aux
            // méthodes Java type body.length()) plutôt que comme des
            // primitives JS natives (typeof, body.length) — vérifié : sans
            // ce réglage, api.fetch(...) renvoyait un "object" dont
            // .length était la méthode Java length(), pas la longueur.
            cx.getWrapFactory().setJavaPrimitiveWrap(false);

            ScriptableObject scope = cx.initStandardObjects();

            // Un java.util.Map brut wrappé par LiveConnect n'expose pas
            // ses entrées en accès par point/crochet JS (api.params.xxx
            // renvoie undefined) — on construit donc un vrai objet JS.
            Scriptable paramsJs = cx.newObject(scope);
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                ScriptableObject.putProperty(paramsJs, entry.getKey(), entry.getValue());
            }
            api.setParams(paramsJs);

            Scriptable apiJs = (Scriptable) Context.javaToJS(api, scope);
            ScriptableObject.putProperty(scope, "api", apiJs);

            cx.evaluateString(scope, source, sourceName, 1, null);

            Object runFn = scope.get("run", scope);
            if (!(runFn instanceof Function function)) {
                throw new RuntimeException("Le script doit définir une fonction run(api)");
            }

            return function.call(cx, scope, scope, new Object[]{apiJs});
        }
    }

    private static final class DeadlineContextFactory extends ContextFactory {
        private final long deadlineNanos;

        DeadlineContextFactory(long deadlineNanos) {
            this.deadlineNanos = deadlineNanos;
        }

        @Override
        protected void observeInstructionCount(Context cx, int instructionCount) {
            if (System.nanoTime() > deadlineNanos) {
                throw new RuntimeException("Erreur script: dépassement du délai imparti");
            }
        }
    }
}
