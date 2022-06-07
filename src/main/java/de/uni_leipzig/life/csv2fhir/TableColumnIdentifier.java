package de.uni_leipzig.life.csv2fhir;

/**
 * @author AXS (30.11.2021)
 */
public interface TableColumnIdentifier {

    /**
     * @return <code>true</code> if this column in mandatory in the table. The
     *         table is not valid/parseable if this column is missing. Interface
     *         default return value is <code>true</code>.
     */
    public default boolean isMandatory() {
        return true;
    }

    /**
     * @return If this value is absent but marked as mandatory then this default
     *         value will be set. <code>null</code> is not a valid default value
     *         so this will cause an error.
     */
    public default String getDefaultIfMissing() {
        return null;
    }

}
