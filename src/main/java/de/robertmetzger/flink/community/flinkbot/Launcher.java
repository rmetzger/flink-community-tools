package de.robertmetzger.flink.community.flinkbot;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Launch the bot
 */
public class Launcher {
    public static void main(String[] args) {
        System.out.println("Launching @flinkbot");

        Properties prop = new Properties();
        try {
            prop.load(Launcher.class.getResourceAsStream("/config.properties"));
        } catch (IOException e) {
            throw new RuntimeException("Unable to load /config.properties from the CL", e);
        }
        Github gh = new Github(prop);
        final Flinkbot bot = new Flinkbot(gh);
        int checkNewPRSeconds = Integer.valueOf(prop.getProperty("main.checkNewPRSeconds"));
        int checkNewActionsSeconds = Integer.valueOf(prop.getProperty("main.checkNewActionsSeconds"));

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        /* executor.scheduleAtFixedRate(() -> {
            try {
                bot.checkForNewPRs();
            } catch (Throwable t) {
                System.out.println("E: "+ t.getMessage());
                t.printStackTrace();
            }
        }, 0, checkNewPRSeconds, TimeUnit.SECONDS); */

        executor.scheduleAtFixedRate(
            () -> {
                try {
                    bot.checkForNewActions();
                } catch (Throwable t) {
                    System.out.println("E: "+ t.getMessage());
                    t.printStackTrace();
                }
            },
            //checkNewPRSeconds / 2,
            0,
            checkNewActionsSeconds,
            TimeUnit.SECONDS);
    }
}
