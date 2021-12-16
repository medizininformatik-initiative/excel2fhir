package de.uni_leipzig.life.csv2fhir.utils;

import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * @author AXS (03.11.2021)
 */
public class JSONFunctions {

    /**
     * The delimiter for key tags
     */
    public static final String KEY_DELIMITER = "/";

    /**
     * @param jsonObject the source {@link JSONObject}
     * @param keys the tag names to the nested {@link JSONObject} each separated
     *            by the {@link #KEY_DELIMITER}
     * @return a nestet {@link JSONObject}
     */
    public static JSONObject getJSONObject(final JSONObject jsonObject, final String keys) {
        return (JSONObject) getValue(jsonObject, keys, false);
    }

    /**
     * @param jsonObject
     * @param keys
     * @return
     */
    public static JSONObject getParentJSONObject(final JSONObject jsonObject, final String keys) {
        return (JSONObject) getValue(jsonObject, keys, true);
    }

    /**
     * @param jsonObject
     * @param keys
     * @return
     */
    public static final JSONArray getJSONArray(final JSONObject jsonObject, final String keys) {
        return (JSONArray) getValue(jsonObject, keys, false);
    }

    /**
     * @param jsonObject
     * @param keys
     * @return
     */
    public static String getString(final JSONObject jsonObject, final String keys) {
        return String.valueOf(getValue(jsonObject, keys, false));
    }

    /**
     * @param jsonObject
     * @param parent
     * @param keys
     * @return
     */
    private static Object getValue(final JSONObject jsonObject, final String keys, final boolean parent) {
        String[] keysArray = keys.split(KEY_DELIMITER);
        return getSubValue(jsonObject, 0, parent, keysArray);
    }

    /**
     * @param jsonObject
     * @param nextKeyIndex
     * @param parent
     * @param keys
     * @return
     */
    private static Object getSubValue(JSONObject jsonObject, int nextKeyIndex, final boolean parent, final String... keys) {
        String key = keys[nextKeyIndex++];
        Object entry = jsonObject.get(key);
        if (nextKeyIndex == keys.length) {
            return entry;
        }
        if (entry instanceof JSONArray) {
            return getSubValue((JSONArray) entry, nextKeyIndex, parent, keys);
        }
        if (entry instanceof JSONObject) {
            jsonObject = (JSONObject) entry;
            if (nextKeyIndex < keys.length - 1) {
                return getSubValue(jsonObject, nextKeyIndex, parent, keys);
            }
            String lastKey = keys[keys.length - 1];
            Object lastValue = jsonObject.get(lastKey);
            return lastValue;
        }
        return null;
    }

    /**
     * @param jsonArray
     * @param nextKeyIndex
     * @param parent
     * @param keys
     * @return
     */
    private static Object getSubValue(final JSONArray jsonArray, int nextKeyIndex, final boolean parent, final String... keys) {
        for (Object jsonArrayEntry : jsonArray) {
            if (jsonArrayEntry instanceof JSONObject) {
                JSONObject subJSONObject = (JSONObject) jsonArrayEntry;
                Object subValue = getSubValue(subJSONObject, nextKeyIndex, parent, keys);
                if (subValue != null) {
                    return parent && nextKeyIndex == keys.length - 1 ? subJSONObject : subValue;
                }
            } else if (jsonArrayEntry instanceof JSONArray) {
                JSONArray subJSONArray = (JSONArray) jsonArrayEntry;
                nextKeyIndex++;
                if (nextKeyIndex == keys.length) {
                    return subJSONArray;
                }
                return getSubValue(jsonArray, nextKeyIndex, parent, keys);
            }
        }
        return null;
    }

    /**
     * @param parentJSONObject
     * @param value
     * @param keys
     * @return
     */
    @SuppressWarnings("unchecked")
    public static void putValue(final JSONObject parentJSONObject, final Object value, final String keys) {
        String[] keysArray = keys.split(KEY_DELIMITER);
        if (value instanceof String) {
            JSONObject jsonObject = getParentJSONObject(parentJSONObject, keys);
            String key = keysArray[keysArray.length - 1];
            jsonObject.put(key, value);
        }
    }

    /**
     * @param parentJSONObject
     * @param oldValue
     * @param newValue
     * @param keys
     * @return
     */
    public static boolean resetIfMatches(final JSONObject parentJSONObject, final String keys,
            final StringMatchCondition matchCondition, final String oldValue, final String newValue) {
        String value = getString(parentJSONObject, keys);
        if (matchCondition.matches(value, oldValue)) {
            putValue(parentJSONObject, newValue, keys);
            return true;
        }
        return false;
    }

    /**
     * @param jsonArray
     * @param pathInJSONArrayEntryToString
     * @param matchCondition
     * @param conditionValues
     * @return
     */
    public static List<JSONObject> getEntriesWithValue(final JSONArray jsonArray, final String pathInJSONArrayEntryToString,
            final StringMatchCondition matchCondition, final String... conditionValues) {
        return getEntriesWithValue(jsonArray, pathInJSONArrayEntryToString, matchCondition, false, conditionValues);
    }

    /**
     * @param jsonArray
     * @param pathInJSONArrayEntryToString
     * @param matchCondition
     * @param conditionValues
     * @return
     */
    public static JSONObject getFirstEntryWithValue(final JSONArray jsonArray, final String pathInJSONArrayEntryToString,
            final StringMatchCondition matchCondition, final String... conditionValues) {
        List<JSONObject> jsonArrayEntriesWithValue = getEntriesWithValue(jsonArray, pathInJSONArrayEntryToString,
                matchCondition, true, conditionValues);
        return jsonArrayEntriesWithValue.isEmpty() ? null : jsonArrayEntriesWithValue.get(0);
    }

    /**
     * @param jsonArray
     * @param pathInJSONArrayEntryToString
     * @param matchCondition
     * @param returnOnlyFirstValue
     * @param conditionValues
     * @return
     */
    private static List<JSONObject> getEntriesWithValue(final JSONArray jsonArray, final String pathInJSONArrayEntryToString,
            final StringMatchCondition matchCondition, final boolean returnOnlyFirstValue, final String... conditionValues) {
        List<JSONObject> arrayEntries = new ArrayList<>();
        for (Object entry : jsonArray) {
            if (entry instanceof JSONObject) {
                JSONObject jsonObject = (JSONObject) entry;
                String value = getString(jsonObject, pathInJSONArrayEntryToString);
                if (matchCondition.matches(value, conditionValues)) {
                    arrayEntries.add(jsonObject);
                    if (returnOnlyFirstValue) {
                        return arrayEntries;
                    }
                }
            } else if (entry instanceof JSONArray) {
                throw new UnsupportedOperationException("Must be implemented");
            }
        }
        return arrayEntries;
    }

    /**
     * @param jsonArray
     * @param pathInJSONArrayEntryToString
     * @return
     */
    public static List<JSONObject> getEntriesWithSubEntry(final JSONArray jsonArray,
            final String pathInJSONArrayEntryToString) {
        return getEntriesWithSubEntry(jsonArray, pathInJSONArrayEntryToString, false);
    }

    /**
     * @param jsonArray
     * @param pathInJSONArrayEntryToString
     * @param matchCondition
     * @param conditionValues
     * @return
     */
    public static JSONObject getFirstEntryWithSubEntry(final JSONArray jsonArray, final String pathInJSONArrayEntryToString) {
        List<JSONObject> jsonArrayEntriesWithSubEntry = getEntriesWithSubEntry(jsonArray, pathInJSONArrayEntryToString, true);
        return jsonArrayEntriesWithSubEntry.isEmpty() ? null : jsonArrayEntriesWithSubEntry.get(0);
    }

    /**
     * @param jsonArray
     * @param pathInJSONArrayEntryToString
     * @param matchCondition
     * @param returnOnlyFirstValue
     * @param conditionValues
     * @return
     */
    private static List<JSONObject> getEntriesWithSubEntry(final JSONArray jsonArray,
            final String pathInJSONArrayEntryToSubEntry, final boolean returnOnlyFirstValue) {
        List<JSONObject> arrayEntries = new ArrayList<>();
        for (Object entry : jsonArray) {
            if (entry instanceof JSONObject) {
                JSONObject jsonObject = (JSONObject) entry;
                if (hasSubEntry(jsonObject, pathInJSONArrayEntryToSubEntry)) {
                    arrayEntries.add(jsonObject);
                    if (returnOnlyFirstValue) {
                        return arrayEntries;
                    }
                }
            } else if (entry instanceof JSONArray) {
                throw new UnsupportedOperationException("Must be implemented");
            }
        }
        return arrayEntries;
    }

    /**
     * @param jsonObject
     * @param pathToSubEntry
     * @return
     */
    public static boolean hasSubEntry(JSONObject jsonObject, String pathToSubEntry) {
        return getValue(jsonObject, pathToSubEntry, false) != null;
    }

    /**
     * @param jsonObject
     * @param pathToJSONArray
     * @param pathInJSONArrayEntryToString
     * @param matchCondition
     * @param conditionValues
     * @return
     */
    public static List<JSONObject> getEntriesWithValue(final JSONObject jsonObject, final String pathToJSONArray,
            final String pathInJSONArrayEntryToString, final StringMatchCondition matchCondition,
            final String... conditionValues) {
        return getEntriesWithValue(jsonObject, pathToJSONArray, pathInJSONArrayEntryToString, matchCondition, false,
                conditionValues);
    }

    /**
     * @param jsonObject
     * @param pathToJSONArray
     * @param pathInJSONArrayEntryToString
     * @param matchCondition
     * @param conditionValues
     * @return
     */
    public static JSONObject getFirstEntryWithValue(final JSONObject jsonObject, final String pathToJSONArray,
            final String pathInJSONArrayEntryToString, final StringMatchCondition matchCondition,
            final String... conditionValues) {
        List<JSONObject> jsonArrayEntriesWithValue = getEntriesWithValue(jsonObject, pathToJSONArray,
                pathInJSONArrayEntryToString, matchCondition, false, conditionValues);
        return jsonArrayEntriesWithValue.isEmpty() ? null : jsonArrayEntriesWithValue.get(0);
    }

    /**
     * @param jsonObject
     * @param pathToJSONArray
     * @param pathInJSONArrayEntryToString
     * @param matchCondition
     * @param returnOnlyFirstValue
     * @param conditionValues
     * @return
     */
    private static List<JSONObject> getEntriesWithValue(final JSONObject jsonObject, final String pathToJSONArray,
            final String pathInJSONArrayEntryToString, final StringMatchCondition matchCondition,
            final boolean returnOnlyFirstValue,
            final String... conditionValues) {
        JSONArray jsonArray = getJSONArray(jsonObject, pathToJSONArray);
        return getEntriesWithValue(jsonArray, pathInJSONArrayEntryToString, matchCondition, returnOnlyFirstValue,
                conditionValues);
    }

    /**
     * Copies all entries from sourceJSONObject to targetJSONObject
     *
     * @param sourceJSONObject the souce object
     * @param targetJSONObject the target object
     * @param overwrite if <code>true</code> existing properties in the target
     *            object will be replaced
     */
    @SuppressWarnings("unchecked")
    public static void transferProperties(final JSONObject sourceJSONObject, final JSONObject targetJSONObject,
            final boolean overwrite) {
        for (Object sourceKey : sourceJSONObject.keySet()) {
            if (overwrite || !targetJSONObject.containsKey(sourceKey)) {
                Object value = sourceJSONObject.get(sourceKey);
                targetJSONObject.put(sourceKey, value);
            }
        }
    }

}
