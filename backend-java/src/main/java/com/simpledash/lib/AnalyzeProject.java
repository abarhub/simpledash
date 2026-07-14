package com.simpledash.lib;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moandjiezana.toml.Toml;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

// Portage de backend/src/lib/analyzeProject.js : pom.xml, package.json,
// Cargo.toml et/ou go.mod (plusieurs peuvent coexister), et suit
// récursivement les sous-modules déclarés par chaque écosystème (<modules>
// Maven, "workspaces" npm, [workspace].members Cargo, directives "use" d'un
// go.work). Les patterns glob ("packages/*") ne sont pas résolus, seuls les
// chemins littéraux sont suivis. Ne résout pas l'héritage Maven complet
// (pas d'effective-pom) : les valeurs pilotées par des propriétés ou un BOM
// peuvent rester non résolues.
public class AnalyzeProject {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> PROJECT_MARKERS = List.of("pom.xml", "package.json", "Cargo.toml", "go.mod");
    private static final Pattern REQUIRE_LINE = Pattern.compile("^require\\s+(\\S+)\\s+(\\S+)");
    private static final Pattern USE_LINE = Pattern.compile("^use\\s+(\\S+)");

    public static AnalyzedProject analyzeProject(Path dir) throws Exception {
        Path pomPath = dir.resolve("pom.xml");
        Path packageJsonPath = dir.resolve("package.json");
        Path cargoTomlPath = dir.resolve("Cargo.toml");
        Path goModPath = dir.resolve("go.mod");
        Path goWorkPath = dir.resolve("go.work");

        PomInfo pom = Files.exists(pomPath) ? parsePom(pomPath) : null;
        NpmInfo npm = Files.exists(packageJsonPath) ? parsePackageJson(packageJsonPath) : null;
        RustInfo rust = Files.exists(cargoTomlPath) ? parseCargoToml(cargoTomlPath) : null;
        GoInfo go = Files.exists(goModPath) ? parseGoMod(goModPath) : null;
        List<String> goWorkMembers = Files.exists(goWorkPath) ? parseGoWorkMembers(goWorkPath) : List.of();

        Set<String> declaredModulePaths = new LinkedHashSet<>();
        if (pom != null) declaredModulePaths.addAll(pom.modules());
        if (npm != null) declaredModulePaths.addAll(withoutGlobs(npm.workspaces()));
        if (rust != null) declaredModulePaths.addAll(withoutGlobs(rust.workspaceMembers()));
        declaredModulePaths.addAll(withoutGlobs(goWorkMembers));

        List<AnalyzedProject> modules = new ArrayList<>();
        for (String modulePath : declaredModulePaths) {
            Path moduleDir = dir.resolve(modulePath).normalize();
            if (hasAnyProjectMarker(moduleDir)) {
                modules.add(analyzeProject(moduleDir));
            }
        }

        ProjectSummary summary = buildSummary(pom, npm, rust, go, modules.stream().map(AnalyzedProject::summary).toList());

        return new AnalyzedProject(dir, pom, npm, rust, go, summary, modules);
    }

    private static List<String> withoutGlobs(List<String> entries) {
        return entries.stream().filter(e -> !e.contains("*")).toList();
    }

    private static boolean hasAnyProjectMarker(Path dir) {
        for (String marker : PROJECT_MARKERS) {
            if (Files.exists(dir.resolve(marker))) return true;
        }
        return false;
    }

    // --- pom.xml ---

    private static PomInfo parsePom(Path pomPath) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(pomPath.toFile());
        doc.getDocumentElement().normalize();

        Element project = doc.getDocumentElement();

        Element parentEl = firstChildElement(project, "parent");
        ParentInfo parent = parentEl == null
            ? null
            : new ParentInfo(childText(parentEl, "groupId"), childText(parentEl, "artifactId"), childText(parentEl, "version"));

        String groupId = firstNonNull(childText(project, "groupId"), parent == null ? null : parent.groupId());
        String artifactId = childText(project, "artifactId");
        String version = firstNonNull(childText(project, "version"), parent == null ? null : parent.version());

        Map<String, String> properties = new LinkedHashMap<>();
        Element propertiesEl = firstChildElement(project, "properties");
        if (propertiesEl != null) {
            for (Element propEl : elementChildren(propertiesEl)) {
                properties.put(propEl.getNodeName(), propEl.getTextContent().trim());
            }
        }

        List<Dependency> dependencies = new ArrayList<>();
        Element dependenciesEl = firstChildElement(project, "dependencies");
        if (dependenciesEl != null) {
            for (Element depEl : childElements(dependenciesEl, "dependency")) {
                dependencies.add(new Dependency(
                    childText(depEl, "groupId"),
                    childText(depEl, "artifactId"),
                    childText(depEl, "version"),
                    childText(depEl, "scope")
                ));
            }
        }

        List<String> modules = new ArrayList<>();
        Element modulesEl = firstChildElement(project, "modules");
        if (modulesEl != null) {
            for (Element moduleEl : childElements(modulesEl, "module")) {
                modules.add(moduleEl.getTextContent().trim());
            }
        }

        return new PomInfo(parent, groupId, artifactId, version, properties, dependencies, modules);
    }

    private static Element firstChildElement(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals(tagName)) {
                return (Element) node;
            }
        }
        return null;
    }

    private static List<Element> childElements(Element parent, String tagName) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals(tagName)) {
                result.add((Element) node);
            }
        }
        return result;
    }

    private static List<Element> elementChildren(Element parent) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                result.add((Element) node);
            }
        }
        return result;
    }

    private static String childText(Element parent, String tagName) {
        Element child = firstChildElement(parent, tagName);
        return child == null ? null : child.getTextContent().trim();
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }

    // --- package.json ---

    private static NpmInfo parsePackageJson(Path packageJsonPath) throws Exception {
        JsonNode json = MAPPER.readTree(packageJsonPath.toFile());

        List<String> workspaces = new ArrayList<>();
        JsonNode workspacesNode = json.get("workspaces");
        if (workspacesNode != null && workspacesNode.isArray()) {
            workspacesNode.forEach(n -> workspaces.add(n.asText()));
        } else if (workspacesNode != null && workspacesNode.has("packages")) {
            workspacesNode.get("packages").forEach(n -> workspaces.add(n.asText()));
        }

        return new NpmInfo(
            textOrNull(json.get("name")),
            textOrNull(json.get("version")),
            toStringMap(json.get("dependencies")),
            toStringMap(json.get("devDependencies")),
            workspaces
        );
    }

    private static Map<String, String> toStringMap(JsonNode node) {
        Map<String, String> result = new LinkedHashMap<>();
        if (node != null && node.isObject()) {
            node.fields().forEachRemaining(e -> result.put(e.getKey(), e.getValue().asText()));
        }
        return result;
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    // --- Cargo.toml ---

    private static RustInfo parseCargoToml(Path cargoTomlPath) throws Exception {
        Toml toml = new Toml().read(cargoTomlPath.toFile());

        Map<String, String> dependencies = new LinkedHashMap<>();
        Toml depsTable = toml.getTable("dependencies");
        if (depsTable != null) {
            for (Map.Entry<String, Object> entry : depsTable.toMap().entrySet()) {
                Object value = entry.getValue();
                String version = null;
                if (value instanceof String s) {
                    version = s;
                } else if (value instanceof Map<?, ?> m && m.get("version") instanceof String s) {
                    version = s;
                }
                if (version != null) {
                    dependencies.put(entry.getKey(), version);
                }
            }
        }

        List<String> workspaceMembers = new ArrayList<>();
        List<Object> members = toml.getList("workspace.members");
        if (members != null) {
            for (Object m : members) {
                workspaceMembers.add(String.valueOf(m));
            }
        }

        return new RustInfo(
            toml.getString("package.name"),
            toml.getString("package.version"),
            toml.getString("package.rust-version"),
            dependencies,
            workspaceMembers
        );
    }

    // --- go.mod / go.work ---
    // Ni JSON ni TOML : parsées ligne à ligne, sans dépendance (format
    // simple et stable).

    private static GoInfo parseGoMod(Path goModPath) throws Exception {
        List<String> lines = Files.readAllLines(goModPath).stream().map(String::trim).toList();

        String moduleLine = lines.stream().filter(l -> l.startsWith("module ")).findFirst().orElse(null);
        String goLine = lines.stream().filter(l -> l.matches("^go\\s+\\d.*")).findFirst().orElse(null);

        Map<String, String> dependencies = new LinkedHashMap<>();
        boolean inRequireBlock = false;
        for (String line : lines) {
            if (line.startsWith("require (")) {
                inRequireBlock = true;
                continue;
            }
            if (inRequireBlock) {
                if (line.equals(")")) {
                    inRequireBlock = false;
                    continue;
                }
                String[] parts = line.split("\\s+");
                if (parts.length >= 2) {
                    dependencies.put(parts[0], parts[1]);
                }
                continue;
            }
            Matcher matcher = REQUIRE_LINE.matcher(line);
            if (matcher.find()) {
                dependencies.put(matcher.group(1), matcher.group(2));
            }
        }

        return new GoInfo(
            moduleLine != null ? moduleLine.substring("module ".length()).trim() : null,
            goLine != null ? goLine.substring("go ".length()).trim() : null,
            dependencies
        );
    }

    private static List<String> parseGoWorkMembers(Path goWorkPath) throws Exception {
        List<String> lines = Files.readAllLines(goWorkPath).stream().map(String::trim).toList();

        List<String> members = new ArrayList<>();
        boolean inUseBlock = false;
        for (String line : lines) {
            if (line.startsWith("use (")) {
                inUseBlock = true;
                continue;
            }
            if (inUseBlock) {
                if (line.equals(")")) {
                    inUseBlock = false;
                    continue;
                }
                if (!line.isEmpty()) members.add(line);
                continue;
            }
            Matcher matcher = USE_LINE.matcher(line);
            if (matcher.find()) {
                members.add(matcher.group(1));
            }
        }

        return members;
    }

    // --- résumé agrégé (bottom-up) ---

    private static ProjectSummary buildSummary(
        PomInfo pom, NpmInfo npm, RustInfo rust, GoInfo go, List<ProjectSummary> childSummaries
    ) {
        List<String> ownJava = new ArrayList<>();
        List<String> ownSpringBoot = new ArrayList<>();
        List<String> ownAngular = new ArrayList<>();
        List<String> ownRust = new ArrayList<>();
        List<String> ownGo = new ArrayList<>();

        if (pom != null) {
            String javaVersion = firstNonNull(
                pom.properties().get("java.version"),
                firstNonNull(pom.properties().get("maven.compiler.release"), pom.properties().get("maven.compiler.source"))
            );
            if (javaVersion != null) ownJava.add(javaVersion);

            String springBootVersion;
            if (pom.parent() != null && "spring-boot-starter-parent".equals(pom.parent().artifactId())) {
                springBootVersion = pom.parent().version();
            } else {
                springBootVersion = pom.properties().get("spring-boot.version");
                if (springBootVersion == null) {
                    springBootVersion = pom.dependencies().stream()
                        .filter(d -> "org.springframework.boot".equals(d.groupId()) && d.version() != null)
                        .map(Dependency::version)
                        .findFirst()
                        .orElse(null);
                }
            }
            if (springBootVersion != null) ownSpringBoot.add(springBootVersion);
        }

        if (npm != null) {
            String angularVersion = npm.dependencies().get("@angular/core");
            if (angularVersion != null) ownAngular.add(angularVersion);
        }

        if (rust != null && rust.rustVersion() != null) {
            ownRust.add(rust.rustVersion());
        }

        if (go != null && go.goVersion() != null) {
            ownGo.add(go.goVersion());
        }

        return new ProjectSummary(
            mergeUnique(ownJava, childSummaries.stream().map(ProjectSummary::javaVersion).toList()),
            mergeUnique(ownSpringBoot, childSummaries.stream().map(ProjectSummary::springBootVersion).toList()),
            mergeUnique(ownAngular, childSummaries.stream().map(ProjectSummary::angularVersion).toList()),
            mergeUnique(ownRust, childSummaries.stream().map(ProjectSummary::rustVersion).toList()),
            mergeUnique(ownGo, childSummaries.stream().map(ProjectSummary::goVersion).toList())
        );
    }

    private static List<String> mergeUnique(List<String> own, List<List<String>> childLists) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(own);
        for (List<String> childList : childLists) {
            merged.addAll(childList);
        }
        return List.copyOf(merged);
    }
}
