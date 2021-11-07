package de.uni_leipzig.life.csv2fhir;

import org.apache.commons.csv.CSVRecord;

/**
 * @author fheuschkel (02.11.2020)
 */
public interface ConverterFactory {

    /**  */
    Converter create(CSVRecord record) throws Exception;

    /**  */
    String[] getNeededColumnNames();

    /**
     * @param index
     * @return the name of the needed column at the index
     */
    public default String getNeededColumnName(int index) {
        return getNeededColumnNames()[index];
    }

    /**
     * @return the name of the column with the id (default 0)
     */
    public default String getIdColumnName() {
        return getNeededColumnName(0);
    }

}
