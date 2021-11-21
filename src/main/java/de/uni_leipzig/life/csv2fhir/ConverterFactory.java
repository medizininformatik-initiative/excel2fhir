package de.uni_leipzig.life.csv2fhir;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVRecord;

/**
 * @author fheuschkel (02.11.2020)
 */
public interface ConverterFactory {

    /** Creates a new Converter for a CSVRecort */
    Converter create(CSVRecord record) throws Exception;

    /**
     * Enum where the list of toString() methods of the elements specifies the
     * names of the table columns needed for conversion.
     */
    Enum<?>[] getNeededColumns();

    /**
     * @param index
     * @return the name of the needed column at the index
     */
    public default String getNeededColumnName(int index) {
        return getNeededColumns()[index].toString();
    }

    /**
     * @return A list with all column names of the table columns needed for
     *         conversion.
     */
    public default List<String> getNeededColumnNames() {
        List<String> neededColumnNames = new ArrayList<>();
        Enum<?>[] neededColumns = getNeededColumns();
        for (Enum<?> enumValue : neededColumns) {
            neededColumnNames.add(enumValue.toString());
        }
        return neededColumnNames;
    }

}
