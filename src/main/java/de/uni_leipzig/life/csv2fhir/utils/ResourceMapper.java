package de.uni_leipzig.life.csv2fhir.utils;

import java.io.InputStream;
import java.net.URL;
import java.util.Properties;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

/**
 * @author AXS (03.12.2021)
 */
@SuppressWarnings("serial")
public class ResourceMapper extends Properties {

    /**
     * @param resourceFileNames the property files to load from the resources
     */
    public ResourceMapper(String... resourceFileNames) {
        load(resourceFileNames);
    }

    /**
     * Loads the properties files from the resources.
     *
     * @param resourceFileName
     */
    public void load(String... resourceFileNames) {
        for (String resourceFileName : resourceFileNames) {
            URL mapFile = ClassLoader.getSystemResource(resourceFileName);
            try (InputStream inputStream = mapFile.openStream()) {
                load(inputStream);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * @param resourceFileNames
     * @return
     */
    public static ResourceMapper of(String... resourceFileNames) {
        return new ResourceMapper(resourceFileNames);
    }

    /**
     * Set of String values that can be interpreted as booleans with value
     * "true".
     */
    private static final Set<String> trueValues = ImmutableSet.of("true", "t", "wahr", "w", "yes", "y", "ja", "j");

    /**
     * @param key
     * @return
     */
    public boolean getBoolean(String key) {
        Object object = get(key);
        if (object == null) {
            return false;
        }
        String value = object.toString().trim().toLowerCase();
        return trueValues.contains(value);
    }

}
