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
            if(file.getFilename().endsWith(".md")) {
                mdFiles++;
            }
            if(file.getFilename().endsWith(".zh.md")) {
                zhMdFiles++;
            }
        }
        if(mdFiles == 0) {
            return "No documentation files were touched! Remember to keep the Flink docs up to date!";
        }
        if(mdFiles > 0 && zhMdFiles == 0) {
            return "Documentation files were touched, but no `.zh.md` files: Update Chinese documentation or file Jira ticket.";
        } else {
            return null;
        }
    }
}
