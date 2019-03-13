package de.robertmetzger;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import sun.util.logging.PlatformLogger;

/**
 * Hello world!
 */
public class App {
    private static Logger LOG = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) throws Exception {

    /*    SLF4JBridgeHandler.install();
        sun.util.logging.PlatformLogger .getLogger("sun.net.www.protocol.http.HttpURLConnection") .setLevel(
            PlatformLogger.Level.ALL);
*/

        LOG.info("Launching PR labeler");
        Properties prop = Utils.getConfig("/config.properties");

        GitHub cachedGitHub = Utils.getGitHub(  prop.getProperty("gh.user"),
                                                prop.getProperty("gh.token"),
                                                prop.getProperty("main.cacheDir"),
                                                Integer.valueOf(prop.getProperty("main.cacheMB"))
                                             );

        GitHub writableGitHub = Utils.getGitHub(    prop.getProperty("gh.write.user"),
                                                    prop.getProperty("gh.write.token"),
                                                    null,0
                                                );

        String cacheDirectory = prop.getProperty("jira.cache");

        DiskCachedJira jira = new DiskCachedJira(prop.getProperty("jira.url"), new DiskCache(cacheDirectory));
        PullUpdater updater = new PullUpdater(cachedGitHub, writableGitHub, jira, prop.getProperty("gh.repo"));

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        int checkNewPRSeconds = Integer.valueOf(prop.getProperty("main.checkNewPRSeconds"));

        executor.scheduleAtFixedRate(() -> {
            try {
                LOG.info("Checking pull requests. GitHub API limits read: {}, write: {}", cachedGitHub.getRateLimit(), writableGitHub.getRateLimit());
            } catch (IOException e) {
                LOG.warn("Error while getting rate limits", e);
            }
            try {
                updater.checkPullRequests();
            } catch (Throwable t) {
                LOG.warn("Error while checking for new PRs", t);
            }
            LOG.info("Done checking pull requests");
        }, 0, checkNewPRSeconds, TimeUnit.SECONDS);

        ScheduledExecutorService jiraInvalidatorExecutor = Executors.newScheduledThreadPool(1);
        int invalidateJiraSeconds = Integer.valueOf(prop.getProperty("main.invalidateJiraSeconds"));

        JiraCacheInvalidator invalidator = new JiraCacheInvalidator(jira, cacheDirectory);
        jiraInvalidatorExecutor.scheduleAtFixedRate(() -> {
            try {
                invalidator.run();
            } catch (Throwable t) {
                LOG.warn("Error while checking for new PRs", t);
            }
        }, 0, invalidateJiraSeconds, TimeUnit.SECONDS);

    }
}
