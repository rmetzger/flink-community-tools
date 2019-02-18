package de.robertmetzger.flink.community.flinkbot;


import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.OkUrlFactory;
import org.kohsuke.github.*;
import org.kohsuke.github.extras.OkHttp3Connector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Github {
    private static Logger LOG = LoggerFactory.getLogger(Github.class);

    private final GitHub cachedGitHub;

    private final GitHub directGitHub;

    private final String repository;
    /**
     * Defines the minimum PR number of the bot to consider a PR.
     * Idea: only apply the bot to new Flink PRs.
     */
    private final int minPRNumber;
    private final GitHub writeGitHub;
    private String botName;

    public Github(Properties prop) {
        int cacheMB = Integer.valueOf(prop.getProperty("main.cacheMB"));
        String cacheDir = prop.getProperty("main.cacheDir");
        botName = prop.getProperty("gh.user");


        try {
            Cache cache = new Cache(new File(cacheDir), cacheMB * 1024 * 1024);
            OkHttpClient.Builder okHttpClient = new OkHttpClient.Builder();
            okHttpClient.cache(cache);
            cachedGitHub = GitHubBuilder.fromEnvironment().withPassword(botName, prop.getProperty("gh.token"))
                    .withConnector(new OkHttp3Connector(new OkUrlFactory(okHttpClient.build())))
                    .build();
            if(!cachedGitHub.isCredentialValid()) {
                throw new RuntimeException("Invalid credentials");
            }

            /*GHRepository repo = cachedGitHub.getRepository("flinkqa/test");
            GHIssue issue = repo.getIssue(4);
            // assume "test" exists
            issue.removeLabels("test");
            issue.addLabels("test1");

            System.exit(1); */

            // also establish an uncached connection with GitHub for notifications processing
            directGitHub = GitHubBuilder.fromEnvironment().withPassword(botName, prop.getProperty("gh.token"))
                    .withConnector(new OkHttp3Connector(new OkUrlFactory(new OkHttpClient.Builder().build())))
                    .build();

            // use an uncached connection for the write connection, as writes can lead to caching issues.
            writeGitHub = GitHubBuilder.fromEnvironment().withPassword(prop.getProperty("gh.write.user"), prop.getProperty("gh.write.token"))
                    .withConnector(new OkHttp3Connector(new OkUrlFactory(new OkHttpClient.Builder().build())))
                    .build();

            if(!writeGitHub.isCredentialValid()) {
                throw new RuntimeException("Invalid write credentials");
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
            GHRepository repo = cachedGitHub.getRepository(repository);
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
            return cachedGitHub.getRateLimit().remaining;
        } catch (IOException e) {
            LOG.info("Unable to get current rate limit", e);
            return -1;
        }
    }

    public Iterator<GHThread> getNewNotificationsIterator() {
        GHNotificationStream notifications = directGitHub.listNotifications();
        // we are blocking
        notifications.nonBlocking(false);
        notifications.since(0);

        notifications.read(false);
        notifications.participating(false);
        return notifications.iterator();
    }


    public GHRepository getWriteableRepository() throws IOException {
        return writeGitHub.getRepository(repository);
    }
}
