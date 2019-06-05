package de.robertmetzger.flink.community.flinkbot;

import org.kohsuke.github.GHObject;
import org.kohsuke.github.GHPullRequest;

import java.util.List;

public interface PullRequestCheck {
    String runCheck(GHPullRequest pullRequest, List<GHObject> comments);
}
