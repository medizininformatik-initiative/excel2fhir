package de.uni_leipzig.life.csv2fhir.converterFactory;

import static de.uni_leipzig.life.csv2fhir.converterFactory.EncounterLevel1ConverterFactory.DEFAULT_ENCOUNTER_ID_NUMBER;

import org.apache.commons.csv.CSVRecord;

import de.uni_leipzig.imise.FHIRValidator;
import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterFactory;
import de.uni_leipzig.life.csv2fhir.ConverterResult;
import de.uni_leipzig.life.csv2fhir.TableColumnIdentifier;
import de.uni_leipzig.life.csv2fhir.converter.ProzedurConverter;

public class ProcedureConverterFactory implements ConverterFactory {

    public static enum Procedure_Columns implements TableColumnIdentifier {
        Patient_ID {
            @Override
            public String toString() {
                return "Patient-ID";
            }
        },
        Versorgungsfall_Nr {
            @Override
            public String toString() {
                return "Versorgungsfall-Nr";
            }
            @Override
            public String getDefaultIfMissing() {
                return DEFAULT_ENCOUNTER_ID_NUMBER;
            }
        },
        Prozedurentext,
        Prozedurencode,
        Dokumentationsdatum,
    }

    @Override
    public Converter create(CSVRecord record, ConverterResult result, FHIRValidator validator) throws Exception {
        return new ProzedurConverter(record, result, validator);
    }

}