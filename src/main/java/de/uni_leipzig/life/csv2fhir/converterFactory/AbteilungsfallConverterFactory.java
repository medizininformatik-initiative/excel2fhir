package de.uni_leipzig.life.csv2fhir.converterFactory;

import org.apache.commons.csv.CSVRecord;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterFactory;
import de.uni_leipzig.life.csv2fhir.converter.AbteilungsfallConverter;

public class AbteilungsfallConverterFactory implements ConverterFactory {

    public enum NeededColumns {
        Patient_ID {
            @Override
            public String toString() {
                return "Patient-ID";
            }
        },
        Startdatum,
        Enddatum,
        Fachabteilung
    }

    @Override
    public Converter create(CSVRecord record) throws Exception {
        return new AbteilungsfallConverter(record);
    }

    @Override
    public Enum<?>[] getNeededColumns() {
        return NeededColumns.values();
    }
}
