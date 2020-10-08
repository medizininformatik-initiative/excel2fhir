package de.uni_leipzig.life.csv2fhir.converterFactory;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterFactory;
import de.uni_leipzig.life.csv2fhir.converter.PersonConverter;
import org.apache.commons.csv.CSVRecord;

public class PersonConverterFactory implements ConverterFactory {

    private static final String[] NEEDED_COLUMNS =
            {"Patient-ID", "Vorname", "Nachname", "Anschrift", "Geburtsdatum",
                    "Geschlecht", "Krankenkasse"};

    public Converter create(CSVRecord record) {
        return new PersonConverter(record);
    }

    @Override
    public String[] getNeededColumnNames() {
        return NEEDED_COLUMNS;
    }
}
