package de.uni_leipzig.life.csv2fhir.converterFactory;

import org.apache.commons.csv.CSVRecord;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterFactory;
import de.uni_leipzig.life.csv2fhir.converter.VersorgungsfallConverter;

public class VersorgungsfallConverterFactory implements ConverterFactory {

    private static final String[] NEEDED_COLUMNS = {"Patient-ID", "Versorgungsfallgrund (Aufnahmediagnose)", "Startdatum",
            "Enddatum", "Versorgungsfallklasse"};

    @Override
    public Converter create(CSVRecord record) throws Exception {
        return new VersorgungsfallConverter(record);
    }

    @Override
    public String[] getNeededColumnNames() {
        return NEEDED_COLUMNS;
    }
}
