package com.simpledash.lib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalyzeProjectTest {

    @Test
    void directoryWithoutMarkers(@TempDir Path dir) throws Exception {
        var result = AnalyzeProject.analyzeProject(dir);

        assertNull(result.pom());
        assertNull(result.npm());
        assertTrue(result.summary().javaVersion().isEmpty());
        assertTrue(result.modules().isEmpty());
    }

    @Test
    void packageJsonOnly(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("package.json"), """
            {
              "name": "my-frontend",
              "version": "0.1.0",
              "dependencies": { "@angular/core": "17.0.2" }
            }
            """);

        var result = AnalyzeProject.analyzeProject(dir);

        assertNull(result.pom());
        assertEquals("my-frontend", result.npm().name());
        assertEquals("0.1.0", result.npm().version());
        assertEquals(List.of("17.0.2"), result.summary().angularVersion());
    }

    @Test
    void simplePomWithoutParent(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("pom.xml"), """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example</groupId>
              <artifactId>simple-app</artifactId>
              <version>1.2.3</version>
              <properties>
                <java.version>17</java.version>
              </properties>
              <dependencies>
                <dependency>
                  <groupId>org.apache.commons</groupId>
                  <artifactId>commons-lang3</artifactId>
                  <version>3.14.0</version>
                </dependency>
              </dependencies>
            </project>
            """);

        var result = AnalyzeProject.analyzeProject(dir);

        assertNull(result.pom().parent());
        assertEquals("com.example", result.pom().groupId());
        assertEquals("simple-app", result.pom().artifactId());
        assertEquals("1.2.3", result.pom().version());
        assertEquals("17", result.pom().properties().get("java.version"));
        assertEquals(1, result.pom().dependencies().size());
        assertEquals("commons-lang3", result.pom().dependencies().get(0).artifactId());
        assertEquals(List.of("17"), result.summary().javaVersion());
        assertTrue(result.modules().isEmpty());
    }

    @Test
    void pomWithSpringBootStarterParent(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("pom.xml"), """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <parent>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-parent</artifactId>
                <version>3.2.1</version>
              </parent>
              <artifactId>spring-app</artifactId>
              <properties>
                <java.version>21</java.version>
              </properties>
              <dependencies>
                <dependency>
                  <groupId>org.springframework.boot</groupId>
                  <artifactId>spring-boot-starter-web</artifactId>
                </dependency>
              </dependencies>
            </project>
            """);

        var result = AnalyzeProject.analyzeProject(dir);

        assertEquals("spring-boot-starter-parent", result.pom().parent().artifactId());
        // groupId/version hérités du parent car absents du pom enfant
        assertEquals("org.springframework.boot", result.pom().groupId());
        assertEquals("3.2.1", result.pom().version());
        assertEquals("spring-app", result.pom().artifactId());
        assertNull(result.pom().dependencies().get(0).version());
        assertEquals(List.of("3.2.1"), result.summary().springBootVersion());
        assertEquals(List.of("21"), result.summary().javaVersion());
    }

    @Test
    void multiModulePomWithPackageJsonInModule(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("pom.xml"), """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example</groupId>
              <artifactId>multi-module</artifactId>
              <version>1.0.0</version>
              <packaging>pom</packaging>
              <modules>
                <module>module-core</module>
                <module>module-web</module>
              </modules>
            </project>
            """);

        Files.createDirectory(dir.resolve("module-core"));
        Files.writeString(dir.resolve("module-core").resolve("pom.xml"), """
            <project>
              <parent>
                <groupId>com.example</groupId>
                <artifactId>multi-module</artifactId>
                <version>1.0.0</version>
              </parent>
              <artifactId>module-core</artifactId>
            </project>
            """);

        Files.createDirectory(dir.resolve("module-web"));
        Files.writeString(dir.resolve("module-web").resolve("pom.xml"), """
            <project>
              <parent>
                <groupId>com.example</groupId>
                <artifactId>multi-module</artifactId>
                <version>1.0.0</version>
              </parent>
              <artifactId>module-web</artifactId>
            </project>
            """);
        Files.writeString(dir.resolve("module-web").resolve("package.json"), """
            {
              "name": "module-web-frontend",
              "version": "0.1.0",
              "dependencies": { "@angular/core": "17.0.2" }
            }
            """);

        var result = AnalyzeProject.analyzeProject(dir);

        assertEquals(List.of("module-core", "module-web"), result.pom().modules());
        assertEquals(2, result.modules().size());

        var core = result.modules().get(0);
        assertEquals("module-core", core.pom().artifactId());
        assertEquals("com.example", core.pom().groupId());
        assertEquals("1.0.0", core.pom().version());
        assertNull(core.npm());

        var web = result.modules().get(1);
        assertEquals("module-web", web.pom().artifactId());
        assertEquals("module-web-frontend", web.npm().name());
        assertEquals(List.of("17.0.2"), web.summary().angularVersion());

        // la racine (pom seul, pas de package.json) remonte quand même
        // l'Angular détecté dans le sous-module
        assertEquals(List.of("17.0.2"), result.summary().angularVersion());
    }

    @Test
    void differingJavaVersionAcrossModulesAggregates(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("pom.xml"), """
            <project>
              <groupId>com.example</groupId>
              <artifactId>multi-module</artifactId>
              <version>1.0.0</version>
              <packaging>pom</packaging>
              <properties>
                <java.version>21</java.version>
              </properties>
              <modules>
                <module>module-a</module>
                <module>module-b</module>
              </modules>
            </project>
            """);

        Files.createDirectory(dir.resolve("module-a"));
        Files.writeString(dir.resolve("module-a").resolve("pom.xml"), """
            <project>
              <parent>
                <groupId>com.example</groupId>
                <artifactId>multi-module</artifactId>
                <version>1.0.0</version>
              </parent>
              <artifactId>module-a</artifactId>
              <properties>
                <java.version>21</java.version>
              </properties>
            </project>
            """);

        Files.createDirectory(dir.resolve("module-b"));
        Files.writeString(dir.resolve("module-b").resolve("pom.xml"), """
            <project>
              <parent>
                <groupId>com.example</groupId>
                <artifactId>multi-module</artifactId>
                <version>1.0.0</version>
              </parent>
              <artifactId>module-b</artifactId>
              <properties>
                <java.version>25</java.version>
              </properties>
            </project>
            """);

        var result = AnalyzeProject.analyzeProject(dir);

        assertEquals(List.of("21", "25"), result.summary().javaVersion());
    }

    @Test
    void npmWorkspaceMemberIsAnalyzed(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("package.json"), """
            { "name": "root", "version": "1.0.0", "workspaces": ["packages/pkg-a"] }
            """);
        Files.createDirectories(dir.resolve("packages").resolve("pkg-a"));
        Files.writeString(dir.resolve("packages").resolve("pkg-a").resolve("package.json"), """
            { "name": "pkg-a", "version": "0.1.0" }
            """);

        var result = AnalyzeProject.analyzeProject(dir);

        assertEquals(1, result.modules().size());
        assertEquals("pkg-a", result.modules().get(0).npm().name());
    }

    @Test
    void globPatternInWorkspacesIsNotResolved(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("package.json"), """
            { "name": "root", "version": "1.0.0", "workspaces": ["packages/*"] }
            """);
        Files.createDirectories(dir.resolve("packages").resolve("pkg-a"));
        Files.writeString(dir.resolve("packages").resolve("pkg-a").resolve("package.json"), """
            { "name": "pkg-a", "version": "0.1.0" }
            """);

        var result = AnalyzeProject.analyzeProject(dir);

        assertTrue(result.modules().isEmpty());
    }

    @Test
    void cargoTomlOnly(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Cargo.toml"), """
            [package]
            name = "my-crate"
            version = "0.3.1"
            rust-version = "1.75"

            [dependencies]
            serde = "1.0"
            tokio = { version = "1.35", features = ["full"] }
            """);

        var result = AnalyzeProject.analyzeProject(dir);

        assertEquals("my-crate", result.rust().name());
        assertEquals("0.3.1", result.rust().version());
        assertEquals("1.75", result.rust().rustVersion());
        assertEquals(Map.of("serde", "1.0", "tokio", "1.35"), result.rust().dependencies());
        assertEquals(List.of("1.75"), result.summary().rustVersion());
        assertTrue(result.modules().isEmpty());
    }

    @Test
    void goModWithBlockAndSingleLineRequire(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("go.mod"), """
            module github.com/example/my-service

            go 1.21

            require github.com/gin-gonic/gin v1.9.1

            require (
                github.com/foo/bar v1.2.3
                github.com/baz/qux v0.5.0 // indirect
            )
            """);

        var result = AnalyzeProject.analyzeProject(dir);

        assertEquals("github.com/example/my-service", result.go().module());
        assertEquals("1.21", result.go().goVersion());
        assertEquals(
            Map.of(
                "github.com/gin-gonic/gin", "v1.9.1",
                "github.com/foo/bar", "v1.2.3",
                "github.com/baz/qux", "v0.5.0"
            ),
            result.go().dependencies()
        );
        assertEquals(List.of("1.21"), result.summary().goVersion());
    }

    @Test
    void cargoWorkspaceMemberIsAnalyzed(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Cargo.toml"), """
            [workspace]
            members = ["crates/crate-a"]
            """);
        Files.createDirectories(dir.resolve("crates").resolve("crate-a"));
        Files.writeString(dir.resolve("crates").resolve("crate-a").resolve("Cargo.toml"), """
            [package]
            name = "crate-a"
            version = "0.1.0"
            """);

        var result = AnalyzeProject.analyzeProject(dir);

        assertEquals(1, result.modules().size());
        assertEquals("crate-a", result.modules().get(0).rust().name());
    }

    @Test
    void goWorkMembersAreAnalyzedEvenWithoutRootGoMod(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("go.work"), """
            go 1.21

            use (
                ./service-a
                ./service-b
            )
            """);
        Files.createDirectory(dir.resolve("service-a"));
        Files.writeString(dir.resolve("service-a").resolve("go.mod"), "module github.com/example/service-a\n\ngo 1.21\n");
        Files.createDirectory(dir.resolve("service-b"));
        Files.writeString(dir.resolve("service-b").resolve("go.mod"), "module github.com/example/service-b\n\ngo 1.21\n");

        var result = AnalyzeProject.analyzeProject(dir);

        assertNull(result.go());
        assertEquals(2, result.modules().size());
        var modules = result.modules().stream().map(m -> m.go().module()).sorted().toList();
        assertEquals(List.of("github.com/example/service-a", "github.com/example/service-b"), modules);
    }

    @Test
    void globPatternInCargoWorkspaceMembersIsNotResolved(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Cargo.toml"), """
            [workspace]
            members = ["crates/*"]
            """);
        Files.createDirectories(dir.resolve("crates").resolve("crate-a"));
        Files.writeString(dir.resolve("crates").resolve("crate-a").resolve("Cargo.toml"), """
            [package]
            name = "crate-a"
            version = "0.1.0"
            """);

        var result = AnalyzeProject.analyzeProject(dir);

        assertTrue(result.modules().isEmpty());
    }
}
