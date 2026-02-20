package com.example.mcp_github.tools.project;

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

    @Tool(name = "detectCurrentProject",
            description = "Détecte automatiquement le projet GitHub courant en lisant .git/config du répertoire de travail. Extrait owner, repo et branch courante, puis les sauvegarde en mémoire comme projet actif.")
    public String detectCurrentProject() {
        Optional<GitProjectContext> ctx = projectContextService.detectFromCurrentDirectory();

        if (ctx.isEmpty()) {
            return """
                    ❌ Aucun projet Git détecté.

                    Solutions :
                    • git init && git remote add origin https://github.com/owner/repo.git
                    • Ou utilise setCurrentProject pour définir manuellement
                    """;
        }

        return saveAndConfirm(ctx.get());
    }

    @Tool(name = "detectProjectFromPath",
            description = "Détecte le projet GitHub depuis un chemin de dossier spécifique.")
    public String detectProjectFromPath(
            @ToolParam(description = "Chemin absolu du dossier projet, ex: C:\\Users\\user\\Desktop\\MonProjet") String path) {
        Optional<GitProjectContext> ctx = projectContextService.detectFromDirectory(path);

        if (ctx.isEmpty()) {
            return "❌ Aucun projet Git trouvé dans : " + path;
        }

        return saveAndConfirm(ctx.get());
    }

    @Tool(name = "setCurrentProject",
            description = "Définit manuellement le projet GitHub actif (owner, repo, branch).")
    public String setCurrentProject(
            @ToolParam(description = "Nom d'utilisateur GitHub du propriétaire") String owner,
            @ToolParam(description = "Nom du repository") String repo,
            @ToolParam(description = "Nom de la branche (ex: main, develop)") String branch) {

        memoryService.remember("current_owner", owner);
        memoryService.remember("current_repo", repo);
        memoryService.remember("current_branch", branch);
        memoryService.remember("current_path", "manuel");

        return """
                ✅ Projet actif défini manuellement !

                Owner  : %s
                Repo   : %s
                Branch : %s
                """.formatted(owner, repo, branch);
    }

    @Tool(name = "getCurrentProject",
            description = "Affiche le projet GitHub actuellement actif en mémoire.")
    public String getCurrentProject() {
        String owner = memoryService.recall("current_owner");
        String repo = memoryService.recall("current_repo");
        String branch = memoryService.recall("current_branch");
        String path = memoryService.recall("current_path");

        if (owner == null || repo == null) {
            return """
                    ⚠️ Aucun projet actif en mémoire.
                    Lance detectCurrentProject pour auto-détecter.
                    """;
        }

        return """
                📦 Projet actif :

                Owner  : %s
                Repo   : %s
                Branch : %s
                Path   : %s

                🔗 URL : https://github.com/%s/%s
                """.formatted(owner, repo, branch,
                path != null ? path : "N/A",
                owner, repo);
    }

    @Tool(name = "clearCurrentProject",
            description = "Efface le projet actif de la mémoire.")
    public String clearCurrentProject() {
        memoryService.forget("current_owner");
        memoryService.forget("current_repo");
        memoryService.forget("current_branch");
        memoryService.forget("current_path");
        return "🗑️ Projet actif effacé de la mémoire.";
    }

    private String saveAndConfirm(GitProjectContext project) {
        memoryService.remember("current_owner", project.owner());
        memoryService.remember("current_repo", project.repo());
        memoryService.remember("current_branch", project.branch());
        memoryService.remember("current_path", project.projectPath());

        return """
                ✅ Projet détecté et défini comme projet actif !

                Owner  : %s
                Repo   : %s
                Branch : %s
                Path   : %s

                🔗 URL : https://github.com/%s/%s
                """.formatted(
                project.owner(), project.repo(),
                project.branch(), project.projectPath(),
                project.owner(), project.repo());
    }
}
