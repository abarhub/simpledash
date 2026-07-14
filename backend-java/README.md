# simpledash-backend (Java / Javalin)

Portage du backend Node (`../backend`) vers Java + [Javalin](https://javalin.io/)
(fine couche sur Jetty), motivé par le démarrage rapide et la faible
empreinte mémoire par rapport à un framework plus lourd type Spring Boot.

**Statut : domaines `système`, `serveurs` et `projects` portés** (ce
dernier pour `pom.xml`/`package.json` uniquement — pas de dépendance TOML
ajoutée pour Cargo.toml, pas de parsing go.mod/go.work ; Rust/Go suivront
dans une PR séparée, comme côté Node à l'époque). `jira`, `bitbucket`,
`bamboo`, `sonar` restent à porter.

`ProjectsDomain.listResources()` scanne `ProjectsConfig.SCAN_ROOTS` (par
défaut le repo lui-même, en repartant du dossier courant — suppose un
lancement depuis `backend-java/`) à chaque appel, comme côté Node ; adapte
`ProjectsConfig` vers tes vrais dossiers de projets.

**Cette étape d'intégration n'a pas pu être vérifiée en conditions réelles**
(pas de preview navigateur possible ici, en plus de ne pas pouvoir
compiler) — à tester particulièrement attentivement.

Compile et testé avec `mvn clean compile` / `mvn test`.

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

## Notes de portage

- **JSON** : sérialisation manuelle via Jackson (`ObjectMapper` direct dans
  `Main.java`) plutôt que le mapper JSON par défaut de Javalin, pour rester
  indépendant de la configuration du plugin JSON de Javalin.
- **CORS** : géré à la main (`app.before(...)`) plutôt que via le plugin CORS.
- **Concurrence** : `Promise.all` (Node) → threads virtuels (Java 21+), pas
  de `CompletableFuture` imbriqués.
- **XML/HTTP client** : contrairement à Node, le JDK a un parseur XML natif
  et `java.net.http.HttpClient` — pas besoin d'équivalent à
  `fast-xml-parser` pour un futur portage du domaine `projects`.
- `system-info` simplifié par rapport à la version Node : pas d'équivalent
  standard multi-OS à l'uptime système en Java (seul l'uptime du process
  JVM est exposé), et le modèle CPU n'est pas exposé nativement.
