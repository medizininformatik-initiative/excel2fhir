/**
 *
 */
package de.uni_leipzig.imise.utils;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

/**
 * @author AXS (17.11.2021)
 */
public class CodeSystemMapper {

    /**
     * As a convention, every code system map should contain this key, which
     * maps to the valid code system URL.
     */
    public static final String CODE_SYSTEM_URL_RESOURCE_KEY = "CODE_SYSTEM_URL";

    /**
     * Maps from a human readable text to a code.
     */
    private final HashMap<String, String> humanToCodeMap = new HashMap<>();

    /**
     * Maps from a code to the first human readable text added to the forward
     * mapping {@link #humanToCodeMap}.
     */
    private final HashMap<String, String> codeToHumanMap = new HashMap<>();

    /**
     * Stores the values of the map in the order in which they are added the
     * first time. If a value already exists in this map it will not be added
     * again.
     */
    private final List<String> valuesAddingOrder = new ArrayList<>();

    /**
     * Fills the both maps from the properties file.
     *
     * @author AXS (17.11.2021)
     */
    @SuppressWarnings("serial")
    private class CodeSystemPropertiesLoader extends Properties {

        /**
         * Loads the properties file from the resources and fills the both maps.
         *
         * @param resourceFileName
         */
        public void load(String resourceFileName) {
            CodeSystemPropertiesLoader codeMapLoader = new CodeSystemPropertiesLoader();
            URL ucumMapFile = ClassLoader.getSystemResource(resourceFileName);
            try (InputStream inputStream = ucumMapFile.openStream()) {
                codeMapLoader.load(inputStream);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public synchronized String put(Object humanReadable, Object code) {
            //Sys.out1(humanReadable + " -> " + code);
            String codeString = String.valueOf(code);
            //the const value 'EMPTY_STRING' means the empty string "" :)
            if ("EMPTY_STRING".equals(codeString)) {
                codeString = "";
            }
            String humanReadableString = String.valueOf(humanReadable);
            // the very first human readable non-code in the ucum properties
            // file is the value for the backward mapping from code to human
            String existingHumanReadable = codeToHumanMap.get(code);
            if (existingHumanReadable == null) {
                codeToHumanMap.put(codeString, humanReadableString);
            }
            if (!valuesAddingOrder.contains(code)) {
                valuesAddingOrder.add(String.valueOf(code));
            }
            return humanToCodeMap.put(humanReadableString, codeString);
        }

    }

    /**
     * @param resourceFileName
     */
    public CodeSystemMapper(String resourceFileName) {
        CodeSystemPropertiesLoader codeSystemPropertiesLoader = new CodeSystemPropertiesLoader();
        codeSystemPropertiesLoader.load(resourceFileName);
    }

    /**
     * @param code
     * @return
     */
    public String getCodeToHuman(String code) {
        return codeToHumanMap.get(code);
    }

    /**
     * @param humanReadableText
     * @return
     */
    public String getHumanToCode(String humanReadableText) {
        return humanToCodeMap.get(humanReadableText);
    }

    /**
     * @return
     */
    public String getCodeSystem() {
        return getHumanToCode(CODE_SYSTEM_URL_RESOURCE_KEY);
    }

    /**
     * @return an iterable of {@link #valuesAddingOrder}
     */
    public Iterable<String> getValuesInAddingOrder() {
        return () -> valuesAddingOrder.iterator();
    }

}
