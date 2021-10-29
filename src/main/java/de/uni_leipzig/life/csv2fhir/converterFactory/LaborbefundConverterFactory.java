package de.uni_leipzig.life.csv2fhir.converterFactory;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterFactory;
import de.uni_leipzig.life.csv2fhir.converter.LaborbefundConverter;
import org.apache.commons.csv.CSVRecord;

public class LaborbefundConverterFactory implements ConverterFactory {

    private static final String[] NEEDED_COLUMNS = {"Patient-ID", "LOINC", "Parameter",
            "Messwert", "Einheit", "Zeitstempel (Abnahme)"}; //Not used: "Methode"

    @Override
    public Converter create(CSVRecord record) throws Exception {
        return new LaborbefundConverter(record);
    }

    @Override
    public String[] getNeededColumnNames() {
        return NEEDED_COLUMNS;
    }
}
