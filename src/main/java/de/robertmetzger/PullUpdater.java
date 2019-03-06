package de.robertmetzger;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PullUpdater {
    private GitHub readGitHub;
    private CachedJira jira;
    private static Logger LOG = LoggerFactory.getLogger(PullUpdater.class);
    private static String COMPONENT_PREFIX = "component=";

    public PullUpdater(GitHub readGitHub, CachedJira jira) {
        this.readGitHub = readGitHub;
        this.jira = jira;
    }

    public void checkPullRequests() throws IOException {
        List<GHPullRequest> pullRequests = readGitHub.getRepository(
            "apache/flink").getPullRequests(GHIssueState.ALL);

        for (GHPullRequest pullRequest : pullRequests) {
            String jiraId = extractJiraId(pullRequest.getTitle());
            List<String> jiraComponents = normalizeComponents(jira.getComponents(jiraId));
            List<GHLabel> requiredLabels = getComponentLabels(jiraComponents);
            Collection<GHLabel> existingPRLabels = pullRequest.getLabels().stream().filter(l -> l.getName().startsWith(COMPONENT_PREFIX)).collect(
                Collectors.toList());
            Collection<GHLabel> correctLabels = CollectionUtils.intersection(
                requiredLabels,
                existingPRLabels);
            existingPRLabels.removeAll(correctLabels);
            Collection<GHLabel> toRemove = existingPRLabels;
            pullRequest.removeLabels(toRemove);

            requiredLabels.removeAll(correctLabels);
            Collection<GHLabel> toAdd = requiredLabels;
            pullRequest.addLabels(toAdd);

            if(toRemove.size() > 0 || toAdd.size() > 0 ) {
                LOG.info("Updating PR '{}' adding labels '{}', removing '{}'", pullRequest.getTitle(), toAdd, toRemove);
            }
        }
    }

    private List<GHLabel> getComponentLabels(List<String> jiraComponents) {

    }

    private static Pattern pattern = Pattern.compile(".*(FLINK-[0-9]+).*");
    public String extractJiraId(String title) {
        Matcher matcher = pattern.matcher(title);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public List<String> normalizeComponents(List<String> components) {
        return components
                    .stream()
                    .map(c -> c.replaceAll(" ", "").toLowerCase())
                    .collect(Collectors.toList());
    }
}
