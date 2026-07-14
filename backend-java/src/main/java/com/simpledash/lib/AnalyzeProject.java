package com.simpledash.lib;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

// Portage de backend/src/lib/analyzeProject.js — pour l'instant pom.xml et
// package.json seulement (pas de dépendance TOML ajoutée pour Cargo.toml,
// pas de parsing go.mod/go.work). Rust/Go suivront dans une PR séparée,
// comme côté Node à l'origine.
//
// Suit récursivement les <modules> (Maven) et le champ "workspaces" (npm) ;
// ne résout pas les patterns glob ("packages/*"), seuls les chemins
// littéraux sont suivis. Ne résout pas l'héritage Maven complet (pas
// d'effective-pom) : les valeurs pilotées par des propriétés ou un BOM
// peuvent rester non résolues.
public class AnalyzeProject {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> PROJECT_MARKERS = List.of("pom.xml", "package.json", "Cargo.toml", "go.mod");

    public static AnalyzedProject analyzeProject(Path dir) throws Exception {
        Path pomPath = dir.resolve("pom.xml");
        Path packageJsonPath = dir.resolve("package.json");

        PomInfo pom = Files.exists(pomPath) ? parsePom(pomPath) : null;
        NpmInfo npm = Files.exists(packageJsonPath) ? parsePackageJson(packageJsonPath) : null;

        Set<String> declaredModulePaths = new LinkedHashSet<>();
        if (pom != null) declaredModulePaths.addAll(pom.modules());
        if (npm != null) declaredModulePaths.addAll(withoutGlobs(npm.workspaces()));

        List<AnalyzedProject> modules = new ArrayList<>();
        for (String modulePath : declaredModulePaths) {
            Path moduleDir = dir.resolve(modulePath).normalize();
            if (hasAnyProjectMarker(moduleDir)) {
                modules.add(analyzeProject(moduleDir));
            }
        }

        ProjectSummary summary = buildSummary(pom, npm, modules.stream().map(AnalyzedProject::summary).toList());

        return new AnalyzedProject(dir, pom, npm, summary, modules);
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

    // --- résumé agrégé (bottom-up) ---

    private static ProjectSummary buildSummary(PomInfo pom, NpmInfo npm, List<ProjectSummary> childSummaries) {
        List<String> ownJava = new ArrayList<>();
        List<String> ownSpringBoot = new ArrayList<>();
        List<String> ownAngular = new ArrayList<>();

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

        return new ProjectSummary(
            mergeUnique(ownJava, childSummaries.stream().map(ProjectSummary::javaVersion).toList()),
            mergeUnique(ownSpringBoot, childSummaries.stream().map(ProjectSummary::springBootVersion).toList()),
            mergeUnique(ownAngular, childSummaries.stream().map(ProjectSummary::angularVersion).toList())
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
