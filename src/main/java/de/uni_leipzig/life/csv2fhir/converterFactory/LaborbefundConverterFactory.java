package de.uni_leipzig.life.csv2fhir.converterFactory;

import org.apache.commons.csv.CSVRecord;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterFactory;
import de.uni_leipzig.life.csv2fhir.converter.LaborbefundConverter;

public class LaborbefundConverterFactory implements ConverterFactory {

    public static enum NeededColumns {
        Patient_ID {
            @Override
            public String toString() {
                return "Patient-ID";
            }
        },
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
    public Converter create(CSVRecord record) throws Exception {
        return new LaborbefundConverter(record);
    }

    @Override
    public Enum<?>[] getNeededColumns() {
        return NeededColumns.values();
    }
}
