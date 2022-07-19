package de.uni_leipzig.life.csv2fhir.converterFactory;

import org.apache.commons.csv.CSVRecord;

import de.uni_leipzig.imise.validate.FHIRValidator;
import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterFactory;
import de.uni_leipzig.life.csv2fhir.ConverterResult;
import de.uni_leipzig.life.csv2fhir.TableColumnIdentifier;
import de.uni_leipzig.life.csv2fhir.converter.ObservationLaboratoryConverter;

public class ObservationLaboratoryConverterFactory implements ConverterFactory {

    public static enum ObservationLaboratory_Columns implements TableColumnIdentifier {
        LOINC,
        Parameter,
        Messwert,
        Einheit,
        Zeitstempel_Abnahme {
            @Override
            public String toString() {
                return "Zeitstempel (Abnahme)";
            }
        },
        //Methode //not used
    }

    @Override
    public Converter create(CSVRecord record, ConverterResult result, FHIRValidator validator) throws Exception {
        return new ObservationLaboratoryConverter(record, result, validator);
    }

}
