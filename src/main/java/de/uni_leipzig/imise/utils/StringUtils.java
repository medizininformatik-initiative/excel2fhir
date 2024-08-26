package de.uni_leipzig.imise.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.base.Strings;

/**
 * Used to format the logging output.
 *
 * @author AXS (22.11.2021)
 */
public class StringUtils {

    /**
     * Fills the first string, if it is shorter, up to the length of the other
     * passed string with the given character. If its length is greater than or
     * equal to the length of the second string, it remains unchanged.
     *
     * @param s
     * @param c
     * @param newMinLenght
     * @return
     */
    public static String fillToLenght(final String stringToFill, char c, final String stringWithMinLength) {
        return fillToMinLenght(stringToFill, c, stringWithMinLength.length());
    }

    /**
     * Fills the passed string, if it is shorter, up to the passed length with
     * the passed character. If its length is greater than or equal to the
     * passed length, it remains unchanged.
     *
     * @param s
     * @param c
     * @param newMinLenght
     * @return
     */
    public static String fillToMinLenght(final String s, char c, final int newMinLenght) {
        int lengthDiff = newMinLenght - s.length();
        if (lengthDiff > 0) {
            StringBuilder sb = new StringBuilder(s);
            char[] whiteSpaces = new char[lengthDiff];
            Arrays.fill(whiteSpaces, c);
            sb.append(whiteSpaces);
            return sb.toString();
        }
        return s;
    }

    /**
     * For the input string <code>"Hello world!"</code> the result is<br>
     * <code>returnString[0] = "################"</code><br>
     * <code>returnString[1] = "# Hello world! #"</code><br>
     * <code>returnString[2] = "################"</code><br>
     *
     * @param message
     * @return
     */
    public static String[] getNumberSignSurroundedLogStrings(String message) {
        String[] returnValues = new String[3];
        message = "# " + message + " #";
        String diamondString = fillToLenght("", '#', message);
        returnValues[0] = diamondString;
        returnValues[1] = message;
        returnValues[2] = diamondString;
        return returnValues;
    }

    /**
     * Splits a string with a delimiter into a collection. All substrings will
     * be trimmed.
     *
     * @param s source string
     * @param delim delimiter to split the source string into parts
     * @return a set to which the parts of the splitted string are added
     */
    public static final Set<String> parseSet(String s, String delim) {
        return parseCollection(s, delim, new HashSet<>());
    }

    /**
     * Splits a string with a delimiter into a collection. All substrings will
     * be trimmed.
     *
     * @param s source string
     * @param delim delimiter to split the source string into parts
     * @return a list to which the parts of the splitted string are added
     */
    public static final List<String> parseList(String s, String delim) {
        return parseCollection(s, delim, new ArrayList<>());
    }

    /**
     * Splits a string with a delimiter into a collection. All substrings will
     * be trimmed.
     *
     * @param <T> type of the Collection
     * @param s source string
     * @param delim delimiter to split the source string into parts
     * @param collectionToFill on this collection the return values will be
     *            appended
     * @return the input collection to which the parts of the splitted string
     *         are added
     */
    public static final <T extends Collection<String>> T parseCollection(String s, String delim, T collectionToFill) {
        if (!Strings.isNullOrEmpty(s)) {
            String[] values = s.trim().split("\\s*\\" + delim + "\\s*");
            collectionToFill.addAll(Arrays.asList(values));
        }
        return collectionToFill;
    }

    /**
     * Concatenates the provided strings using the specified separator, ensuring
     * that null or empty strings are skipped and that the separator does not
     * appear at the end.
     *
     * @param separator the object to be used as the separator between the
     *            strings. The object's toString() method will be used to
     *            convert it to a String. If null, the method returns an empty
     *            string.
     * @param strings a variable number of String arguments to be concatenated.
     *            If null, the method returns an empty string. Null or empty
     *            strings within the array are ignored.
     * @return a single concatenated String with the specified separator between
     *         non-null and non-empty strings, or an empty string if no valid
     *         strings are provided. Example:
     *
     *         <pre>
     *         concatenate(", ", "Apple", null, "Banana", "Cherry", "")
     *         // returns "Apple, Banana, Cherry"
     *         </pre>
     */
    public static final String concatenate(Object separator, String... strings) {
        if (separator == null || strings == null) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        String sep = separator.toString();

        for (String str : strings) {
            if (str != null && !str.isEmpty()) {
                if (result.length() > 0) {
                    result.append(sep);
                }
                result.append(str);
            }
        }

        return result.toString();
    }

}
