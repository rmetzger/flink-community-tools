package de.robertmetzger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.OkUrlFactory;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.extras.OkHttp3Connector;

public class Utils {
    public static Properties getConfig(String filename) {
        Properties prop = new Properties();
        try {
            InputStream config = App.class.getResourceAsStream(filename);
            if (config == null) {
                throw new RuntimeException(
                    "Unable to load /config.properties from the CL. CP: " + System.getProperty(
                        "java.class.path"));
            }
            prop.load(config);
        } catch (IOException e) {
            throw new RuntimeException("Unable to load /config.properties from the CL", e);
        }
        return prop;
    }

    public static GitHubWithCache getGitHub(String user, String password, String cacheDir, int cacheMB) throws IOException {
        GitHubBuilder ghBuilder = GitHubBuilder.fromEnvironment().withPassword(user, password);
        Cache cache = null;
        if(cacheDir != null) {
            cache = new Cache(new File(cacheDir), cacheMB * 1024 * 1024);
            OkHttpClient.Builder okHttpClient = new OkHttpClient.Builder();
            okHttpClient.cache(cache);
            ghBuilder.withConnector(new OkHttp3Connector(new OkUrlFactory(okHttpClient.build())));
        }
        GitHub gh = ghBuilder.build();
        if (!gh.isCredentialValid()) {
            throw new RuntimeException("Invalid credentials");
        }

        return new GitHubWithCache(gh, cache);
    }

    public static class GitHubWithCache {
        public final GitHub gitHub;
        public final Cache cache;

        public GitHubWithCache(GitHub gitHub, Cache cache) {
            this.gitHub = gitHub;
            this.cache = cache;
        }
    }

    public static String getVersion() {
        Properties properties = new Properties();
        try {
            properties.load(Utils.class.getClassLoader().getResourceAsStream("git.properties"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return properties.getProperty("git.commit.id");
    }
}
