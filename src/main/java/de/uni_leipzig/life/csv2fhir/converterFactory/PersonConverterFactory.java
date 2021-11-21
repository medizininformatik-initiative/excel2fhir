package de.uni_leipzig.life.csv2fhir.converterFactory;

import org.apache.commons.csv.CSVRecord;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterFactory;
import de.uni_leipzig.life.csv2fhir.converter.PersonConverter;

public class PersonConverterFactory implements ConverterFactory {

    public static enum NeededColumns {
        Patient_ID {
            @Override
            public String toString() {
                return "Patient-ID";
            }
        },
        Vorname,
        Nachname,
        Anschrift,
        Geburtsdatum,
        Geschlecht,
        Krankenkasse
    }

    @Override
    public Converter create(CSVRecord record) throws Exception {
        return new PersonConverter(record);
    }

    @Override
    public Enum<?>[] getNeededColumns() {
        return NeededColumns.values();
    }

}
