package com.simpledash.domains.projects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.simpledash.domains.Resource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitInfoExtractorTest {

    private static void git(Path cwd, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(cwd.toFile()).start();
        process.getInputStream().readAllBytes();
        process.getErrorStream().readAllBytes();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("git " + String.join(" ", args) + " failed: " + exitCode);
        }
    }

    private static void initRepo(Path dir) throws Exception {
        git(dir, "init", "-b", "main");
        git(dir, "config", "user.email", "test@example.com");
        git(dir, "config", "user.name", "Test");
    }

    private static void commit(Path dir, String message) throws Exception {
        git(dir, "add", "-A");
        git(dir, "commit", "-m", message);
    }

    private static Resource resourceFor(Path dir) {
        return new Resource("r1", "repo", List.of("git"), Map.of("path", dir));
    }

    @Test
    void cleanRepoWithoutRemote(@TempDir Path dir) throws Exception {
        initRepo(dir);
        Files.writeString(dir.resolve("a.txt"), "hello");
        commit(dir, "Premier commit");

        var widgets = new GitInfoExtractor().fetch(resourceFor(dir));

        assertEquals(1, widgets.size());
        var widget = widgets.get(0);
        assertEquals("Git", widget.title());
        assertEquals("Premier commit", widget.data().get("Message"));
        assertTrue(widget.data().get("Dernier commit").matches("^[0-9a-f]{7,}$"));
        assertEquals("main", widget.data().get("Branche"));
        assertEquals("Non", widget.data().get("Modifs non commitées"));
        assertEquals("pas de remote suivi", widget.data().get("Vs remote"));
        assertEquals("main", widget.data().get("Branches contenant ce commit"));
        assertNull(widget.url());
    }

    @Test
    void detectsUncommittedChanges(@TempDir Path dir) throws Exception {
        initRepo(dir);
        Files.writeString(dir.resolve("a.txt"), "hello");
        commit(dir, "Premier commit");
        Files.writeString(dir.resolve("a.txt"), "modifié");

        var widgets = new GitInfoExtractor().fetch(resourceFor(dir));

        assertEquals("Oui", widgets.get(0).data().get("Modifs non commitées"));
    }

    @Test
    void buildsWebUrlFromHttpsRemote(@TempDir Path dir) throws Exception {
        initRepo(dir);
        Files.writeString(dir.resolve("a.txt"), "hello");
        commit(dir, "Premier commit");
        git(dir, "remote", "add", "origin", "https://github.com/abarhub/simpledash.git");

        var widgets = new GitInfoExtractor().fetch(resourceFor(dir));

        assertEquals("https://github.com/abarhub/simpledash", widgets.get(0).url());
    }

    @Test
    void buildsWebUrlFromSshRemote(@TempDir Path dir) throws Exception {
        initRepo(dir);
        Files.writeString(dir.resolve("a.txt"), "hello");
        commit(dir, "Premier commit");
        git(dir, "remote", "add", "origin", "git@github.com:abarhub/simpledash.git");

        var widgets = new GitInfoExtractor().fetch(resourceFor(dir));

        assertEquals("https://github.com/abarhub/simpledash", widgets.get(0).url());
    }

    @Test
    void aheadBehindAgainstTrackedUpstream(@TempDir Path bareDir, @TempDir Path dir) throws Exception {
        git(bareDir, "init", "--bare", "-b", "main");

        initRepo(dir);
        Files.writeString(dir.resolve("a.txt"), "hello");
        commit(dir, "Premier commit");
        git(dir, "remote", "add", "origin", bareDir.toString());
        git(dir, "push", "-u", "origin", "main");

        Files.writeString(dir.resolve("b.txt"), "world");
        commit(dir, "Deuxième commit");

        var widgets = new GitInfoExtractor().fetch(resourceFor(dir));

        assertEquals("1 en avance, 0 en retard", widgets.get(0).data().get("Vs remote"));
    }

    @Test
    void multipleBranchesOnSameCommit(@TempDir Path dir) throws Exception {
        initRepo(dir);
        Files.writeString(dir.resolve("a.txt"), "hello");
        commit(dir, "Premier commit");
        git(dir, "branch", "feature-x");

        var widgets = new GitInfoExtractor().fetch(resourceFor(dir));

        var branches = List.of(widgets.get(0).data().get("Branches contenant ce commit").split(", "));
        assertEquals(
            branches.stream().sorted().toList(),
            List.of("feature-x", "main").stream().sorted().toList()
        );
    }
}
