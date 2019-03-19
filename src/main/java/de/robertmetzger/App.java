package de.robertmetzger;

import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Hello world!
 */
public class App {
    private static Logger LOG = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) throws Exception {

        /*SLF4JBridgeHandler.install();
        sun.util.logging.PlatformLogger .getLogger("sun.net.www.protocol.http.HttpURLConnection") .setLevel(
            PlatformLogger.Level.ALL); */


        LOG.info("Launching PR labeler version {}", Utils.getVersion());
        Properties prop = Utils.getConfig("/config.properties");

        String cacheDirectory = prop.getProperty("jira.cache");

        DiskCachedJira jira = new DiskCachedJira(prop.getProperty("jira.url"), new DiskCache(cacheDirectory));
        PullRequestLabelCache labelCache = new PullRequestLabelCache(prop.getProperty("main.labelCache"));
        PullUpdater updater = new PullUpdater(prop, jira, labelCache, prop.getProperty("gh.repo"));

        int checkNewPRSeconds = Integer.valueOf(prop.getProperty("main.checkNewPRSeconds"));

        Runnable checkPRs = () -> {
            while(true) {
                try {
                    updater.checkPullRequests();
                } catch (Throwable t) {
                    LOG.warn("Error while checking for new PRs", t);
                }
                LOG.info("Done checking pull requests. Waiting for {} seconds", checkNewPRSeconds);
                try {
                    Thread.sleep(checkNewPRSeconds * 1000);
                } catch (InterruptedException e) {
                    LOG.warn("Thread got interrupted");
                    break;
                }
            }
        };

        Thread checkPRThread = new Thread(checkPRs);
        checkPRThread.setName("check-pr-thread");
        checkPRThread.start();

        ScheduledExecutorService jiraInvalidatorExecutor = Executors.newScheduledThreadPool(1);
        int invalidateJiraSeconds = Integer.valueOf(prop.getProperty("main.invalidateJiraSeconds"));

        if(invalidateJiraSeconds > 0) {
            JiraCacheInvalidator invalidator = new JiraCacheInvalidator(jira, cacheDirectory);
            jiraInvalidatorExecutor.scheduleAtFixedRate(() -> {
                try {
                    invalidator.run();
                } catch (Throwable t) {
                    LOG.warn("Error while invalidating JIRAs", t);
                }
            }, 0, invalidateJiraSeconds, TimeUnit.SECONDS);
        }

    }
}
