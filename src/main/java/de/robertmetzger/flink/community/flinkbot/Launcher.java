package de.robertmetzger.flink.community.flinkbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
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
        LOG.info("Launching @flinkbot");

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
        final Flinkbot bot = new Flinkbot(gh);
        int checkNewPRSeconds = Integer.valueOf(prop.getProperty("main.checkNewPRSeconds"));
        int checkNewActionsSeconds = Integer.valueOf(prop.getProperty("main.checkNewActionsSeconds"));

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

        executor.scheduleAtFixedRate(() -> {
            try {
                bot.checkForNewPRs();
            } catch (Throwable t) {
                LOG.warn("Error while checking for new PRs", t);
            }
        }, 0, checkNewPRSeconds, TimeUnit.SECONDS);

        executor.scheduleAtFixedRate(
            () -> {
                try {
                    bot.checkForNewActions();
                } catch (Throwable t) {
                    LOG.warn("Error while checking for new actions", t);
                }
            },
            checkNewPRSeconds / 2,
            checkNewActionsSeconds,
            TimeUnit.SECONDS);
    }
}
