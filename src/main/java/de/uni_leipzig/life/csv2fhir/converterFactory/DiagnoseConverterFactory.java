package de.uni_leipzig.life.csv2fhir.converterFactory;

import org.apache.commons.csv.CSVRecord;

import de.uni_leipzig.imise.FHIRValidator;
import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterFactory;
import de.uni_leipzig.life.csv2fhir.converter.DiagnoseConverter;

public class DiagnoseConverterFactory implements ConverterFactory {

    public static enum NeededColumns {
        Patient_ID {
            @Override
            public String toString() {
                return "Patient-ID";
            }
        },
        Bezeichner,
        ICD,
        Dokumentationsdatum,
        Typ
    }

    @Override
    public Converter create(CSVRecord record, FHIRValidator validator) throws Exception {
        return new DiagnoseConverter(record, validator);
    }

    @Override
    public Enum<?>[] getNeededColumns() {
        return NeededColumns.values();
    }

}
