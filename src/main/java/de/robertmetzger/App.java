package de.robertmetzger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.OkUrlFactory;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.extras.OkHttp3Connector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hello world!
 */
public class App {
    private static Logger LOG = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) throws Exception {
        Properties prop = new Properties();
        try {
            InputStream config = App.class.getResourceAsStream("/config.properties");
            if (config == null) {
                throw new RuntimeException(
                    "Unable to load /config.properties from the CL. CP: " + System.getProperty(
                        "java.class.path"));
            }
            prop.load(config);
        } catch (IOException e) {
            throw new RuntimeException("Unable to load /config.properties from the CL", e);
        }

        int cacheMB = Integer.valueOf(prop.getProperty("main.cacheMB"));
        String cacheDir = prop.getProperty("main.cacheDir");
        String readUser = prop.getProperty("gh.user");

        Cache cache = new Cache(new File(cacheDir), cacheMB * 1024 * 1024);
        OkHttpClient.Builder okHttpClient = new OkHttpClient.Builder();
        okHttpClient.cache(cache);
        GitHub cachedGitHub = GitHubBuilder.fromEnvironment().withPassword(
            readUser,
            prop.getProperty("gh.token"))
            .withConnector(new OkHttp3Connector(new OkUrlFactory(okHttpClient.build())))
            .build();
        if (!cachedGitHub.isCredentialValid()) {
            throw new RuntimeException("Invalid credentials");
        }

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        int checkNewPRSeconds = Integer.valueOf(prop.getProperty("main.checkNewPRSeconds"));
        PullUpdater updater = new PullUpdater(cachedGitHub);
        executor.scheduleAtFixedRate(() -> {
            try {
                updater.checkPullRequests();
            } catch (Throwable t) {
                LOG.warn("Error while checking for new PRs", t);
            }
        }, 0, checkNewPRSeconds, TimeUnit.SECONDS);


        //  final URI jiraServerUri = new URI("https://issues.apache.org/jira");
        //final URI jiraServerUri = new URI("http://localhost:8080");

        //  final JiraRestClient restClient = new AsynchronousJiraRestClient(jiraServerUri, new AnonymousAuthenticationHandler());
       /* final Issue issue = restClient.getIssueClient().getIssue("FLINK-11418").get();

        List<String> components = StreamSupport
            .stream(issue.getComponents().spliterator(),false)
            .map(BasicComponent::getName)
            .collect(Collectors.toList());

        System.out.println("Components " + components);
        System.out.println(issue); */



       /* SearchResult result = restClient.getSearchClient().searchJql(
            "project = FLINK ORDER BY updated DESC, priority DESC").get();
        System.out.println("Result " + result); */

    }
}
