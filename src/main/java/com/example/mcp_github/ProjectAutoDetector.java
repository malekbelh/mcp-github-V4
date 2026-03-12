package com.example.mcp_github;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.example.mcp_github.service.MemoryService;
import com.example.mcp_github.service.ProjectContextService;

@Component
public class ProjectAutoDetector implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProjectAutoDetector.class);

    private final ProjectContextService projectContextService;
    private final MemoryService memoryService;

    public ProjectAutoDetector(ProjectContextService projectContextService,
            MemoryService memoryService) {
        this.projectContextService = projectContextService;
        this.memoryService = memoryService;
    }

    @Override
    public void run(ApplicationArguments args) {

        // ── 1. Récupère le workspace actuel ───────────────────────────────────
        String workspace = System.getenv("PROJECT_PATH");
        if (workspace == null || workspace.isBlank()) {
            workspace = System.getenv("VSCODE_WORKSPACE");
        }

        if (workspace == null || workspace.isBlank()) {
            log.warn("⚠️ Aucun chemin projet configuré (PROJECT_PATH ou VSCODE_WORKSPACE)");
            return;
        }

        // ── 2. Compare avec ce qui est en mémoire ────────────────────────────
        String savedPath = memoryService.recall("current_path");
        String savedOwner = memoryService.recall("current_owner");

        if (savedOwner != null && savedPath != null
                && savedPath.equalsIgnoreCase(workspace)) {
            log.warn("✅ Projet inchangé en mémoire : {} — path: {}", savedOwner, savedPath);
            return;
        }

        // ── 3. Projet différent → on rédetecte ───────────────────────────────
        log.warn("🔄 Workspace changé ({} → {}), rédetection...", savedPath, workspace);

        // ── 4. Essai via ProjectContextService (parse .git/config) ───────────
        var ctx = projectContextService.detectFromDirectory(workspace);
        if (ctx.isPresent()) {
            save(ctx.get().owner(), ctx.get().repo(), ctx.get().branch(), workspace);
            return;
        }

        // ── 5. Fallback — commandes git directes ─────────────────────────────
        log.warn("⚠️ Parse .git/config échoué, tentative via commandes git...");
        try {
            String remoteUrl = runGit(workspace, "git", "remote", "get-url", "origin");
            String branch = runGit(workspace, "git", "rev-parse", "--abbrev-ref", "HEAD");

            if (remoteUrl == null || remoteUrl.isBlank()) {
                log.warn("❌ Aucun remote 'origin' trouvé dans {}", workspace);
                return;
            }

            String[] parts = parseGitUrl(remoteUrl);
            if (parts == null) {
                log.warn("❌ URL remote non reconnue : {}", remoteUrl);
                return;
            }

            save(parts[0], parts[1], branch != null ? branch : "main", workspace);

        } catch (Exception e) {
            log.warn("❌ Erreur détection git : {}", e.getMessage());
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────
    private void save(String owner, String repo, String branch, String path) {
        memoryService.remember("current_owner", owner);
        memoryService.remember("current_repo", repo);
        memoryService.remember("current_branch", branch);
        memoryService.remember("current_path", path);
        log.warn("✅ Projet sauvegardé : {}/{} [{}]", owner, repo, branch);
    }

    private String runGit(String workdir, String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new java.io.File(workdir));
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                process.waitFor();
                return line != null ? line.trim() : null;
            }
        } catch (Exception e) {
            log.warn("Commande git échouée : {}", e.getMessage());
            return null;
        }
    }

    private String[] parseGitUrl(String url) {
        url = url.trim().replace(".git", "");

        // HTTPS : https://github.com/owner/repo
        if (url.contains("github.com/")) {
            String path = url.substring(url.indexOf("github.com/") + "github.com/".length());
            String[] parts = path.split("/");
            if (parts.length >= 2) {
                return new String[]{parts[0], parts[1]};
            }
        }

        // SSH : git@github.com:owner/repo
        if (url.contains("github.com:")) {
            String path = url.substring(url.indexOf("github.com:") + "github.com:".length());
            String[] parts = path.split("/");
            if (parts.length >= 2) {
                return new String[]{parts[0], parts[1]};
            }
        }

        return null;
    }
}
