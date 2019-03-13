package de.robertmetzger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import org.junit.Test;


public class PullUpdaterTest
{
    @Test
    public void testJiraNameExtraction() throws IOException {
        PullUpdater pu = new PullUpdater(null, null, null,null);

        assertEquals("FLINK-11537", pu.extractJiraId("[BP-1.7][FLINK-11537] Make ExecutionGraph#suspend terminate ExecutionGraph atomically"));
        assertEquals("FLINK-11838", pu.extractJiraId("FLINK-11838 Add GCS RecoverableWriter"));
        assertEquals("FLINK-11786", pu.extractJiraId("[FLINK-11786][travis] Merge cron branches into master"));
        assertNull(pu.extractJiraId("[typo] Inaccurate info on Avro splitting support"));
        assertNull(pu.extractJiraId("[FLINK-x] Activate checkstyle flink-java/*"));
        assertNull(pu.extractJiraId("[FLINK-??] Activate checkstyle flink-java/*"));
        assertNull(pu.extractJiraId("[FLINK-][travis] Merge cron branches into master"));
    }
}
