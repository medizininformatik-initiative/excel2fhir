package de.uni_leipzig.life.csv2fhir.converterFactory;

import org.apache.commons.csv.CSVRecord;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterFactory;
import de.uni_leipzig.life.csv2fhir.converter.VersorgungsfallConverter;

public class VersorgungsfallConverterFactory implements ConverterFactory {

    public enum NeededColumns {
        Patient_ID {
            @Override
            public String toString() {
                return "Patient-ID";
            }
        },
        Versorgungsfallgrund_Aufnahmediagnose {
            @Override
            public String toString() {
                return "Versorgungsfallgrund (Aufnahmediagnose)";
            }
        },
        Startdatum,
        Enddatum,
        Versorgungsfallklasse,
    }

    @Override
    public Converter create(CSVRecord record) throws Exception {
        return new VersorgungsfallConverter(record);
    }

    @Override
    public Enum<?>[] getNeededColumns() {
        return NeededColumns.values();
    }
}
