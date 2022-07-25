package de.uni_leipzig.life.csv2fhir.utils;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

import javax.annotation.Nullable;

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
     * @param resourceFileNames
     * @return true if at least one file could be loaded
     */
    public boolean load(@Nullable String... resourceFileNames) {
        boolean loaded = false;
        for (String resourceFileName : resourceFileNames) {
            try {
                File resourceFile = new File(resourceFileName);
                URL mapFile = null;
                if (resourceFile.canRead()) {
                    mapFile = resourceFile.toURI().toURL();
                } else {
                    mapFile = ClassLoader.getSystemResource(resourceFileName);
                }
                InputStream inputStream = mapFile.openStream();
                load(inputStream);
                inputStream.close();
                loaded = true;
            } catch (Exception e) {
                // ignore
            }
        }
        return loaded;
    }

    /**
     * @param resourceFileNames
     * @return
     */
    public static ResourceMapper of(String... resourceFileNames) {
        return new ResourceMapper(resourceFileNames);
    }

}
