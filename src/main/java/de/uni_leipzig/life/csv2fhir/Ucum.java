package de.uni_leipzig.life.csv2fhir;

import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import de.uni_leipzig.imise.utils.Sys;

/**
 * @author fheuschkel (02.11.2020), AXS
 */
@SuppressWarnings("serial")
public class Ucum {

    /**
     * Name of the ucum map file in the resources which maps from non-UCUM text.
     */
    public static final String UCUM_MAP_FILE_NAME = "ucum.map";

    /** Maps from human readable non-UCUM text to the UCUM code */
    private static Map<String, String> humanToUcumMap = new HashMap<>();

    /** Maps from an UCUM code to the human readable non-UCUM text */
    private static Map<String, String> ucumToHumanMap = new HashMap<>();

    static {
        UcumPropertiesLoader.load();
    }

    /**
     * Fills the both maps from the properties file.
     *
     * @author AXS (17.11.2021)
     */
    private static class UcumPropertiesLoader extends Properties {

        /**
         * Loads the properties file from the resources and fills the both maps.
         */
        public static void load() {
            UcumPropertiesLoader ucumLoader = new UcumPropertiesLoader();
            URL ucumMapFile = ClassLoader.getSystemResource(UCUM_MAP_FILE_NAME);
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
            String existingHumanReadable = ucumToHumanMap.get(ucumCode);
            if (existingHumanReadable == null) {
                ucumToHumanMap.put(ucumCodeString, humanReadableString);
            }
            return humanToUcumMap.put(humanReadableString, ucumCodeString);
        }

    }

    /**
     * @param ucum
     * @return
     */
    public static boolean isUcum(String ucum) {
        if (ucum == null || ucum.isBlank()) {
            return false;
        }
        String[] uArr = ucum.split("/", -1);
        for (String u : uArr) {
            String h = ucumToHumanMap.get(u);
            if (h == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param ucum
     * @return
     */
    public static String ucum2human(String ucum) {
        String[] uArr = ucum.split("/", -1);
        String human = "";
        for (String u : uArr) {
            String h = ucumToHumanMap.get(u.trim());
            if (h == null) {
                Sys.out1("unknown ucum unit <" + u + "> in " + ucum + "; error ignored");
                h = u;
            }
            if (!human.isEmpty()) {
                human += "/";
            }
            human += h;
        }
        return human;
    }

    /**
     * @param human
     * @return
     */
    public static String human2ucum(String human) {
        String[] hArr = human.split("/", -1);
        String ucum = "";
        boolean first = true;
        for (String h : hArr) {
            String u = humanToUcumMap.get(h.trim());
            if (u == null) {
                Sys.out1("unknown human readable unit <" + h + "> in " + human + "; ucum will be empty");
                return "";
            }

            if (first) {
                first = false;
            } else {
                ucum += "/";
            }

            ucum += u;
        }
        return ucum;
    }
}
