package de.robertmetzger.flink.community.flinkbot.checks;

import de.robertmetzger.flink.community.flinkbot.PullRequestCheck;
import org.kohsuke.github.GHObject;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestFileDetail;

import java.util.List;

/**
 * Checks if changes to a pom file were made
 */
public class PomChangesCheck implements PullRequestCheck {

    @Override
    public String runCheck(GHPullRequest pullRequest, List<GHObject> comments) {
        int pomFiles = 0;
        for(GHPullRequestFileDetail file: pullRequest.listFiles()) {
            if(file.getFilename().endsWith("pom.xml")) {
                pomFiles++;
            }
        }
        if(pomFiles > 0) {
            return "**" + pomFiles + " pom.xml files were touched**: Check for build and licensing issues.";
        } else {
            return null;
        }
    }
}
