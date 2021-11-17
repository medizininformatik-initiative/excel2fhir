/**
 *
 */
package de.uni_leipzig.imise.utils;

import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
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
            CodeSystemPropertiesLoader ucumLoader = new CodeSystemPropertiesLoader();
            URL ucumMapFile = ClassLoader.getSystemResource(resourceFileName);
            try (InputStream inputStream = ucumMapFile.openStream()) {
                ucumLoader.load(inputStream);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public synchronized String put(Object humanReadable, Object ucumCode) {
            //Sys.out1(humanReadable + " -> " + ucumCode);
            String ucumCodeString = String.valueOf(ucumCode);
            //the const value 'EMPTY_STRING' means the empty string "" :)
            if ("EMPTY_STRING".equals(ucumCodeString)) {
                ucumCodeString = "";
            }
            String humanReadableString = String.valueOf(humanReadable);
            // the very first human readable non-UCUM code in the ucum properties
            // file is the value for the backward mapping from UCUM to nin-UCUM
            String existingHumanReadable = codeToHumanMap.get(ucumCode);
            if (existingHumanReadable == null) {
                codeToHumanMap.put(ucumCodeString, humanReadableString);
            }
            return humanToCodeMap.put(humanReadableString, ucumCodeString);
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

}
