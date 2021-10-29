package de.uni_leipzig.life.csv2fhir;

import org.apache.commons.csv.CSVRecord;

public interface ConverterFactory {

    Converter create(CSVRecord record) throws Exception;

    String[] getNeededColumnNames();
}
