package de.robertmetzger;


import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.BasicComponent;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.auth.AnonymousAuthenticationHandler;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DiskCachedJira {
    private static final Logger LOG = LoggerFactory.getLogger(DiskCachedJira.class);

    private final Cache cache;
    private final URI jiraUri;
    private JiraRestClient restClient = null;
    private IssueRestClient issueClient = null;


    public DiskCachedJira(String jiraUrl, Cache cache) throws URISyntaxException {
        this.jiraUri = new URI(jiraUrl);
        this.cache = cache;
    }


    private List<String> getComponentsFromJiraApi(String issueId) throws JiraException {
        Issue issue = null;
        int trie = 0;
        Throwable last = null;
        while(trie++ < 4) {
            try {
                issue = getIssueClient().getIssue(issueId).get();
                last = null;
                break; // successfully got issue
            } catch (Throwable t) {
                LOG.info("Got exception while getting Jira ticket " + issueId + " try " + trie + ". Waiting for 30 seconds.", t);
                try {
                    restClient.close();
                } catch (IOException e) {
                    throw new JiraException("Error while closing rest client", e);
                }
                restClient = null; issueClient = null;
                try {
                    Thread.sleep(30 * 1000); // wait for 30 seconds
                } catch (InterruptedException e) {
                    continue; // stop waiting, try next.
                }
                last = t;
            }
        }
        if(last != null) {
            throw new JiraException("Error while retrieving data from Jira", last);
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

    private IssueRestClient getIssueClient() {
        if(issueClient == null) {
            issueClient = getJiraClient().getIssueClient();
        }
        return issueClient;
    }

    public JiraRestClient getJiraClient() {
        if(this.restClient == null) {
            LOG.info("Creating new JIRA REST client");
            AsynchronousJiraRestClientFactory factory = new AsynchronousJiraRestClientFactory();
            this.restClient = factory.create(this.jiraUri, new AnonymousAuthenticationHandler());
        }
        return this.restClient;
    }

    public static class JiraException extends Exception {
        public JiraException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
