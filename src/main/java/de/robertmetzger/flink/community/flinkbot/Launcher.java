package de.robertmetzger.flink.community.flinkbot;


import org.apache.commons.lang3.StringUtils;
import org.kohsuke.github.GHThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Launch the bot
 */
public class Launcher {
    private static Logger LOG = LoggerFactory.getLogger(Launcher.class);

    public static void main(String[] args) {
        LOG.info("Launching The Flink Bot");

        // Optionally remove existing handlers attached to j.u.l root logger
        SLF4JBridgeHandler.removeHandlersForRootLogger();  // (since SLF4J 1.6.5)

        // add SLF4JBridgeHandler to j.u.l's root logger, should be done once during
        // the initialization phase of your application
        SLF4JBridgeHandler.install();

        Properties prop = new Properties();
        try {
            InputStream config = Launcher.class.getResourceAsStream("/config.properties");
            if(config == null) {
                throw new RuntimeException("Unable to load /config.properties from the CL. CP: " + System.getProperty("java.class.path"));
            }
            prop.load(config);
        } catch (IOException e) {
            throw new RuntimeException("Unable to load /config.properties from the CL", e);
        }

        Github gh = new Github(prop);
        String[] committers = StringUtils.split(prop.getProperty("main.committers"), ',');
        String[] pmc = StringUtils.split(prop.getProperty("main.pmc"), ',');
        final Flinkbot bot = new Flinkbot(gh, committers, pmc);


        // Schedule periodic checks
        int checkNewPRSeconds = Integer.valueOf(prop.getProperty("main.checkNewPRSeconds"));

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

        executor.scheduleAtFixedRate(() -> {
            try {
                bot.checkForNewPRs();
            } catch (Throwable t) {
                LOG.warn("Error while checking for new PRs", t);
            }
        }, 0, checkNewPRSeconds, TimeUnit.SECONDS);

        Thread notificationProcessor = new Thread(() -> {
            // process notifications indefinitely
            LOG.info("Launching notifications processor");
            Iterator<GHThread> notificationsIterator = gh.getNewNotificationsIterator();
            bot.processBotMentions(notificationsIterator);
            LOG.info("Shutting down notification processor ...");
        });
        notificationProcessor.setName("Notification processor");
        notificationProcessor.start();

    }
}
