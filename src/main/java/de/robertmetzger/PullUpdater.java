package de.robertmetzger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import okhttp3.Cache;
import org.apache.commons.collections4.CollectionUtils;
import org.kohsuke.github.GHDirection;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestQueryBuilder;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PullUpdater {
    private static final Logger LOG = LoggerFactory.getLogger(PullUpdater.class);

    private static final String LABEL_COLOR = "175fb7";
    private static final String COMPONENT_PREFIX = "component=";

    private final GitHub cachedGitHubForPulls;
    private final GitHub uncachedGitHubForWritingLabels;

    private final Cache gitHubForLabelsCache;
    private final GHRepository uncachedRepoForWritingLabels;
    private final GHRepository cachedRepoForPulls;
    private final GHRepository cachedRepoForLabels;

    private DiskCachedJira jira;


    public PullUpdater(Properties prop, DiskCachedJira jira, String repoName) throws
        IOException {
        this.jira = jira;

        cachedGitHubForPulls = Utils.getGitHub(
            prop.getProperty("gh.user"),
            prop.getProperty("gh.token"),
            prop.getProperty("main.pullCacheDir"),
            Integer.valueOf(prop.getProperty("main.cacheMB"))
        ).gitHub;

        this.cachedRepoForPulls = cachedGitHubForPulls.getRepository(repoName);

        Utils.GitHubWithCache ghForLabels = Utils.getGitHub(
            prop.getProperty("gh.user"),
            prop.getProperty("gh.token"),
            prop.getProperty("main.labelCacheDir"),
            Integer.valueOf(prop.getProperty("main.cacheMB"))
        );
        this.cachedRepoForLabels = ghForLabels.gitHub.getRepository(repoName);
        this.gitHubForLabelsCache = ghForLabels.cache;

        this.uncachedGitHubForWritingLabels = Utils.getGitHub(prop.getProperty("gh.write.user"),
            prop.getProperty("gh.write.token"),
            null,0
        ).gitHub;
        this.uncachedRepoForWritingLabels = uncachedGitHubForWritingLabels.getRepository(repoName);
    }

    public void checkPullRequests() throws IOException, DiskCachedJira.JiraException {
        try {
            LOG.info("Checking pull requests. GitHub API limits read: {}, write: {}", cachedGitHubForPulls.getRateLimit(), uncachedGitHubForWritingLabels.getRateLimit());
        } catch (IOException e) {
            LOG.warn("Error while getting rate limits", e);
        }

        /*
         * Statistics on March 14:
         * With empty cache:
         * Space on Disk: 14 MB
         * Rate limit usage: 266 requests
         * seconds elapsed: 360 seconds
         *
         * Against a full cache:
         * Rate limit usage: 266 requests
         * seconds elapsed: 360 seconds
         */
        // this is a very expensive call
        //List<GHPullRequest> pullRequests = cachedRepoForPulls.getPullRequests(GHIssueState.ALL);



        /*
         * Statistics on March 14:
         * With empty cache:
         * Space on Disk: 14 MB
         * Rate limit usage: 266 requests
         * seconds elapsed: 360 seconds
         *
         * Against a full cache:
         * Rate limit usage: 266 requests
         * seconds elapsed: 360 seconds
         */

        // Use a deterministic query (only the last page changes)
        // TODO: this doesn't work
        GHPullRequestQueryBuilder prQuery = cachedRepoForPulls.queryPullRequests();
        prQuery.state(GHIssueState.ALL);
        prQuery.sort(GHPullRequestQueryBuilder.Sort.CREATED);
        prQuery.direction(GHDirection.ASC);
        List<GHPullRequest> pullRequests = prQuery.list().asList();

        LOG.info("Retrieved " + pullRequests.size());
        try {
            LOG.info("Checking pull requests. GitHub API limits read: {}, write: {}", cachedGitHubForPulls.getRateLimit(), uncachedGitHubForWritingLabels.getRateLimit());
        } catch (IOException e) {
            LOG.warn("Error while getting rate limits", e);
        }


        for (GHPullRequest pullRequest : pullRequests) {
            String jiraId = extractJiraId(pullRequest.getTitle());
            if(jiraId == null) {
                continue;
            }
            List<String> jiraComponents = normalizeComponents(jira.getComponents(jiraId));
            List<GHLabel> requiredLabels = getComponentLabels(jiraComponents);
            Collection<GHLabel> existingPRLabels = pullRequest.getLabels().stream().filter(l -> l.getName().startsWith(COMPONENT_PREFIX)).collect(
                Collectors.toList());
            Collection<GHLabel> correctLabels = CollectionUtils.intersection(
                requiredLabels,
                existingPRLabels);
            existingPRLabels.removeAll(correctLabels);
            Collection<GHLabel> toRemove = existingPRLabels;

            requiredLabels.removeAll(correctLabels);
            Collection<GHLabel> toAdd = requiredLabels;

            if(toRemove.size() > 0 || toAdd.size() > 0 ) {
                GHPullRequest writablePR = uncachedRepoForWritingLabels.getPullRequest(pullRequest.getNumber());
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
            return cachedRepoForLabels.getLabel(labelString);
        } catch(FileNotFoundException noLabel) {
            //  LOG.debug("Label '{}' did not exist", labelString, noLabel);
            LOG.info("Label '{}' did not exist, creating it", labelString);
            gitHubForLabelsCache.evictAll(); // empty the cache for getting labels so that the newly created label can be found
            return uncachedRepoForWritingLabels.createLabel(labelString, LABEL_COLOR);
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
        if(components.size() == 0) {
            return Collections.singletonList(COMPONENT_PREFIX+"<none>");
        }
        return components
                    .stream()
                    .map(c -> COMPONENT_PREFIX + c.replaceAll(" ", ""))
                    .collect(Collectors.toList());
    }
}
