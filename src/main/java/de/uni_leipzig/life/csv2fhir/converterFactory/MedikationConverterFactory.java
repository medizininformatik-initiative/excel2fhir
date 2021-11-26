package de.uni_leipzig.life.csv2fhir.converterFactory;

import org.apache.commons.csv.CSVRecord;

import de.uni_leipzig.imise.FHIRValidator;
import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterFactory;
import de.uni_leipzig.life.csv2fhir.converter.MedikationConverter;
import de.uni_leipzig.life.csv2fhir.utils.StringEqualsIgnoreCase;

/**
 * @author fheuschkel (02.11.2020), AXS (18.11.2021)
 */
public class MedikationConverterFactory implements ConverterFactory {

    /**
     *
     */
    public static enum NeededColumns {
        Patient_ID {
            @Override
            public String toString() {
                return "Patient-ID";
            }
        },
        Zeitstempel,
        FHIR_Resourcentyp,
        Medikationsplanart,
        Wirksubstanz_aus_Praeparat_Handelsname {
            @Override
            public String toString() {
                return "Wirksubstanz aus Präparat/Handelsname";
            }
        },
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
        ASK,
        FHIR_UserSelected,
        Darreichungsform,
        //Tagesdosis,
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
        //KombinationsAMI,
    }

    /**
     *
     */
    public static enum Medikationsplanart_Values implements StringEqualsIgnoreCase {
        Vor_Aufnahme,
        Am_Aufnahmetag,
        Im_Verlauf,
        Am_letztzen_Tag,
        Bei_Entlassung;
        @Override
        public String toString() {
            return super.toString().replace('_', ' ');
        }
    }

    @Override
    public Converter create(CSVRecord record, FHIRValidator validator) throws Exception {
        return new MedikationConverter(record, validator);
    }

    @Override
    public Enum<?>[] getNeededColumns() {
        return NeededColumns.values();
    }

    @Override
    public void resetConverterStaticValues() {
        MedikationConverter.reset();
    }

}
