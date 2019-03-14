package de.robertmetzger;

import com.atlassian.jira.rest.client.IssueRestClient;
import com.atlassian.jira.rest.client.SearchRestClient;
import com.atlassian.jira.rest.client.domain.BasicIssue;
import com.atlassian.jira.rest.client.domain.SearchResult;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enable HTTP logging:
 *  -Dorg.apache.commons.logging.Log=org.apache.commons.logging.impl.SimpleLog -Dorg.apache.commons.logging.simplelog.showdatetime=true -Dorg.apache.commons.logging.simplelog.log.org.apache.http=DEBUG -Dorg.apache.commons.logging.simplelog.log.org.apache.http.wire=ERROR
 */
public class JiraCacheInvalidator {

    private static Logger LOG = LoggerFactory.getLogger(App.class);

    private final DiskCachedJira jira;
    private final File dataFile;
    private final SearchRestClient searchClient;
    private final static Charset UTF8 = Charset.forName("UTF-8");
    private final IssueRestClient issueClient;


    public JiraCacheInvalidator(DiskCachedJira jira, String dataDirectory){
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

    /*
     * The following two methods handle time. We use java.time.Instant, which is UTC-based.
     * JIRA is also using time in UTC.
     */

    private void writeCurrentTimeToDataFile() throws IOException {
        FileOutputStream fos = new FileOutputStream(dataFile);
        Instant now = Instant.now();
        fos.write(Long.toString(now.toEpochMilli()).getBytes(UTF8));
        fos.close();
    }
    private Instant getLastUpdateTime() throws IOException {
        byte[] encoded = Files.readAllBytes(dataFile.toPath());
        String tsString = new String(encoded, UTF8);
        return Instant.ofEpochMilli((Long.parseLong(tsString)));
    }


    public void run() throws ExecutionException, InterruptedException, IOException {
        LOG.info("Invalidating updated JIRA tickets");
        Instant lastUpdated = getLastUpdateTime();

        // search for tickets
        DateTimeFormatter jqlDateFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone( ZoneId.of("UTC"));;
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
