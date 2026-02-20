package com.example.mcp_github;

import java.util.Arrays;
import java.util.List;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.example.mcp_github.tools.actions.GitHubActionsTools;
import com.example.mcp_github.tools.branch.GitHubBranchTools;
import com.example.mcp_github.tools.commit.GitHubCommitTools;
import com.example.mcp_github.tools.file.GitHubFileTools;
import com.example.mcp_github.tools.issue.GitHubIssueTools;
import com.example.mcp_github.tools.memory.MemoryTools;
import com.example.mcp_github.tools.project.ProjectContextTools;
import com.example.mcp_github.tools.pullrequest.GitHubPullRequestTools;
import com.example.mcp_github.tools.release.GitHubReleaseTools;
import com.example.mcp_github.tools.repository.GitHubRepositoryTools;
import com.example.mcp_github.tools.social.GitHubSocialTools;
import com.example.mcp_github.tools.user.GitHubUserTools;

@SpringBootApplication
public class McpGithubApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpGithubApplication.class, args);
    }

    @Bean
    public List<ToolCallback> tools(
            GitHubRepositoryTools repositoryTools,
            GitHubCommitTools commitTools,
            GitHubBranchTools branchTools,
            GitHubIssueTools issueTools,
            GitHubPullRequestTools pullRequestTools,
            GitHubFileTools fileTools,
            GitHubUserTools userTools,
            GitHubReleaseTools releaseTools,
            GitHubActionsTools actionsTools,
            GitHubSocialTools socialTools,
            MemoryTools memoryTools,
            ProjectContextTools projectContextTools // ← 🆕
    ) {
        return Arrays.stream(new ToolCallback[][]{
            ToolCallbacks.from(repositoryTools),
            ToolCallbacks.from(commitTools),
            ToolCallbacks.from(branchTools),
            ToolCallbacks.from(issueTools),
            ToolCallbacks.from(pullRequestTools),
            ToolCallbacks.from(fileTools),
            ToolCallbacks.from(userTools),
            ToolCallbacks.from(releaseTools),
            ToolCallbacks.from(actionsTools),
            ToolCallbacks.from(socialTools),
            ToolCallbacks.from(memoryTools),
            ToolCallbacks.from(projectContextTools) // ← 🆕
        })
                .flatMap(Arrays::stream)
                .toList();
    }
}
