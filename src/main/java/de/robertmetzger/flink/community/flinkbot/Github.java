package de.robertmetzger.flink.community.flinkbot;


import com.squareup.okhttp.Cache;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.OkUrlFactory;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.extras.OkHttpConnector;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

public class Github {
    private final GitHub gitHub;

    public Github(Properties prop) {
        int cacheMB = Integer.valueOf(prop.getProperty("main.cacheMB"));
        String cacheDir = prop.getProperty("main.cacheDir");
        Cache cache = new Cache(new File(cacheDir), cacheMB * 1024 * 1024); // 10MB cache
        try {
            gitHub = GitHubBuilder.fromEnvironment().withPassword(prop.getProperty("gh.user"), prop.getProperty("gh.token"))
                    .withConnector(new OkHttpConnector(new OkUrlFactory(new OkHttpClient().setCache(cache))))
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("Error initializing GitHub", e);
        }
    }
}
