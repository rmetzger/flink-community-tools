package de.robertmetzger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PullUpdater {
    private static final Logger LOG = LoggerFactory.getLogger(PullUpdater.class);

    private static final String LABEL_COLOR = "175fb7";
    private static final String COMPONENT_PREFIX = "component=";
    private final String repoName;
    private final GHRepository readRepo;
    private final GHRepository writeRepo;

    private GitHub readGitHub;
    private DiskCachedJira jira;


    public PullUpdater(GitHub readGitHub, GitHub writeGitHub, DiskCachedJira jira, String repo) throws
        IOException {
        this.readGitHub = readGitHub;
        this.jira = jira;
        this.repoName = repo;

        readRepo = readGitHub.getRepository(this.repoName);
        writeRepo = writeGitHub.getRepository(this.repoName);
    }

    public void checkPullRequests() throws IOException, DiskCachedJira.JiraException {
        List<GHPullRequest> pullRequests = readRepo.getPullRequests(GHIssueState.ALL);
        LOG.debug("Getting pull requests {}", readGitHub.getRateLimit());

        for (GHPullRequest pullRequest : pullRequests) {
            String jiraId = extractJiraId(pullRequest.getTitle());
            if(jiraId == null) {
                continue;
            }
            List<String> jiraComponents = normalizeComponents(jira.getComponents(jiraId));
            List<GHLabel> requiredLabels = getComponentLabels(jiraComponents);
            Collection<GHLabel> existingPRLabels = pullRequest.getLabels().stream().filter(l -> l.getName().startsWith(COMPONENT_PREFIX)).collect(
                Collectors.toList());
            LOG.debug("Getting existing labels {}", readGitHub.getRateLimit());
            Collection<GHLabel> correctLabels = CollectionUtils.intersection(
                requiredLabels,
                existingPRLabels);
            existingPRLabels.removeAll(correctLabels);
            Collection<GHLabel> toRemove = existingPRLabels;

            requiredLabels.removeAll(correctLabels);
            Collection<GHLabel> toAdd = requiredLabels;

            if(toRemove.size() > 0 || toAdd.size() > 0 ) {
                GHPullRequest writablePR = writeRepo.getPullRequest(pullRequest.getNumber());
                writablePR.addLabels(toAdd);
                writablePR.removeLabels(toRemove);
                LOG.info("Updating PR '{}' adding labels '{}', removing '{}'", pullRequest.getTitle(), toAdd, toRemove);
            }
        }
    }

    private List<GHLabel> getComponentLabels(List<String> jiraComponents) throws
        IOException {
        List<GHLabel> labels = new ArrayList<>(jiraComponents.size());
        for(String label: jiraComponents) {
            try {
                labels.add(createOrGetLabel(label));
            } catch(IOException e) {
                throw new IOException("Error while getting label " + label, e);
            }

        }
        return labels;
    }

    private GHLabel createOrGetLabel(String labelString) throws IOException {
        try {
            return readRepo.getLabel(labelString);
        } catch(FileNotFoundException noLabel) {
            //  LOG.debug("Label '{}' did not exist", labelString, noLabel);
            LOG.info("Label '{}' did not exist, creating it", labelString);
            return writeRepo.createLabel(labelString, LABEL_COLOR);
        }
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
                    .map(c -> COMPONENT_PREFIX + c.replaceAll(" ", ""))
                    .collect(Collectors.toList());
    }
}
