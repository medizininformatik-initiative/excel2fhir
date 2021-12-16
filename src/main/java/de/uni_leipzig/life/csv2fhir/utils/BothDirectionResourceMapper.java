package de.uni_leipzig.life.csv2fhir.utils;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * @author AXS (03.12.2021)
 */
public class BothDirectionResourceMapper implements Map<String, String> {

    /**
     * Constant string as map value in the map to identify the empty string
     */
    public static final String EMPTY_STRING = "EMPTY_STRING";

    /**
     * Maps from a key from the loaded resource file to a value.
     */
    private final HashMap<String, String> forwardMap = new HashMap<>();

    /**
     * Maps from the values back to the keys. You will always get the very first
     * key in the map if the value is ambigious.
     */
    private final HashMap<String, String> backwardMap = new HashMap<>();

    /**
     * Stores the values of the map in the order in which they are added the
     * first time. If a value already exists in this map it will not be added
     * again.
     */
    private final List<String> valuesAddingOrder = new ArrayList<>();

    /**
     *
     */
    private final CodeSystemPropertiesLoader codeSystemPropertiesLoader = new CodeSystemPropertiesLoader();

    /**
     * Fills both maps from the properties file.
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
        public synchronized String put(Object key, Object value) {
            //Sys.out1(key + " -> " + value);
            String valueString = String.valueOf(value).trim(); //we trim!
            //the const value 'EMPTY_STRING' means the empty string "" :)
            if (EMPTY_STRING.equals(valueString)) {
                valueString = "";
            }
            String keyString = String.valueOf(key);
            // the very first key in the properties file is the value
            // for the backward mapping from value to key
            String existingBackwardKey = backwardMap.get(valueString);
            if (existingBackwardKey == null) {
                backwardMap.put(valueString, keyString);
                valuesAddingOrder.add(valueString);
            }
            return forwardMap.put(keyString, valueString);
        }

    }

    /**
     * @param resourceFileName
     */
    public BothDirectionResourceMapper(String... resourceFileNames) {
        for (String resourceFileName : resourceFileNames) {
            codeSystemPropertiesLoader.load(resourceFileName);
        }
    }

    /**
     * @return an iterable of {@link #valuesAddingOrder}
     */
    public Iterable<String> getValuesInAddingOrder() {
        return () -> valuesAddingOrder.iterator();
    }

    /**
     * @param key
     * @return
     */
    public String getForwardValue(String key) {
        return forwardMap.get(key);
    }

    /**
     * @param value
     * @return
     */
    public String getFirstBackwardKey(String value) {
        return backwardMap.get(value);
    }

    /**
     * @return
     * @see java.util.HashMap#keySet()
     */
    @Override
    public Set<String> keySet() {
        return forwardMap.keySet();
    }

    /**
     * @return
     * @see java.util.HashMap#values()
     */
    @Override
    public Collection<String> values() {
        return forwardMap.values();
    }

    @Override
    public int size() {
        return forwardMap.size();
    }

    @Override
    public boolean isEmpty() {
        return forwardMap.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return forwardMap.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return forwardMap.containsValue(value);
    }

    @Override
    public String get(Object key) {
        return getForwardValue(String.valueOf(key));
    }

    @Override
    public String put(String key, String value) {
        return codeSystemPropertiesLoader.put(key, value);
    }

    @Override
    public String remove(Object key) {
        return forwardMap.remove(key);
    }

    @Override
    public void putAll(Map<? extends String, ? extends String> m) {
        for (String key : m.keySet()) {
            put(key, m.get(key));
        }
    }

    @Override
    public void clear() {
        forwardMap.clear();
        backwardMap.clear();
        valuesAddingOrder.clear();
    }

    @Override
    public Set<Entry<String, String>> entrySet() {
        return forwardMap.entrySet();
    }

}
