package com.example.mcp_github.service;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

@Service
public class ProjectContextService {

    private static final Pattern HTTPS_PATTERN
            = Pattern.compile("url\\s*=\\s*https://github\\.com/([^/]+)/([^\\s\\.]+)");

    private static final Pattern SSH_PATTERN
            = Pattern.compile("url\\s*=\\s*git@github\\.com:([^/]+)/([^\\s\\.]+)");

    public Optional<GitProjectContext> detectFromCurrentDirectory() {
        // Essaie d'abord user.dir
        Optional<GitProjectContext> result = detectFromDirectory(System.getProperty("user.dir"));

        // Si pas trouvé, essaie le Desktop
        if (result.isEmpty()) {
            String desktop = System.getProperty("user.home") + "\\Desktop";
            File desktopDir = new File(desktop);
            if (desktopDir.exists()) {
                // Parcourt tous les dossiers du Desktop
                File[] folders = desktopDir.listFiles(File::isDirectory);
                if (folders != null) {
                    for (File folder : folders) {
                        result = detectFromDirectory(folder.getAbsolutePath());
                        if (result.isPresent()) {
                            return result;
                        }
                    }
                }
            }
        }
        return result;
    }

    public Optional<GitProjectContext> detectFromDirectory(String path) {
        if (path == null) {
            return Optional.empty();
        }

        File gitConfig = new File(path + "/.git/config");

        if (!gitConfig.exists()) {
            File parent = new File(path).getParentFile();
            if (parent != null && !parent.getPath().equals(path)) {
                return detectFromDirectory(parent.getAbsolutePath());
            }
            return Optional.empty();
        }

        try {
            String content = Files.readString(gitConfig.toPath());
            return parseGitConfig(content, path);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<GitProjectContext> parseGitConfig(String content, String projectPath) {
        for (Pattern pattern : List.of(HTTPS_PATTERN, SSH_PATTERN)) {
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                String owner = matcher.group(1);
                String repo = matcher.group(2).replace(".git", "");
                String branch = readCurrentBranch(projectPath);
                return Optional.of(new GitProjectContext(owner, repo, branch, projectPath));
            }
        }
        return Optional.empty();
    }

    private String readCurrentBranch(String projectPath) {
        try {
            File headFile = new File(projectPath + "/.git/HEAD");
            String content = Files.readString(headFile.toPath()).trim();

            if (content.startsWith("ref: refs/heads/")) {
                return content.substring("ref: refs/heads/".length());
            }
            if (content.length() >= 7) {
                return "detached@" + content.substring(0, 7);
            }
        } catch (Exception ignored) {
        }

        return "main";
    }

    public record GitProjectContext(
            String owner,
            String repo,
            String branch,
            String projectPath
            ) {

    }
}
