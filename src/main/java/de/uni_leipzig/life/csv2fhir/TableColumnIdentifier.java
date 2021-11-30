package de.uni_leipzig.life.csv2fhir;

/**
 * @author AXS (30.11.2021)
 */
public interface TableColumnIdentifier {

    /**
     * @return true if this column in mandatory in the table. The tabke is not
     *         valid/parseable if this column is missing. Interface default
     *         return value is <code>true</code>.
     */
    public default boolean isMandatory() {
        return true;
    }

}
