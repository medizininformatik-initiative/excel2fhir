package de.uni_leipzig.life.csv2fhir.converterFactory;

import org.apache.commons.csv.CSVRecord;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterFactory;
import de.uni_leipzig.life.csv2fhir.converter.MedikationConverter;

public class MedikationConverterFactory implements ConverterFactory {

    private static final String[] NEEDED_COLUMNS = {"Patient-ID", "Zeitstempel", "FHIR_Resourcentyp",
            "ATC Code", "PZN Code", "FHIR_UserSelected",
            "Anzahl Dosen pro Tag", "Therapiestartdatum",
            "Therapieendedatum", "Einzeldosis", "Einheit",
            "Wirksubstanz aus Präparat/Handelsname"};
    //"Medikationsplanart", "Darreichungsform", "Tagesdosis", "KombinationsAMI", "ASK" not used!

    @Override
    public Converter create(CSVRecord record) throws Exception {
        return new MedikationConverter(record);
    }

    @Override
    public String[] getNeededColumnNames() {
        return NEEDED_COLUMNS;
    }
}
