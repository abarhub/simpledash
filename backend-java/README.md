# simpledash-backend (Java / Javalin)

Portage du backend Node (`../backend`) vers Java + [Javalin](https://javalin.io/)
(fine couche sur Jetty), motivé par le démarrage rapide et la faible
empreinte mémoire par rapport à un framework plus lourd type Spring Boot.

**Statut : les 7 domaines du backend Node sont portés** (`système`,
`serveurs`, `projects`, `jira`, `bitbucket`, `bamboo`, `sonar`),
`projects` avec pom.xml/package.json/Cargo.toml/go.mod/go.work + un
extracteur Git (dernier commit, branche, statut, avance/retard sur le
remote, via `ProcessBuilder`). La gestion des credentials (`.env`) est en
place pour les 4 domaines authentifiés.

`ProjectsDomain.listResources()` scanne `ProjectsConfig.SCAN_ROOTS` (par
défaut le repo lui-même, en repartant du dossier courant — suppose un
lancement depuis `backend-java/`) à chaque appel, comme côté Node ; adapte
`ProjectsConfig` vers tes vrais dossiers de projets.

Compile et testé avec `mvn clean test` / `mvn package` (Maven est
disponible dans cet environnement depuis le domaine `jira` : premiers
domaines réellement compilés et lancés, `/api/domains/*` vérifiés à la
main avec `curl` contre le jar packagé — `système`, `serveurs` et
`projects`, écrits avant, n'avaient pu être vérifiés qu'à la lecture).

## Structure

Même architecture domaine/ressource/extracteur que côté Node, transposée en
Java :

- `Domain` (interface) — `id()`, `name()`, `listResources()`, `extractors()`
- `Resource` (record) — `id`, `name`, `types`, `extra` (champs spécifiques
  au domaine, ex: `path` pour un projet, `jql` pour Jira)
- `Extractor` (interface) — `id()`, `name()`, `description()`,
  `compatibleTypes()`, `fetch(Resource)`
- `ExtractorWidget` (record, sortie brute d'un extracteur) → décoré en
  `Widget` (record final envoyé au front) par `DomainRegistry`
- `DomainRegistry` — équivalent de `backend/src/domains/index.js` : calcule
  le produit ressources × extracteurs compatibles, en parallèle via des
  **threads virtuels** (`Executors.newVirtualThreadPerTaskExecutor()`,
  l'équivalent du `Promise.all` côté Node), et convertit toute exception
  d'un extracteur en widget d'erreur plutôt que de faire échouer l'appel.

Le contrat HTTP (`/api/domains`, `/api/domains/{id}/resources`,
`/api/domains/{id}/extractors`, `POST /api/data`) est identique à celui du
backend Node — le frontend React n'a besoin d'aucune modification pour
pointer vers l'un ou l'autre.

## Lancer

```bash
mvn package
java -jar target/simpledash-backend.jar   # http://localhost:3008 (ou $PORT)
```

## Credentials (`.env`)

Copie `.env.example` en `.env` (non versionné, ignoré comme côté Node) et
renseigne les variables des domaines authentifiés (`JIRA_*`,
`BITBUCKET_*`, `BAMBOO_*`, `SONAR_*`). Un domaine dont les variables ne
sont pas renseignées reste vide (pas d'erreur), il n'apparaît juste sans
ressource à sélectionner — même comportement que côté Node.

Contrairement à Node (`node --env-file-if-exists=.env`, flag CLI natif),
Java n'a pas d'équivalent intégré : `Env.get(...)`
(`lib/Env.java`) charge lui-même `.env` depuis le répertoire courant au
premier appel (suppose un lancement depuis `backend-java/`, comme
`ProjectsConfig`). Une vraie variable d'environnement du process a
toujours priorité sur la valeur du fichier, comme côté Node.

## Notes de portage

- **JSON** : sérialisation manuelle via Jackson (`ObjectMapper` direct dans
  `Main.java`) plutôt que le mapper JSON par défaut de Javalin, pour rester
  indépendant de la configuration du plugin JSON de Javalin.
- **CORS** : géré à la main (`app.before(...)`) plutôt que via le plugin CORS.
- **Concurrence** : `Promise.all` (Node) → threads virtuels (Java 21+), pas
  de `CompletableFuture` imbriqués.
- **XML/HTTP client** : contrairement à Node, le JDK a un parseur XML natif
  (`javax.xml.parsers.DocumentBuilder`, utilisé pour pom.xml) et
  `java.net.http.HttpClient` — pas besoin d'équivalent à `fast-xml-parser`.
- **TOML (Cargo.toml)** : contrairement à Node (`smol-toml`), pas de
  parseur TOML dans le JDK — dépendance `toml4j` ajoutée, choisie pour son
  API simple (accesseurs par chemin à points) plutôt que la plus récente
  `tomlj`, plus difficile à utiliser correctement sans pouvoir compiler ici
  pour vérifier.
- `system-info` simplifié par rapport à la version Node : pas d'équivalent
  standard multi-OS à l'uptime système en Java (seul l'uptime du process
  JVM est exposé), et le modèle CPU n'est pas exposé nativement.
- `GitInfoExtractorTest` utilise `@TempDir` (nettoyage géré par JUnit,
  sans les options de retry qu'on avait dû ajouter côté Node pour un
  `EBUSY` Windows après un sous-processus `git`) — si le même problème
  apparaît ici, il faudra gérer les répertoires temporaires à la main
  avec une logique de retry équivalente.
- **Dates Jira** : le champ `updated` renvoyé par l'API Jira utilise un
  offset sans deux-points (`+0000`), pas le format `OffsetDateTime.parse`
  par défaut (`ISO_OFFSET_DATE_TIME`) — `IssuesExtractor` utilise un
  `DateTimeFormatter` dédié (motif `Z`) plutôt que le format implicite.
- **Tests HTTP** : comme `HttpStatusExtractorTest`, tous les extracteurs
  HTTP (`IssuesExtractorTest`, `PullRequestsExtractorTest`,
  `BuildStatusExtractorTest`, `QualityExtractorTest`) démarrent un vrai
  `com.sun.net.httpserver.HttpServer` local plutôt que de mocker le
  client HTTP, pour garder la même philosophie de test que côté Node
  (fichiers/process réels) même si Node, lui, mocke `fetch` sur ce point
  précis.
- **Dates Bitbucket** : `createdDate` est un epoch millis (nombre), pas
  une chaîne ISO comme le `updated` de Jira — formaté via
  `Instant.ofEpochMilli(...)` avec le fuseau par défaut de la JVM, comme
  `new Date(ms).toLocaleDateString('fr-FR')` côté Node utilise le fuseau
  du process.
- **Bamboo** : `buildStartedTime` est au format ISO avec deux-points dans
  l'offset (`+02:00`), donc `OffsetDateTime.parse` par défaut suffit,
  contrairement à Jira. `BuildStatusExtractor.fetch` lance le résultat du
  plan principal et la liste des branches en parallèle (threads
  virtuels), puis le résultat de chaque branche, comme les `Promise.all`
  imbriqués de `build-status.js` — `Future.get()` enveloppant toute
  exception dans une `ExecutionException`, un petit `await(...)` la
  déballe pour que `getMessage()` reste `"Erreur Bamboo: ..."` telle
  quelle plutôt que noyée dans le message de l'enveloppe.
- **Sonar** : auth Basic (token en nom d'utilisateur, mot de passe vide —
  encodé en base64 avec `Base64.getEncoder()`), pas de Bearer comme les 3
  autres domaines. Le format de date des analyses (`+0200`, sans
  deux-points ni millisecondes) diffère à la fois de Jira et de Bamboo,
  d'où un troisième `DateTimeFormatter` dédié. Les métriques Sonar sont
  des chaînes ("0.0" inclus) : comme en JS où seule une chaîne vide/nulle
  est "falsy", `QualityExtractor` teste la présence de la valeur plutôt
  que sa valeur numérique, pour ne pas afficher "?" sur une couverture ou
  une duplication à 0.
