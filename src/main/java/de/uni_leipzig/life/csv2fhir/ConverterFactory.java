package de.uni_leipzig.life.csv2fhir;

import org.apache.commons.csv.CSVRecord;

import de.uni_leipzig.imise.FHIRValidator;

/**
 * @author fheuschkel (02.11.2020), AXS (18.11.2021)
 */
public interface ConverterFactory {

    /**
     * Creates a new Converter for a CSVRecort
     *
     * @param record
     * @param result
     * @param validator
     * @return
     * @throws Exception
     */
    Converter create(CSVRecord record, ConverterResult result, FHIRValidator validator) throws Exception;

}
