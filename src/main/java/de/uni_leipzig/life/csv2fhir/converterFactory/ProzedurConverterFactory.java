package de.uni_leipzig.life.csv2fhir.converterFactory;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterFactory;
import de.uni_leipzig.life.csv2fhir.converter.ProzedurConverter;
import org.apache.commons.csv.CSVRecord;

public class ProzedurConverterFactory implements ConverterFactory {

    private static final String[] NEEDED_COLUMNS = {"Patient-ID", "Prozedurentext", "Prozedurencode", "Dokumentationsdatum"};

    @Override
    public Converter create(CSVRecord record) {
        return new ProzedurConverter(record);
    }

    @Override
    public String[] getNeededColumnNames() {
        return NEEDED_COLUMNS;
    }
}
