package de.uni_leipzig.life.csv2fhir.converterFactory;

import org.apache.commons.csv.CSVRecord;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterFactory;
import de.uni_leipzig.life.csv2fhir.converter.ProzedurConverter;

public class ProzedurConverterFactory implements ConverterFactory {

    public enum NeededColumns {
        Patient_ID {
            @Override
            public String toString() {
                return "Patient-ID";
            }
        },
        Prozedurentext,
        Prozedurencode,
        Dokumentationsdatum,
    }

    @Override
    public Converter create(CSVRecord record) throws Exception {
        return new ProzedurConverter(record);
    }

    @Override
    public Enum<?>[] getNeededColumns() {
        return NeededColumns.values();
    }
}
