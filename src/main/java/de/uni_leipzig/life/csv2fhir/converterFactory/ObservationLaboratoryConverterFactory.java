package de.uni_leipzig.life.csv2fhir.converterFactory;

import org.apache.commons.csv.CSVRecord;

import de.uni_leipzig.imise.validate.FHIRValidator;
import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterFactory;
import de.uni_leipzig.life.csv2fhir.ConverterResult;
import de.uni_leipzig.life.csv2fhir.converter.ObservationLaboratoryConverter;

/**
 * @author jheuschkel (19.10.2020), AXS (05.11.2021)
 */
public class ObservationLaboratoryConverterFactory implements ConverterFactory {

    @Override
    public Converter create(CSVRecord record, ConverterResult result, FHIRValidator validator) throws Exception {
        return new ObservationLaboratoryConverter(record, result, validator);
    }

}
