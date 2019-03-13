package de.robertmetzger;


import java.io.IOException;
import java.util.List;

public interface Cache {

    /**
     * @param key
     * @return null if object is not present
     */
    List<String> get(String key);

    void put(String key, List<String> elements) throws IOException;

    boolean remove(String key);
}
