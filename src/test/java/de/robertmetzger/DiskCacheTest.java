package de.robertmetzger;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DiskCacheTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testFullCycle() throws IOException {
        DiskCache dc = new DiskCache(folder.newFolder().toString());
        Assert.assertNull(dc.get("test"));

        List<String> e = Collections.singletonList("haha");
        dc.put("yolo", e);

        List<String> result = dc.get("yolo");
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("haha", result.get(0));

        dc.remove("yolo");
        Assert.assertNull(dc.get("yolo"));

        dc.remove("nonex");
    }
}