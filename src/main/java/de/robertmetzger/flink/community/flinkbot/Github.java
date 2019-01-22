package de.robertmetzger.flink.community.flinkbot;


import com.squareup.okhttp.Cache;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.OkUrlFactory;
import org.kohsuke.github.*;
import org.kohsuke.github.extras.OkHttpConnector;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Github {
    private final GitHub gitHub;

    private final String repository;
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
    }

    public List<GHIssue> getAllPullRequests() {
        try {
            GHRepository repo = gitHub.getRepository(repository);
            List<GHIssue> allIssues = repo.getIssues(GHIssueState.OPEN);
            // remove issues, keep PRs
            allIssues.removeIf(issue -> !issue.isPullRequest());
            return allIssues;
        } catch (IOException e) {
            throw new RuntimeException("err", e);
        }
    }

    public String getBotName() {
        return botName;
    }

    public int getRemainingRequests() {
        try {
            return gitHub.getRateLimit().remaining;
        } catch (IOException e) {
            throw new RuntimeException("err", e);
        }
    }

    public List<NotificationAndComments> getNewNotifications() {
        GHNotificationStream notifications = gitHub.listNotifications();
        //  blocking mode = we are not listening for new notifications
        notifications.nonBlocking(true);

        notifications.read(false);
        notifications.participating(true);
        Iterator<GHThread> iter = notifications.iterator();
        List<NotificationAndComments> result = new ArrayList<>();

        System.out.println("Getting notifications");
        while(iter.hasNext()) {
            GHThread ele = iter.next();
            if(ele.getReason().equals("mention")) {
                try {
                    List<GHIssueComment> comments = ele.getBoundPullRequest().getComments();
                    // sort by date, so that we process mentions in order
                    Collections.sort(comments, (o1, o2) -> {
                        try {
                            return o1.getCreatedAt().compareTo(o2.getCreatedAt());
                        } catch (IOException e) {
                            throw new RuntimeException("err", e);
                        }
                    });
                    result.add(new NotificationAndComments(ele, comments));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        try {
            System.out.println("Done checking notifications " + gitHub.getRateLimit().remaining);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }


    public static class NotificationAndComments {
        private GHThread notification;
        private List<GHIssueComment> comments;

        public NotificationAndComments(GHThread notification, List<GHIssueComment> comments) {
            this.notification = notification;
            this.comments = comments;
        }

        public GHThread getNotification() {
            return notification;
        }

        public List<GHIssueComment> getComments() {
            return comments;
        }
    }
}
