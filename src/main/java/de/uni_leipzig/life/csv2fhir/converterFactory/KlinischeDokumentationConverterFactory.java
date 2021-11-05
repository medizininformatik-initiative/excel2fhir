package de.uni_leipzig.life.csv2fhir.converterFactory;

import org.apache.commons.csv.CSVRecord;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterFactory;
import de.uni_leipzig.life.csv2fhir.converter.KlinischeDokumentationConverter;

public class KlinischeDokumentationConverterFactory implements ConverterFactory {

    private static final String[] NEEDED_COLUMNS = {"Patient-ID", "Bezeichner", "LOINC", "Wert", "Einheit", "Zeitstempel"};

    @Override
    public Converter create(final CSVRecord record) throws Exception {
        return new KlinischeDokumentationConverter(record);
    }

    @Override
    public String[] getNeededColumnNames() {
        return NEEDED_COLUMNS;
    }
}
