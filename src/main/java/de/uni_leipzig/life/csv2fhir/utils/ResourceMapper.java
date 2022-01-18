package de.uni_leipzig.life.csv2fhir.utils;

import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

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

}
