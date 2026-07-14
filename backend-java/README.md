# simpledash-backend (Java / Javalin)

Portage du backend Node (`../backend`) vers Java + [Javalin](https://javalin.io/)
(fine couche sur Jetty), motivé par le démarrage rapide et la faible
empreinte mémoire par rapport à un framework plus lourd type Spring Boot.

**Statut : domaines `système`, `serveurs` et `projects` portés**, ce
dernier avec pom.xml/package.json/Cargo.toml/go.mod/go.work + un
extracteur Git (dernier commit, branche, statut, avance/retard sur le
remote, via `ProcessBuilder`). La gestion des credentials (`.env`) est en
place ; `jira`, `bitbucket`, `bamboo`, `sonar` restent à porter.

`ProjectsDomain.listResources()` scanne `ProjectsConfig.SCAN_ROOTS` (par
défaut le repo lui-même, en repartant du dossier courant — suppose un
lancement depuis `backend-java/`) à chaque appel, comme côté Node ; adapte
`ProjectsConfig` vers tes vrais dossiers de projets.

**Le branchement du domaine `projects` (`ProjectsDomain` et ses
extracteurs) n'a pas pu être vérifié en conditions réelles** (pas de
preview navigateur possible ici, en plus de ne pas pouvoir compiler) — à
tester particulièrement attentivement. `AnalyzeProject`/`FindProjects`
eux-mêmes ont leur couverture de tests.

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
