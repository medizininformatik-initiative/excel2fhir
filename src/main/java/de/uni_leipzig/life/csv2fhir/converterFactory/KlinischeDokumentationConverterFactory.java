package de.uni_leipzig.life.csv2fhir.converterFactory;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterFactory;
import de.uni_leipzig.life.csv2fhir.converter.KlinischeDokumentationConverter;
import org.apache.commons.csv.CSVRecord;

public class KlinischeDokumentationConverterFactory implements ConverterFactory {

    private static final String[] NEEDED_COLUMNS = {"Patient-ID", "Bezeichner", "LOINC", "Wert", "Einheit", "Zeitstempel"};

    @Override
    public Converter create(CSVRecord record) throws Exception {
        return new KlinischeDokumentationConverter(record);
    }

    @Override
    public String[] getNeededColumnNames() {
        return NEEDED_COLUMNS;
    }
}
