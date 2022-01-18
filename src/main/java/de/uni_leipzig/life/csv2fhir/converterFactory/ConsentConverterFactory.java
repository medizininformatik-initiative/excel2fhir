package de.uni_leipzig.life.csv2fhir.converterFactory;

import org.apache.commons.csv.CSVRecord;

import de.uni_leipzig.imise.FHIRValidator;
import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterFactory;
import de.uni_leipzig.life.csv2fhir.ConverterResult;
import de.uni_leipzig.life.csv2fhir.TableColumnIdentifier;
import de.uni_leipzig.life.csv2fhir.converter.ConsentConverter;

public class ConsentConverterFactory implements ConverterFactory {

    public static enum Consent_Columns implements TableColumnIdentifier {
        Patient_ID {
            @Override
            public String toString() {
                return "Patient-ID";
            }
        },
        Datum_Einwilligung,
        PDAT_Einwilligung,
        KKDAT_retro_Einwilligung,
        KKDAT_Einwilligung,
        BIOMAT_Einwilligung,
        BIOMAT_Zusatz_Einwilligung;

        @Override
        public String toString() {
            return name().replace('_', ' ');
        }

    }

    @Override
    public Converter create(CSVRecord record, ConverterResult result, FHIRValidator validator) throws Exception {
        return new ConsentConverter(record, result, validator);
    }

}
