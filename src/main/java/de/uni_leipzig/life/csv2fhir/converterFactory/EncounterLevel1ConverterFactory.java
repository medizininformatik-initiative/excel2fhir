package de.uni_leipzig.life.csv2fhir.converterFactory;

import org.apache.commons.csv.CSVRecord;

import de.uni_leipzig.imise.FHIRValidator;
import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterFactory;
import de.uni_leipzig.life.csv2fhir.ConverterResult;
import de.uni_leipzig.life.csv2fhir.TableColumnIdentifier;
import de.uni_leipzig.life.csv2fhir.converter.EncounterLevel1Converter;

public class EncounterLevel1ConverterFactory implements ConverterFactory {

    public static enum EncounterLevel1_Columns implements TableColumnIdentifier {
        Patient_ID {
            @Override
            public String toString() {
                return "Patient-ID";
            }
        },
        Versorgungsfall_Nr {
            @Override
            public String toString() {
                return "Versorgungsfall-Nr";
            }
            @Override
            public boolean isMandatory() {
                return false;
            }
        },
        Startdatum,
        Enddatum,
        Versorgungsfallklasse,
    }

    @Override
    public Converter create(CSVRecord record, ConverterResult result, FHIRValidator validator) throws Exception {
        return new EncounterLevel1Converter(record, result, validator);
    }

}
