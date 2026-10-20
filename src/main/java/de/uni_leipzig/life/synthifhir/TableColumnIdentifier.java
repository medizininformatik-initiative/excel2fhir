package de.uni_leipzig.life.synthifhir;

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
     * @param potencialTableColumnIdentifier
     * @return <code>true</code> if the {@link Enum} is an
     *         {@link TableColumnIdentifier} and this TableColumnIdentifier returns
     *         <code>true</code> on {@link #isMandatory()}.
     */
    public static boolean isMandatory(Enum<?> potencialTableColumnIdentifier) {
        return potencialTableColumnIdentifier instanceof TableColumnIdentifier
                && ((TableColumnIdentifier) potencialTableColumnIdentifier).isMandatory();
    }

}
