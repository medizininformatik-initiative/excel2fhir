package de.uni_leipzig.life.csv2fhir.converterFactory;

import org.apache.commons.csv.CSVRecord;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterFactory;
import de.uni_leipzig.life.csv2fhir.converter.MedikationConverter;

public class MedikationConverterFactory implements ConverterFactory {

    public enum NeededColumns {
        Patient_ID {
            @Override
            public String toString() {
                return "Patient-ID";
            }
        },
        Zeitstempel,
        FHIR_Resourcentyp,
        ATC_Code {
            @Override
            public String toString() {
                return "ATC Code";
            }
        },
        PZN_Code {
            @Override
            public String toString() {
                return "PZN Code";
            }
        },
        FHIR_UserSelected,
        Anzahl_Dosen_pro_Tag {
            @Override
            public String toString() {
                return "Anzahl Dosen pro Tag";
            }
        },
        Therapiestartdatum,
        Therapieendedatum,
        Einzeldosis,
        Einheit,
        Wirksubstanz_aus_Praeparat_Handelsname {
            @Override
            public String toString() {
                return "Wirksubstanz aus Präparat/Handelsname";
            }
        },
        //currently not used:
        //Methode,
        //Darreichungsform,
        //Tagesdosis,
        //KombinationsAMI,
        //ASK,
    }

    @Override
    public Converter create(CSVRecord record) throws Exception {
        return new MedikationConverter(record);
    }

    @Override
    public Enum<?>[] getNeededColumns() {
        return NeededColumns.values();
    }
}
