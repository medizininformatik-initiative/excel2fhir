package de.uni_leipzig.life.csv2fhir.converterFactory;

import org.apache.commons.csv.CSVRecord;

import de.uni_leipzig.imise.validate.FHIRValidator;
import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterFactory;
import de.uni_leipzig.life.csv2fhir.ConverterResult;
import de.uni_leipzig.life.csv2fhir.TableColumnIdentifier;
import de.uni_leipzig.life.csv2fhir.converter.EncounterLevel1Converter;

public class EncounterLevel1ConverterFactory implements ConverterFactory {

    /**
     * Value that will be set if the mandatory column "Versorgungsfall-Nr" is
     * missing in the data table sheets.
     */
    public static final String DEFAULT_ENCOUNTER_ID_NUMBER = "1";

    public static enum EncounterLevel1_Columns implements TableColumnIdentifier {
        Startdatum,
        Enddatum,
        Versorgungsfallklasse,
    }

    @Override
    public Converter create(CSVRecord record, ConverterResult result, FHIRValidator validator) throws Exception {
        return new EncounterLevel1Converter(record, result, validator);
    }

}
