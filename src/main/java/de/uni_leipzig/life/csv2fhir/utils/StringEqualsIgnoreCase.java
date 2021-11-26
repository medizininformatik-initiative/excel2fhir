package de.uni_leipzig.life.csv2fhir.utils;

/**
 * @author AXS (26.11.2021)
 */
public interface StringEqualsIgnoreCase {

    /**
     * @param s
     * @return <code>true</code> if the given string is case insensitive equals
     *         to this.toString().
     */
    public default boolean equals(String s) {
        return toString().equalsIgnoreCase(s);
    }

}
