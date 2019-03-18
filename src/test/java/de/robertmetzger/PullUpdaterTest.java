package de.robertmetzger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.Test;


public class PullUpdaterTest
{
    @Test
    public void testJiraNameExtraction() {
        assertEquals("FLINK-11537", PullUpdater.extractJiraId("[BP-1.7][FLINK-11537] Make ExecutionGraph#suspend terminate ExecutionGraph atomically"));
        assertEquals("FLINK-11838", PullUpdater.extractJiraId("FLINK-11838 Add GCS RecoverableWriter"));
        assertEquals("FLINK-11786", PullUpdater.extractJiraId("[FLINK-11786][travis] Merge cron branches into master"));
        assertEquals("FLINK-11786", PullUpdater.extractJiraId("[Flink-11786][travis] Merge cron branches into master"));
        assertNull(PullUpdater.extractJiraId("[typo] Inaccurate info on Avro splitting support"));
        assertNull(PullUpdater.extractJiraId("[FLINK-x] Activate checkstyle flink-java/*"));
        assertNull(PullUpdater.extractJiraId("[FLINK-??] Activate checkstyle flink-java/*"));
        assertNull(PullUpdater.extractJiraId("[FLINK-][travis] Merge cron branches into master"));
    }

    @Test
    public void testLength() {
        List<String> res = PullUpdater.normalizeComponents(Collections.singletonList(
            "Formats(JSON,Avro,Parquet,ORC,SequenceFile)"));

        assertEquals("component=Formats", res.get(0));

        res = PullUpdater.normalizeComponents(Collections.singletonList(
            "API/DataSet"));

        assertEquals("component=API/DataSet", res.get(0));
    }
}
