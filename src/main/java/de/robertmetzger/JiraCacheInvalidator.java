package de.robertmetzger;

import com.atlassian.jira.rest.client.IssueRestClient;
import com.atlassian.jira.rest.client.SearchRestClient;
import com.atlassian.jira.rest.client.domain.BasicIssue;
import com.atlassian.jira.rest.client.domain.SearchResult;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JiraCacheInvalidator {

    private static Logger LOG = LoggerFactory.getLogger(App.class);

    private final DiskCachedJira jira;
    private final File dataFile;
    private final SearchRestClient searchClient;
    private final static Charset UTF8 = Charset.forName("UTF-8");
    private final IssueRestClient issueClient;


    public JiraCacheInvalidator(DiskCachedJira jira, String dataDirectory) {
        this.jira = jira;
        // initialize time-tracking
        this.dataFile = new File(dataDirectory, "__last-invalidator-run");
        if(!dataFile.exists()) {
            LOG.warn(" !NOTE! The datafile of the invalidator did not exist   !NOTE!");
            LOG.warn(" !NOTE! Creating it now                                 !NOTE!");
            LOG.warn(" !NOTE! Make sure this is the first run of the tool, or !NOTE!");
            LOG.warn(" !NOTE! delete all files in the cache directory.        !NOTE!");
            try {
                writeCurrentTimeToDataFile();
            } catch (IOException e) {
                LOG.warn("Unable to write data file", e);
            }
        }
        // initialize JIRA search client
        this.searchClient = jira.getJiraClient().getSearchClient();
        this.issueClient = jira.getJiraClient().getIssueClient();
    }

    private void writeCurrentTimeToDataFile() throws IOException {
        FileOutputStream fos = new FileOutputStream(dataFile);
        fos.write(Long.toString(System.currentTimeMillis()).getBytes(UTF8));
        fos.close();
    }
    private Date getLastUpdateTime() throws IOException {
        byte[] encoded = Files.readAllBytes(dataFile.toPath());
        String tsString = new String(encoded, UTF8);
        return new Date(Long.parseLong(tsString));
    }


    public void run() throws ExecutionException, InterruptedException, IOException {
        LOG.info("Invalidating updated JIRA tickets");
        Date lastUpdated = getLastUpdateTime();

        // search for tickets
        DateFormat jqlDateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm");
        String jql = "project = FLINK AND updatedDate  >= \""+jqlDateFormat.format(lastUpdated)+"\" ORDER BY updated DESC";
        LOG.debug("jql = {}", jql);
        SearchResult result = searchClient.searchJql(jql, 1000, 0).get();
        LOG.info("Processing {} JIRA tickets since {}", result.getTotal(), lastUpdated);
        Iterator<BasicIssue> resultIterator = result.getIssues().iterator();
        int i = 0;
        while(resultIterator.hasNext()) {
            BasicIssue ticket = resultIterator.next();
            LOG.info("Invalidating ticket[{}] = {}" , i++, ticket.getKey());
            if(jira.invalidateCache(ticket.getKey())) {
                LOG.info("  Deleted {} from cache.", ticket.getKey());
            }
        }
        writeCurrentTimeToDataFile();
    }
}
