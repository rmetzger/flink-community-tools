package de.robertmetzger.flink.community.flinkbot.checks;


import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.auth.AnonymousAuthenticationHandler;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import com.atlassian.util.concurrent.Promise;
import de.robertmetzger.flink.community.flinkbot.Flinkbot;
import de.robertmetzger.flink.community.flinkbot.PullRequestCheck;
import org.kohsuke.github.GHObject;
import org.kohsuke.github.GHPullRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AssignedJiraCheck implements PullRequestCheck {
    private static Logger LOG = LoggerFactory.getLogger(Flinkbot.class);

    private static Pattern pattern = Pattern.compile("(?i).*(FLINK-[0-9]+).*");
    private IssueRestClient issueClient;

    public AssignedJiraCheck() {

    }

    private IssueRestClient getIssueClient() {
        if(issueClient != null) {
            return this.issueClient;
        }
        LOG.info("Creating new JIRA REST client");
        AsynchronousJiraRestClientFactory factory = new AsynchronousJiraRestClientFactory();
        JiraRestClient restClient = null;
        try {
            restClient = factory.create(new URI("https://issues.apache.org/jira"), new AnonymousAuthenticationHandler());
        } catch (URISyntaxException e) {
            throw new RuntimeException("URI wrong", e);
        }
        this.issueClient = restClient.getIssueClient();
        return issueClient;
    }

    @Override
    public String runCheck(GHPullRequest pullRequest, List<GHObject> comments) {
        String prTitle = pullRequest.getTitle();
        String jiraId = extractJiraId(prTitle);
        if(jiraId == null && !prTitle.contains("hotfix")) {
            return "**Invalid pull request title: No valid Jira ID provided**";
        }
        // we've got a valid JIRA id: Check if it is assigned.
        try {
            Issue jiraIssue = getIssueClient().getIssue(jiraId).get();
            if(jiraIssue.getAssignee() == null) {
                return "**This pull request references an unassigned [Jira ticket](https://issues.apache.org/jira/browse/"+jiraId+").** " +
                        "According to the [code contribution guide](https://flink.apache.org/contributing/contribute-code.html), " +
                        "tickets need to be assigned before starting with the implementation work.";
            }
        } catch (Throwable e) {
            LOG.warn("Unable to get Jira issue " + jiraId, e);
            this.issueClient = null;
        }
        return null;
    }


    public static String extractJiraId(String title) {
        Matcher matcher = pattern.matcher(title);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        return null;
    }
}
