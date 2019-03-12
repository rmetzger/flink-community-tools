package de.robertmetzger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DiskCache implements Cache {
    private static Logger LOG = LoggerFactory.getLogger(DiskCache.class);

    private final String parent;

    public DiskCache(String location) throws IOException {
        this.parent = location;
        File locationAsFile = new File(location);
        if(!locationAsFile.exists()) {
            LOG.warn("Disk cache location does not exist. Creating it.");
            locationAsFile.mkdirs();
        }
    }

    private File locateFile(String key) {
        String name = Base64.getEncoder().encodeToString(key.getBytes());
        return new File(parent, name);
    }


    @Override
    public List<String> get(String key) {
        if(key == null) {
            return null;
        }
        File file = locateFile(key);
        if(file.exists()) {

            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                return (List<String>) ois.readObject();
            } catch (IOException | ClassNotFoundException e) {
                LOG.warn("Error while deserializing cached value", e);
                return null;
            }
        } else {
            return null;
        }
    }

    @Override
    public void put(String key, List<String> elements) throws IOException {
        File file = locateFile(key);
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file)))  {
            oos.writeObject(elements);
        }
    }

    @Override
    public void remove(String key) {
        locateFile(key).delete();
    }
}
