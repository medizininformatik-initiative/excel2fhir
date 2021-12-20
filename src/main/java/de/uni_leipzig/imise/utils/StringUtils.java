package de.uni_leipzig.imise.utils;

import java.util.Arrays;

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

}
