package de.robertmetzger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is caching access to GitHub labels on disk
 * Since most PR's labels do not change, should reduce the number of requests to the GitHub API on re-checking old PRs.
 */
public class PullRequestLabelCache {
    private static final Logger LOG = LoggerFactory.getLogger(PullRequestLabelCache.class);

    private final String parent;

    public PullRequestLabelCache(String parent) {
        this.parent = parent;
        File file = new File(parent);
        if(!file.exists()) {
            LOG.warn("parent {} does not exist. Creating it ?!", parent);
            file.mkdirs();
        }
    }

    public Collection<GHLabel> getLabelsFor(GHPullRequest pullRequest) throws IOException {
        File fileOnDisk = locateFile(Integer.toString(pullRequest.getNumber()));
        if(!fileOnDisk.exists()) {
            return getAndCache(pullRequest, fileOnDisk);
        }
        CacheEntry entry = getFromDisk(fileOnDisk);
        // cache >= GitHub API
        if(entry.lastUpdated.equals(pullRequest.getUpdatedAt()) || entry.lastUpdated.after(pullRequest.getUpdatedAt())) {
            // cache hit
            return entry.labels;
        }
        return getAndCache(pullRequest, fileOnDisk);
    }

    private CacheEntry getFromDisk(File fileOnDisk) throws IOException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(fileOnDisk))) {
            return (CacheEntry) ois.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException("Class not found", e);
        }
    }

    private Collection<GHLabel> getAndCache(GHPullRequest pullRequest, File fileOnDisk) throws
        IOException {
        LOG.info("Getting labels for PR #{} from GitHub", pullRequest.getNumber());
        CacheEntry entry = new CacheEntry();
        entry.labels = pullRequest.getLabels();
        entry.lastUpdated = pullRequest.getUpdatedAt();
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(fileOnDisk)))  {
            oos.writeObject(entry);
        }
        return entry.labels;
    }

    public static class CacheEntry implements Serializable {
        public Date lastUpdated;
        public Collection<GHLabel> labels;
    }

    private File locateFile(String key) {
        String name = Base64.getEncoder().encodeToString(key.getBytes());
        return new File(parent, name);
    }

}
