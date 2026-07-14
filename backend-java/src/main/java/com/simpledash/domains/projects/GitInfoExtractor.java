package com.simpledash.domains.projects;

import com.simpledash.domains.Extractor;
import com.simpledash.domains.ExtractorWidget;
import com.simpledash.domains.Resource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GitInfoExtractor implements Extractor {

    private static final Pattern SSH_REMOTE = Pattern.compile("^git@([^:]+):(.+?)(\\.git)?$");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.FRANCE);

    public String id() {
        return "git-info";
    }

    public String name() {
        return "Git";
    }

    public String description() {
        return "Dernier commit, branche courante, statut du dépôt, avance/retard sur le remote";
    }

    public List<String> compatibleTypes() {
        return List.of("git");
    }

    public List<ExtractorWidget> fetch(Resource resource) throws Exception {
        Path dir = (Path) resource.get("path");

        String shortHash = git(dir, "rev-parse", "--short", "HEAD");
        String commitDateRaw = git(dir, "log", "-1", "--format=%cI");
        String commitMessage = git(dir, "log", "-1", "--format=%s");
        String branch = git(dir, "branch", "--show-current");
        String status = git(dir, "status", "--porcelain");
        String containingBranchesRaw = git(dir, "branch", "--contains", "HEAD", "--format=%(refname:short)");

        String remoteUrl;
        try {
            remoteUrl = git(dir, "remote", "get-url", "origin");
        } catch (Exception e) {
            remoteUrl = "";
        }

        String vsRemote = "pas de remote suivi";
        try {
            String counts = git(dir, "rev-list", "--left-right", "--count", "HEAD...@{u}");
            String[] parts = counts.split("\\s+");
            if (parts.length == 2) {
                vsRemote = parts[0] + " en avance, " + parts[1] + " en retard";
            }
        } catch (Exception e) {
            // pas de branche upstream configurée pour la branche courante
        }

        List<String> branches = containingBranchesRaw.isBlank()
            ? List.of()
            : List.of(containingBranchesRaw.split("\n"));

        Map<String, String> data = new LinkedHashMap<>();
        data.put("Dernier commit", shortHash);
        data.put("Message", commitMessage);
        data.put("Date", formatDate(commitDateRaw));
        data.put("Branche", branch.isBlank() ? "(detached)" : branch);
        data.put("Modifs non commitées", status.isBlank() ? "Non" : "Oui");
        data.put("Vs remote", vsRemote);
        data.put("Branches contenant ce commit", branches.isEmpty() ? "?" : String.join(", ", branches));

        String url = toWebUrl(remoteUrl);
        return List.of(url != null
            ? ExtractorWidget.data("git", "Git", url, data)
            : ExtractorWidget.data("git", "Git", data));
    }

    private static String formatDate(String isoDate) {
        if (isoDate == null || isoDate.isBlank()) return "?";
        try {
            return OffsetDateTime.parse(isoDate).format(DATE_FORMAT);
        } catch (Exception e) {
            return isoDate;
        }
    }

    // git@github.com:owner/repo.git -> https://github.com/owner/repo
    // https://github.com/owner/repo.git -> https://github.com/owner/repo
    private static String toWebUrl(String remoteUrl) {
        if (remoteUrl == null || remoteUrl.isBlank()) return null;
        Matcher sshMatch = SSH_REMOTE.matcher(remoteUrl);
        if (sshMatch.matches()) {
            return "https://" + sshMatch.group(1) + "/" + sshMatch.group(2);
        }
        return remoteUrl.endsWith(".git") ? remoteUrl.substring(0, remoteUrl.length() - 4) : remoteUrl;
    }

    private static String git(Path cwd, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(cwd.toFile());
        Process process = pb.start();

        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException(!stderr.isBlank() ? stderr.trim() : "git " + String.join(" ", args) + ": exit " + exitCode);
        }
        return stdout.trim();
    }
}
