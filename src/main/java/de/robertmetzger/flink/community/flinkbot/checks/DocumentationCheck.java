package de.robertmetzger.flink.community.flinkbot.checks;

import de.robertmetzger.flink.community.flinkbot.PullRequestCheck;
import org.kohsuke.github.GHObject;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestFileDetail;

import java.util.List;

public class DocumentationCheck implements PullRequestCheck {
    @Override
    public String runCheck(GHPullRequest pullRequest, List<GHObject> comments) {
        int mdFiles = 0;
        int zhMdFiles = 0;
        List<GHPullRequestFileDetail> files = pullRequest.listFiles().asList();
        for(GHPullRequestFileDetail file: files) {
            if (!file.getFilename().endsWith(".md")) {
                continue;
            }
            if(file.getFilename().contains("docs/content/")) {
                mdFiles++;
            }
            if(file.getFilename().contains("docs/content.zh/")) {
                zhMdFiles++;
            }
        }
        if(mdFiles == 0) {
            return "No documentation files were touched! Remember to keep the Flink docs up to date!";
        }
        if(mdFiles > 0 && zhMdFiles == 0) {
            return "Documentation files were touched, but no `docs/content.zh/` files: Update Chinese documentation or file Jira ticket.";
        } else {
            return null;
        }
    }
}
