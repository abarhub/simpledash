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

`ProjectsDomain.listResources()` scanne les racines déclarées dans
`projects.yml` (voir section dédiée plus bas) à chaque appel, comme côté
Node.

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

### Déploiement mono-serveur

Comme côté Node (`backend/src/index.js`), si `../frontend/dist` existe
(après un `npm run build` côté frontend), `Main.java` sert ce dossier en
statique et retombe sur `index.html` pour toute route non-`/api` non
trouvée (routage côté client) — via `config.staticFiles.add(...)` et
`config.spaRoot.addFile("/", ...)` de Javalin. Suppose un lancement
depuis `backend-java/`, comme `Env`. En dev (`frontend/dist`
absent), l'API répond normalement et rien n'est servi à la racine.
Vérifié en conditions réelles (jar packagé + frontend buildé) : page,
assets et repli SPA tous corrects — y compris le même comportement que
Node sur un chemin non-`/api` qui ressemble à un asset mais n'existe pas
(repli sur `index.html` avec 200, pas de vrai 404).

## Credentials (`.env`)

Copie `.env.example` en `.env` (non versionné, ignoré comme côté Node) et
renseigne les variables des domaines authentifiés (`JIRA_*`,
`BITBUCKET_*`, `BAMBOO_*`, `SONAR_*`). Un domaine dont les variables ne
sont pas renseignées reste vide (pas d'erreur), il n'apparaît juste sans
ressource à sélectionner — même comportement que côté Node.

Contrairement à Node (`node --env-file-if-exists=.env`, flag CLI natif),
Java n'a pas d'équivalent intégré : `Env.get(...)`
(`lib/Env.java`) charge lui-même `.env` depuis le répertoire courant au
premier appel (suppose un lancement depuis `backend-java/`). Une vraie
variable d'environnement du process a toujours priorité sur la valeur du
fichier, comme côté Node.

## Projets à scanner (`projects.yml`)

Contrairement à Node (`backend/src/domains/projects/config.js`, un
fichier JS édité directement), la liste des racines à scanner est
externalisée dans un fichier YAML plutôt que codée en dur : copie
`projects.yml.example` en `projects.yml` (non versionné, comme `.env`) à
la racine de `backend-java/`, et adapte-le :

```yaml
scanRoots:
  - D:/projet/mon-appli-java
  - D:/projet/dossier-avec-plusieurs-projets
ignoreDirs:
  - dossier-a-ignorer
groups:
  - id: mes-projets-java
    name: Mes projets Java
    paths:
      - D:/projet/mon-appli-java/module-a
      - D:/projet/mon-appli-java/module-b
```

- `scanRoots` : un ou plusieurs répertoires, scannés récursivement par
  `FindProjects` à la recherche de projets (marqueurs : `pom.xml`,
  `package.json`, `Cargo.toml`, `go.mod`) — le scan s'arrête dès qu'un
  marqueur est trouvé dans un répertoire (pas de descente dans ses
  sous-dossiers).
- `ignoreDirs` : noms de répertoires ignorés pendant le scan, **en plus**
  des ignorés par défaut de `FindProjects` (`node_modules`, `target`,
  `dist`, `build`, `out`, `.git`, `venv`, `.venv`, `env`, `__pycache__`).
- `groups` (optionnel) : regroupe des ressources déjà trouvées par le
  scan, par chemin exact (pas par id, généré dynamiquement). Un groupe
  "Tous les projets" est toujours ajouté automatiquement en plus.

Absent, le domaine reste vide (pas d'erreur), même comportement que les
domaines authentifiés sans `.env`. Comme pour `.env`, c'est chargé une
seule fois au premier accès (suppose un lancement depuis
`backend-java/`) — modifier `projects.yml` nécessite un redémarrage du
process (pas de rebuild : contrairement au code en dur d'avant, ce n'est
plus compilé).

Vérifié en conditions réelles (jar packagé) avec un `projects.yml`
pointant vers `backend-java/`, `backend/` et `frontend/` de ce repo :
détection correcte des types (`maven`/`npm`), et filtrage du groupe
configuré à la seule ressource concernée.

## Certificat TLS interne

Si Jira/Bitbucket/Bamboo/Sonar tournent derrière une CA interne (cas
courant en on-premise), les appels `HttpClient` échoueront tant que la
JVM ne connaît pas cette CA — même symptôme que côté Node, mais pas le
même mécanisme ni la même solution.

Côté Node, `NODE_EXTRA_CA_CERTS` **ajoute** une CA à celles déjà connues
(voir le [README racine](../README.md)). La JVM n'a pas d'équivalent
additif intégré : `-Djavax.net.ssl.trustStore=...` **remplace**
entièrement le trust store par défaut. Pointer ce paramètre directement
vers un fichier ne contenant que la CA interne casserait donc la
confiance pour tout le reste (Bitbucket Cloud, GitHub, etc. si jamais
appelés) — il faut d'abord fusionner la CA interne dans une **copie** du
trust store fourni par le JDK :

```bash
cp "$JAVA_HOME/lib/security/cacerts" ./cacerts-avec-ca-interne
keytool -import -trustcacerts -noprompt \
  -alias ca-interne \
  -file /chemin/vers/ca-interne.pem \
  -keystore ./cacerts-avec-ca-interne \
  -storepass changeit
```

(`changeit` est le mot de passe par défaut du `cacerts` fourni par le
JDK — à ajuster si ta distribution l'a changé.)

Puis, au lancement :

```bash
java -Djavax.net.ssl.trustStore=./cacerts-avec-ca-interne \
     -Djavax.net.ssl.trustStorePassword=changeit \
     -jar target/simpledash-backend.jar
```

Pour rester au plus près de l'ergonomie de `NODE_EXTRA_CA_CERTS=... npm
run dev` (une variable d'environnement à définir avant de lancer, sans
modifier la commande elle-même), la JVM reconnaît nativement
`JAVA_TOOL_OPTIONS` pour ça :

```bash
JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=./cacerts-avec-ca-interne -Djavax.net.ssl.trustStorePassword=changeit" \
java -jar target/simpledash-backend.jar
```

Comme côté Node où la variable doit être définie avant le lancement (pas
dans `backend/.env`, chargé trop tard pour le module TLS), ces propriétés
`-D`/`JAVA_TOOL_OPTIONS` doivent être présentes **au démarrage de la
JVM** — pas dans `.env` ni `projects.yml`, qui sont lus par le code de
l'appli bien après que `HttpClient.newHttpClient()` (champ statique de
chaque extracteur HTTP) ait déjà capturé le `SSLContext` par défaut de la
JVM.

Éviter un `TrustManager` qui accepte tout (l'équivalent Java de
`NODE_TLS_REJECT_UNAUTHORIZED=0`) : ça désactive la vérification pour
tout le process plutôt que de faire confiance uniquement à la CA
interne. Non testé contre un vrai serveur avec CA interne (même
limitation que la section équivalente du README racine) — recette à
vérifier après configuration.

## Notes de portage

- **JSON** : sérialisation manuelle via Jackson (`ObjectMapper` direct dans
  `Main.java`) plutôt que le mapper JSON par défaut de Javalin, pour rester
  indépendant de la configuration du plugin JSON de Javalin.
- **CORS** : géré à la main (`app.before(...)`) plutôt que via le plugin CORS.
- **Fichiers statiques** : `config.staticFiles.add(...)` / `config.spaRoot.addFile(...)`
  (API confirmée via `javap` sur le jar Javalin, puis vérifiée à l'exécution) plutôt que du
  `app.before`/`app.get("*", ...)` fait main, contrairement au choix pour JSON/CORS ci-dessus
  — l'API de plugin est ici la voie la plus simple et a pu être vérifiée avec certitude.
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
- **YAML (`projects.yml`)** : dépendance `jackson-dataformat-yaml` plutôt
  que SnakeYAML directement, pour réutiliser le même style de
  désérialisation (`ObjectMapper.readValue` vers un record) déjà en place
  pour le JSON, plutôt qu'introduire une deuxième API de parsing. Les
  records `RawConfig`/`RawGroup` de `ProjectsConfig` sont désérialisés
  automatiquement (le plugin compilateur Maven a déjà `-parameters`
  activé pour Javalin, ce qui suffit aussi à Jackson pour les records,
  sans module ni annotation `@JsonCreator` supplémentaire).
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
