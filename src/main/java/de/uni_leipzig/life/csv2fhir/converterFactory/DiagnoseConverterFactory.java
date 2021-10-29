package de.uni_leipzig.life.csv2fhir.converterFactory;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterFactory;
import de.uni_leipzig.life.csv2fhir.converter.DiagnoseConverter;
import org.apache.commons.csv.CSVRecord;

public class DiagnoseConverterFactory implements ConverterFactory {

    private static final String[] NEEDED_COLUMNS = {"Patient-ID", "Bezeichner", "ICD", "Dokumentationsdatum", "Typ"};

    @Override
    public Converter create(CSVRecord record) throws Exception {
        return new DiagnoseConverter(record);
    }

    @Override
    public String[] getNeededColumnNames() {
        return NEEDED_COLUMNS;
    }
}
