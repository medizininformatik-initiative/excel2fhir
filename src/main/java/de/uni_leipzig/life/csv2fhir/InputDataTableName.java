package de.uni_leipzig.life.csv2fhir;

import java.util.ArrayList;
import java.util.List;

import de.uni_leipzig.life.csv2fhir.converterFactory.AbteilungsfallConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.DiagnoseConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.KlinischeDokumentationConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.LaborbefundConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.MedikationConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.PersonConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.ProzedurConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.VersorgungsfallConverterFactory;

/**
 * @author AXS (18.11.2021)
 */
public enum InputDataTableName {
    Person {
        @Override
        public ConverterFactory getConverterFactory() {
            return new PersonConverterFactory();
        }
    },
    Versorgungsfall {
        @Override
        public ConverterFactory getConverterFactory() {
            return new VersorgungsfallConverterFactory();
        }
    },
    Abteilungsfall {
        @Override
        public ConverterFactory getConverterFactory() {
            return new AbteilungsfallConverterFactory();
        }
    },
    Laborbefund {
        @Override
        public ConverterFactory getConverterFactory() {
            return new LaborbefundConverterFactory();
        }
    },
    Diagnose {
        @Override
        public ConverterFactory getConverterFactory() {
            return new DiagnoseConverterFactory();
        }
    },
    Prozedur {
        @Override
        public ConverterFactory getConverterFactory() {
            return new ProzedurConverterFactory();
        }
    },
    Medikation {
        @Override
        public ConverterFactory getConverterFactory() {
            return new MedikationConverterFactory();
        }
    },
    Klinische_Dokumentation {
        @Override
        public ConverterFactory getConverterFactory() {
            return new KlinischeDokumentationConverterFactory();
        }
    };

    /**
     * @return the {@link ConverterFactory} for this file type
     */
    public abstract ConverterFactory getConverterFactory();

    @Override
    public String toString() {
        return name().replace('_', ' ');
    }

    /**
     * @return
     */
    public String getCsvFileName() {
        return toString() + ".csv";
    }

    /**
     * @return
     */
    public String getExcelSheetName() {
        return toString();
    }

    /**
     * @return
     */
    public static List<String> getCsvFileNames() {
        List<String> csvFileNames = new ArrayList<>();
        for (InputDataTableName csvFileName : values()) {
            csvFileNames.add(csvFileName.getCsvFileName());
        }
        return csvFileNames;
    }

    /**
     * @return
     */
    public static List<String> getExcelSheetNames() {
        List<String> excelSheetNames = new ArrayList<>();
        for (InputDataTableName csvFileName : values()) {
            excelSheetNames.add(csvFileName.getExcelSheetName());
        }
        return excelSheetNames;
    }

}
