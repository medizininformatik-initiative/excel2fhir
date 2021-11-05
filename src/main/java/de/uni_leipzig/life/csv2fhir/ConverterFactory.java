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

}
