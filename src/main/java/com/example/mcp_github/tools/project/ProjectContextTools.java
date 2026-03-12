package com.example.mcp_github.tools.project;

import java.io.File;
import java.util.Optional;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.example.mcp_github.service.MemoryService;
import com.example.mcp_github.service.ProjectContextService;
import com.example.mcp_github.service.ProjectContextService.GitProjectContext;

@Component
public class ProjectContextTools {

    private final ProjectContextService projectContextService;
    private final MemoryService memoryService;

    public ProjectContextTools(ProjectContextService projectContextService,
            MemoryService memoryService) {
        this.projectContextService = projectContextService;
        this.memoryService = memoryService;
    }

    @Tool(
            name = "initializeProject",
            description = """
        MANDATORY: Call this tool automatically at the very beginning of EVERY conversation.
        Auto-detects the active GitHub project from the current Antigravity workspace.
        Re-detects automatically if the workspace has changed since last session.
        Falls back to persistent memory if no project is detected.
        """
    )
    public String initializeProject() {

        // ── 1. Workspace actuel ───────────────────────────────────────────────
        String currentWorkspace = System.getenv("PROJECT_PATH");
        if (currentWorkspace == null || currentWorkspace.isBlank()) {
            currentWorkspace = System.getenv("VSCODE_WORKSPACE");
        }

        // ── 2. Ce qui est en mémoire ──────────────────────────────────────────
        String savedPath = memoryService.recall("current_path");
        String savedOwner = memoryService.recall("current_owner");
        String savedRepo = memoryService.recall("current_repo");
        String savedBranch = memoryService.recall("current_branch");

        // ── 3. Même projet → retour immédiat ─────────────────────────────────
        if (savedOwner != null && savedPath != null
                && currentWorkspace != null
                && savedPath.equalsIgnoreCase(currentWorkspace)) {
            return """
                ✅ Projet actif (auto-détecté au démarrage) :

                Owner  : %s
                Repo   : %s
                Branch : %s
                Path   : %s

                🔗 https://github.com/%s/%s
                """.formatted(savedOwner, savedRepo, savedBranch,
                    savedPath, savedOwner, savedRepo);
        }

        // ── 4. Workspace changé → rédetection automatique ────────────────────
        if (currentWorkspace != null && !currentWorkspace.isBlank()) {
            Optional<GitProjectContext> ctx = projectContextService.detectFromDirectory(currentWorkspace);
            if (ctx.isPresent()) {
                memoryService.remember("current_owner", ctx.get().owner());
                memoryService.remember("current_repo", ctx.get().repo());
                memoryService.remember("current_branch", ctx.get().branch());
                memoryService.remember("current_path", currentWorkspace);

                return """
                    🔄 Nouveau projet détecté automatiquement !

                    Owner  : %s
                    Repo   : %s
                    Branch : %s
                    Path   : %s

                    🔗 https://github.com/%s/%s
                    """.formatted(ctx.get().owner(), ctx.get().repo(),
                        ctx.get().branch(), currentWorkspace,
                        ctx.get().owner(), ctx.get().repo());
            }
        }

        // ── 5. Aucun projet détecté → fallback sur la mémoire ────────────────
        if (savedOwner != null && savedRepo != null) {
            return """
                ⚠️ Workspace non détecté — projet chargé depuis la mémoire :

                Owner  : %s
                Repo   : %s
                Branch : %s
                Path   : %s

                🔗 https://github.com/%s/%s
                """.formatted(savedOwner, savedRepo,
                    savedBranch != null ? savedBranch : "main",
                    savedPath != null ? savedPath : "N/A",
                    savedOwner, savedRepo);
        }

        // ── 6. Vraiment rien → message d'erreur ──────────────────────────────
        return """
            ⚠️ Aucun projet actif détecté.
            Solutions :
            • Ouvrir un folder contenant un .git dans Antigravity
            • Ou appeler : setCurrentProject(owner, repo, branch)
            """;
    }

    @Tool(
            name = "detectCurrentProject",
            description = """
            Détecte automatiquement le projet GitHub actif depuis le workspace antigravity ouvert.
            """
    )
    public String detectCurrentProject() {
        Optional<GitProjectContext> ctx = projectContextService.detectFromCurrentDirectory();

        if (ctx.isEmpty()) {
            return """
                    ❌ Aucun projet Git détecté dans le workspace antigravity.
                    """;
        }

        return saveAndConfirm(ctx.get(), "auto-détecté depuis le workspace antigravity");
    }

    @Tool(
            name = "detectProjectFromPath",
            description = """
            Détecte le projet GitHub depuis un chemin de dossier spécifique fourni par l'utilisateur.
            """
    )
    public String detectProjectFromPath(
            @ToolParam(description = "Chemin absolu du dossier projet.") String path) {

        if (path == null || path.isBlank()) {
            return "❌ Chemin invalide.";
        }

        Optional<GitProjectContext> ctx = projectContextService.detectFromDirectory(path);

        if (ctx.isEmpty()) {
            return "❌ Aucun projet Git trouvé dans : " + path
                    + "\n   Vérifie que ce dossier contient un sous-dossier .git";
        }

        return saveAndConfirm(ctx.get(), "détecté depuis le chemin : " + path);
    }

    @Tool(
            name = "getCurrentProject",
            description = "Affiche le projet GitHub actuellement actif en mémoire persistante."
    )
    public String getCurrentProject() {
        String owner = memoryService.recall("current_owner");
        String repo = memoryService.recall("current_repo");
        String branch = memoryService.recall("current_branch");
        String path = memoryService.recall("current_path");

        if (owner == null || repo == null) {
            return "⚠️ Aucun projet actif en mémoire.";
        }

        return """
                📦 Projet actif :

                Owner  : %s
                Repo   : %s
                Branch : %s
                Path   : %s

                🔗 URL : https://github.com/%s/%s
                """.formatted(owner, repo, branch, path, owner, repo);
    }

    @Tool(
            name = "setCurrentProject",
            description = "Définit manuellement le projet GitHub actif (owner, repo, branch)."
    )
    public String setCurrentProject(
            @ToolParam(description = "Nom d'utilisateur GitHub du propriétaire") String owner,
            @ToolParam(description = "Nom du repository GitHub") String repo,
            @ToolParam(description = "Nom de la branche active") String branch) {

        if (owner == null || owner.isBlank()) {
            return "❌ Le paramètre 'owner' est requis.";
        }
        if (repo == null || repo.isBlank()) {
            return "❌ Le paramètre 'repo' est requis.";
        }
        if (branch == null || branch.isBlank()) {
            branch = "main";
        }

        memoryService.remember("current_owner", owner.trim());
        memoryService.remember("current_repo", repo.trim());
        memoryService.remember("current_branch", branch.trim());
        memoryService.remember("current_path", "manuel");

        return """
                ✅ Projet défini manuellement !

                Owner  : %s
                Repo   : %s
                Branch : %s

                🔗 https://github.com/%s/%s
                """.formatted(owner, repo, branch, owner, repo);
    }

    @Tool(
            name = "refreshCurrentBranch",
            description = "Relit .git/HEAD pour mettre à jour la branche courante en mémoire."
    )
    public String refreshCurrentBranch() {
        String path = memoryService.recall("current_path");
        String owner = memoryService.recall("current_owner");
        String repo = memoryService.recall("current_repo");

        if (owner == null || repo == null) {
            return "⚠️ Aucun projet actif en mémoire.";
        }

        if (path == null || path.equals("manuel")) {
            return "⚠️ Projet défini manuellement — branche non rafraîchissable automatiquement.";
        }

        Optional<GitProjectContext> ctx = projectContextService.detectFromDirectory(path);
        if (ctx.isEmpty()) {
            return "❌ Impossible de relire le projet depuis : " + path;
        }

        String newBranch = ctx.get().branch();
        String oldBranch = memoryService.recall("current_branch");
        memoryService.remember("current_branch", newBranch);

        if (newBranch.equals(oldBranch)) {
            return "✅ Branche inchangée : " + newBranch;
        }

        return "🔄 Branche mise à jour : %s → %s".formatted(oldBranch, newBranch);
    }

    @Tool(
            name = "clearCurrentProject",
            description = "Efface le projet actif de la mémoire persistante."
    )
    public String clearCurrentProject() {
        String owner = memoryService.recall("current_owner");
        String repo = memoryService.recall("current_repo");

        memoryService.forget("current_owner");
        memoryService.forget("current_repo");
        memoryService.forget("current_branch");
        memoryService.forget("current_path");

        if (owner == null) {
            return "ℹ️ Aucun projet actif en mémoire.";
        }

        return "🗑️ Projet " + owner + "/" + repo + " effacé de la mémoire.";
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────
    private String saveAndConfirm(GitProjectContext project, String source) {
        memoryService.remember("current_owner", project.owner());
        memoryService.remember("current_repo", project.repo());
        memoryService.remember("current_branch", project.branch());
        memoryService.remember("current_path", project.projectPath());

        return """
                ✅ Projet %s !

                Owner  : %s
                Repo   : %s
                Branch : %s
                Path   : %s

                🔗 https://github.com/%s/%s
                """.formatted(source,
                project.owner(), project.repo(),
                project.branch(), project.projectPath(),
                project.owner(), project.repo());
    }

    private String verifyProjectStillExists(String path) {
        if (path == null || path.isBlank() || path.equals("manuel")) {
            return "";
        }
        File gitDir = new File(path, ".git");
        if (!gitDir.exists()) {
            return "⚠️ Dossier local introuvable : " + path + "\n";
        }
        return "";
    }
}
