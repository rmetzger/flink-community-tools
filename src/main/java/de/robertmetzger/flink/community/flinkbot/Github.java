package de.robertmetzger.flink.community.flinkbot;


import com.squareup.okhttp.Cache;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.OkUrlFactory;
import org.kohsuke.github.*;
import org.kohsuke.github.extras.OkHttpConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Github {
    private static Logger LOG = LoggerFactory.getLogger(Github.class);

    private final GitHub gitHub;

    private final String repository;
    /**
     * Defines the minimum PR number of the bot to consider a PR.
     * Idea: only apply the bot to new Flink PRs.
     */
    private final int minPRNumber;
    private String botName;

    public Github(Properties prop) {
        int cacheMB = Integer.valueOf(prop.getProperty("main.cacheMB"));
        String cacheDir = prop.getProperty("main.cacheDir");
        botName = prop.getProperty("gh.user");

        Cache cache = new Cache(new File(cacheDir), cacheMB * 1024 * 1024); // 10MB cache
        try {
            gitHub = GitHubBuilder.fromEnvironment().withPassword(botName, prop.getProperty("gh.token"))
                    .withConnector(new OkHttpConnector(new OkUrlFactory(new OkHttpClient().setCache(cache))))
                    .build();
            if(!gitHub.isCredentialValid()) {
                throw new RuntimeException("Invalid credentials");
            }
        } catch (IOException e) {
            throw new RuntimeException("Error initializing GitHub", e);
        }
        repository = prop.getProperty("gh.repo");
        minPRNumber = Integer.valueOf(prop.getProperty("gh.minPRNumber"));
    }

    /**
     * Gets all open pull requests (treated as issues)
     */
    public List<GHIssue> getAllPullRequests() {
        try {
            GHRepository repo = gitHub.getRepository(repository);
            List<GHIssue> allIssues = repo.getIssues(GHIssueState.OPEN);
            // remove issues, keep PRs
            allIssues.removeIf(issue -> !issue.isPullRequest());
            allIssues.removeIf(issue -> issue.getNumber() < minPRNumber);
            return allIssues;
        } catch (IOException e) {
            LOG.warn("Error getting pull requests", e);
            return new ArrayList<>();
        }
    }

    public String getBotName() {
        return botName;
    }

    public int getRemainingRequests() {
        try {
            return gitHub.getRateLimit().remaining;
        } catch (IOException e) {
            LOG.info("Unable to get current rate limit", e);
            return -1;
        }
    }

    public Iterator<GHThread> getNewNotificationsIterator() {
        GHNotificationStream notifications = gitHub.listNotifications();
        // we are blocking
        notifications.nonBlocking(false);
        notifications.since(0);

        notifications.read(false);
        notifications.participating(true);
        return notifications.iterator();
    }

}
