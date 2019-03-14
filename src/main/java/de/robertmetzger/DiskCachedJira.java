package de.robertmetzger;

import com.atlassian.jira.rest.client.IssueRestClient;
import com.atlassian.jira.rest.client.JiraRestClient;
import com.atlassian.jira.rest.client.auth.AnonymousAuthenticationHandler;
import com.atlassian.jira.rest.client.domain.BasicComponent;
import com.atlassian.jira.rest.client.domain.Issue;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClient;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DiskCachedJira {
    private static final Logger LOG = LoggerFactory.getLogger(DiskCachedJira.class);


    private final IssueRestClient issueClient;
    private final Cache cache;
    private final AsynchronousJiraRestClient restClient;


    public DiskCachedJira(String jiraUrl, Cache cache) throws URISyntaxException {
        final URI jiraServerUri = new URI(jiraUrl);

        this.restClient = new AsynchronousJiraRestClient(jiraServerUri, new AnonymousAuthenticationHandler());
        this.issueClient = restClient.getIssueClient();
        this.cache = cache;
    }

    private List<String> getComponentsFromJiraApi(String issueId) throws JiraException {
        final Issue issue;
        try {
            issue = issueClient.getIssue(issueId).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JiraException("Error while retrieving data from Jira", e);
        }

        return StreamSupport
            .stream(issue.getComponents().spliterator(),false)
            .map(BasicComponent::getName)
            .collect(Collectors.toList());
    }

    public List<String> getComponents(String jiraId) throws JiraException {
        List<String> fromCache = cache.get(jiraId);
        if(fromCache != null) {
            return fromCache;
        } else {
            List<String> fromJira = getComponentsFromJiraApi(jiraId);
            try {
                cache.put(jiraId, fromJira);
            } catch (IOException e) {
                throw new JiraException("Error while putting data into cache", e);
            }
            LOG.info("Getting components for {} from JIRA server", jiraId);
            return fromJira;
        }
    }

    public boolean invalidateCache(String issueId) {
        return cache.remove(issueId);
    }

    public JiraRestClient getJiraClient() {
        return this.restClient;
    }

    public static class JiraException extends Exception {
        public JiraException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
