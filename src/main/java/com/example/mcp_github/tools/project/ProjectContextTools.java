package com.example.mcp_github.tools.project;

import java.io.File;
import java.util.Optional;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.example.mcp_github.service.MemoryService;
import com.example.mcp_github.service.ProjectContextService;
import com.example.mcp_github.service.ProjectContextService.GitProjectContext;

/**
 * MCP Tools — Project Context domain.
 *
 * Gère la détection automatique du projet GitHub actif depuis le workspace VS
 * Code / Antigravity, et la persistance du projet en mémoire JSON.
 *
 * Ordre de priorité pour la détection : 1. Mémoire persistante (memory.json) →
 * retour immédiat si présente 2. Variable d'env VSCODE_WORKSPACE → injectée par
 * VS Code via mcp.json 3. Variable d'env WORKSPACE_PATH → fallback manuel 4.
 * user.dir → dernier recours JVM
 */
@Component
public class ProjectContextTools {

    private final ProjectContextService projectContextService;
    private final MemoryService memoryService;

    public ProjectContextTools(ProjectContextService projectContextService,
            MemoryService memoryService) {
        this.projectContextService = projectContextService;
        this.memoryService = memoryService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INIT — appelé AUTOMATIQUEMENT à chaque début de conversation
    // ─────────────────────────────────────────────────────────────────────────
    @Tool(
            name = "initializeProject",
            description = """
            MANDATORY: Call this tool automatically at the very beginning of EVERY conversation,
            before any other tool. No exceptions.

            Logic:
            1. Reads persistent memory (memory.json on disk).
               → If a project is already saved: returns it immediately. Done.
            2. If memory is empty: auto-detects from VSCODE_WORKSPACE environment variable
               (injected by VS Code / Antigravity via mcp.json), reads .git/config
               to extract owner, repo and current branch, then saves to memory.
               → If detected: saves and returns.
            3. If nothing is detected: asks user to call setCurrentProject(owner, repo, branch)
               or detectProjectFromPath(path) manually.

            Result: the AI always knows the active GitHub project, even after
            restarting VS Code or the MCP server.
            """
    )
    public String initializeProject() {

        // ── 1. Mémoire persistante (priorité absolue) ─────────────────────────
        String owner = memoryService.recall("current_owner");
        String repo = memoryService.recall("current_repo");
        String branch = memoryService.recall("current_branch");
        String path = memoryService.recall("current_path");

        if (owner != null && repo != null) {
            String diskWarning = verifyProjectStillExists(path);
            return """
                    ✅ Projet actif chargé depuis la mémoire persistante :

                    Owner  : %s
                    Repo   : %s
                    Branch : %s
                    Path   : %s
                    %s
                    🔗 https://github.com/%s/%s
                    """.formatted(
                    owner, repo, branch,
                    path != null ? path : "N/A",
                    diskWarning,
                    owner, repo);
        }

        // ── 2. Auto-détection via VSCODE_WORKSPACE ────────────────────────────
        Optional<GitProjectContext> ctx = projectContextService.detectFromCurrentDirectory();

        if (ctx.isEmpty()) {
            return """
                    ⚠️ Aucun projet GitHub détecté automatiquement.

                    Solutions :
                    • Assure-toi d'avoir un dossier Git ouvert dans VS Code
                    • Appelle : setCurrentProject(owner, repo, branch)
                    • Appelle : detectProjectFromPath("C:\\\\chemin\\\\vers\\\\projet")
                    """;
        }

        return saveAndConfirm(ctx.get(), "auto-détecté depuis le workspace VS Code");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DÉTECTION
    // ─────────────────────────────────────────────────────────────────────────
    @Tool(
            name = "detectCurrentProject",
            description = """
            Détecte automatiquement le projet GitHub actif depuis le workspace VS Code ouvert.
            Lit la variable d'environnement VSCODE_WORKSPACE (injectée par VS Code via mcp.json),
            puis parse .git/config pour extraire owner, repo et branche courante.
            Sauvegarde le résultat en mémoire persistante (memory.json).
            Appelle cet outil si initializeProject n'a pas trouvé de projet en mémoire.
            """
    )
    public String detectCurrentProject() {
        Optional<GitProjectContext> ctx = projectContextService.detectFromCurrentDirectory();

        if (ctx.isEmpty()) {
            return """
                    ❌ Aucun projet Git détecté dans le workspace VS Code.

                    Vérifications :
                    • Le dossier ouvert dans VS Code contient-il un fichier .git ?
                    • Sinon : git init && git remote add origin https://github.com/owner/repo.git
                    • Ou utilise setCurrentProject pour définir le projet manuellement.
                    """;
        }

        return saveAndConfirm(ctx.get(), "auto-détecté depuis le workspace VS Code");
    }

    @Tool(
            name = "detectProjectFromPath",
            description = """
            Détecte le projet GitHub depuis un chemin de dossier spécifique fourni par l'utilisateur.
            Utile quand le projet n'est pas dans le workspace VS Code courant, ou pour changer de projet.
            Parse .git/config du dossier cible et sauvegarde en mémoire persistante.
            """
    )
    public String detectProjectFromPath(
            @ToolParam(description = "Chemin absolu du dossier projet. Exemple : C:\\Users\\user\\projets\\mon-app") String path) {

        if (path == null || path.isBlank()) {
            return "❌ Chemin invalide. Fournis un chemin absolu vers un dossier contenant un .git";
        }

        Optional<GitProjectContext> ctx = projectContextService.detectFromDirectory(path);

        if (ctx.isEmpty()) {
            return "❌ Aucun projet Git trouvé dans : " + path
                    + "\n   Vérifie que ce dossier contient un sous-dossier .git";
        }

        return saveAndConfirm(ctx.get(), "détecté depuis le chemin : " + path);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LECTURE
    // ─────────────────────────────────────────────────────────────────────────
    @Tool(
            name = "getCurrentProject",
            description = """
            Affiche le projet GitHub actuellement actif en mémoire persistante.
            Indique owner, repo, branche, chemin local et URL GitHub.
            Signale si le dossier local n'est plus accessible sur le disque.
            """
    )
    public String getCurrentProject() {
        String owner = memoryService.recall("current_owner");
        String repo = memoryService.recall("current_repo");
        String branch = memoryService.recall("current_branch");
        String path = memoryService.recall("current_path");

        if (owner == null || repo == null) {
            return """
                    ⚠️ Aucun projet actif en mémoire persistante.
                    Appelle initializeProject ou detectCurrentProject.
                    """;
        }

        String diskWarning = verifyProjectStillExists(path);

        return """
                📦 Projet actif :

                Owner  : %s
                Repo   : %s
                Branch : %s
                Path   : %s
                %s
                🔗 URL : https://github.com/%s/%s
                """.formatted(
                owner, repo, branch,
                path != null ? path : "N/A",
                diskWarning,
                owner, repo);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MISE À JOUR
    // ─────────────────────────────────────────────────────────────────────────
    @Tool(
            name = "setCurrentProject",
            description = """
            Définit manuellement le projet GitHub actif (owner, repo, branch).
            Sauvegarde immédiatement en mémoire persistante (memory.json).
            Ce projet sera retrouvé automatiquement même après redémarrage de VS Code.
            Utilise cet outil si la détection automatique échoue ou pour forcer un projet précis.
            """
    )
    public String setCurrentProject(
            @ToolParam(description = "Nom d'utilisateur GitHub du propriétaire du repo") String owner,
            @ToolParam(description = "Nom du repository GitHub") String repo,
            @ToolParam(description = "Nom de la branche active (ex: main, develop, feature/login)") String branch) {

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
                ✅ Projet actif défini manuellement et sauvegardé !

                Owner  : %s
                Repo   : %s
                Branch : %s

                🔗 https://github.com/%s/%s

                💾 Ce projet sera retrouvé automatiquement au prochain démarrage.
                """.formatted(owner, repo, branch, owner, repo);
    }

    @Tool(
            name = "refreshCurrentBranch",
            description = """
            Relit le fichier .git/HEAD du projet actif pour mettre à jour la branche courante en mémoire.
            Appelle cet outil après un 'git checkout' ou 'git switch' pour que l'IA connaisse
            la nouvelle branche sans avoir à tout redétecter.
            """
    )
    public String refreshCurrentBranch() {
        String path = memoryService.recall("current_path");
        String owner = memoryService.recall("current_owner");
        String repo = memoryService.recall("current_repo");

        if (owner == null || repo == null) {
            return "⚠️ Aucun projet actif en mémoire. Appelle initializeProject d'abord.";
        }

        if (path == null || path.equals("manuel")) {
            return """
                    ⚠️ Projet défini manuellement sans chemin local.
                    La branche ne peut pas être auto-rafraîchie.
                    Utilise setCurrentProject pour mettre à jour la branche manuellement.
                    """;
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

        return """
                🔄 Branche mise à jour !

                Ancienne branche : %s
                Nouvelle branche : %s

                Projet : %s/%s
                """.formatted(oldBranch, newBranch, owner, repo);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SUPPRESSION
    // ─────────────────────────────────────────────────────────────────────────
    @Tool(
            name = "clearCurrentProject",
            description = """
            Efface le projet actif de la mémoire persistante (memory.json).
            Après cet appel, initializeProject relancera la détection automatique
            depuis VSCODE_WORKSPACE au prochain démarrage.
            """
    )
    public String clearCurrentProject() {
        String owner = memoryService.recall("current_owner");
        String repo = memoryService.recall("current_repo");

        memoryService.forget("current_owner");
        memoryService.forget("current_repo");
        memoryService.forget("current_branch");
        memoryService.forget("current_path");

        if (owner == null) {
            return "ℹ️ Aucun projet actif en mémoire. Rien à effacer.";
        }

        return "🗑️ Projet " + owner + "/" + repo + " effacé de la mémoire persistante.";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS PRIVÉS
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Sauvegarde le projet détecté en mémoire persistante et retourne un
     * message de confirmation formaté.
     */
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

                💾 Sauvegardé en mémoire persistante — retrouvé automatiquement au prochain démarrage.
                """.formatted(
                source,
                project.owner(), project.repo(),
                project.branch(), project.projectPath(),
                project.owner(), project.repo());
    }

    /**
     * Vérifie si le dossier .git est toujours accessible sur le disque.
     * Retourne un avertissement si le projet a été déplacé ou supprimé.
     */
    private String verifyProjectStillExists(String path) {
        if (path == null || path.isBlank() || path.equals("manuel")) {
            return "";
        }
        File gitDir = new File(path, ".git");
        if (!gitDir.exists()) {
            return "⚠️  Attention : le dossier local n'est plus accessible.\n"
                    + "   Chemin introuvable : " + path + "\n"
                    + "   Appelle detectProjectFromPath ou setCurrentProject.\n";
        }
        return "";
    }
}
