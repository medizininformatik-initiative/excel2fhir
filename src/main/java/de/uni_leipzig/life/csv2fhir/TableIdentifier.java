package de.uni_leipzig.life.csv2fhir;

import static de.uni_leipzig.imise.validate.FHIRValidator.ValidationResultType.ERROR;
import static de.uni_leipzig.imise.validate.FHIRValidator.ValidationResultType.VALID;
import static de.uni_leipzig.life.csv2fhir.converter.EncounterLevel1Converter.DEFAULT_ENCOUNTER_ID_NUMBER;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.Resource;

import com.google.common.collect.ImmutableList;

import de.uni_leipzig.imise.validate.FHIRValidator;
import de.uni_leipzig.imise.validate.FHIRValidator.ValidationResultType;
import de.uni_leipzig.life.csv2fhir.converter.ConsentConverter.Consent_Columns;
import de.uni_leipzig.life.csv2fhir.converter.DiagnosisConverter.Diagnosis_Columns;
import de.uni_leipzig.life.csv2fhir.converter.EncounterLevel1Converter.EncounterLevel1_Columns;
import de.uni_leipzig.life.csv2fhir.converter.EncounterLevel2Converter.EncounterLevel2_Columns;
import de.uni_leipzig.life.csv2fhir.converter.MedicationConverter.Medication_Columns;
import de.uni_leipzig.life.csv2fhir.converter.ObservationLaboratoryConverter.ObservationLaboratory_Columns;
import de.uni_leipzig.life.csv2fhir.converter.ObservationVitalSignsConverter.ObservationVitalSigns_Columns;
import de.uni_leipzig.life.csv2fhir.converter.PersonConverter.Person_Columns;
import de.uni_leipzig.life.csv2fhir.converter.ProzedurConverter.Procedure_Columns;
import de.uni_leipzig.life.csv2fhir.converterFactory.ConsentConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.DiagnosisConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.EncounterLevel1ConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.EncounterLevel2ConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.MedicationConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.ObservationLaboratoryConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.ObservationVitalSignsConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.PersonConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.ProcedureConverterFactory;

/**
 * @author AXS (18.11.2021)
 */
public enum TableIdentifier {

    Person(Person_Columns.class, PersonConverterFactory.class),

    Versorgungsfall(EncounterLevel1_Columns.class, EncounterLevel1ConverterFactory.class),

    Abteilungsfall(EncounterLevel2_Columns.class, EncounterLevel2ConverterFactory.class),

    Laborbefund(ObservationLaboratory_Columns.class, ObservationLaboratoryConverterFactory.class),

    Diagnose(Diagnosis_Columns.class, DiagnosisConverterFactory.class),

    Prozedur(Procedure_Columns.class, ProcedureConverterFactory.class),

    Medikation(Medication_Columns.class, MedicationConverterFactory.class),

    Klinische_Dokumentation(ObservationVitalSigns_Columns.class, ObservationVitalSignsConverterFactory.class),

    Consent(Consent_Columns.class, ConsentConverterFactory.class) {
        @Override
        public String toString() {
            return Person.toString(); // Consent data are on the patient sheet
        }
    };

    /**
     * Table column identifier which are the same in all tables.
     */
    public static enum DefaultTableColumnNames implements TableColumnIdentifier {
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
            @Override
            public boolean isMandatory() {
                return false;
            }
        },
    }

    /** The class with the converter factory for this data type */
    private final Class<? extends ConverterFactory> converterFactoryClass;

    /** The converter factory for this data type */
    private ConverterFactory converterFactory;

    /** The class with the enum with the definition of the table columns */
    private final Class<? extends Enum<? extends TableColumnIdentifier>> columnIdentifiersClass;

    /**
     * @param columnIdentifiersClass
     * @param converterFactoryClass
     */
    private TableIdentifier(Class<? extends Enum<? extends TableColumnIdentifier>> columnIdentifiersClass, Class<? extends ConverterFactory> converterFactoryClass) {
        this.columnIdentifiersClass = columnIdentifiersClass;
        this.converterFactoryClass = converterFactoryClass;
    }

    /**
     * @return Enum where the list of toString() methods of the elements
     *         specifies the names of the table columns needed for conversion.
     * @see de.uni_leipzig.life.csv2fhir.ConverterFactory#getNeededColumns()
     */
    public Collection<Enum<? extends TableColumnIdentifier>> getMandatoryColumns() {
        ImmutableList.Builder<Enum<? extends TableColumnIdentifier>> mandatoryColumns = ImmutableList.builder();
        for (Enum<? extends TableColumnIdentifier> columnIndentifier : columnIdentifiersClass.getEnumConstants()) {
            TableColumnIdentifier tableColumnIdentifier = (TableColumnIdentifier) columnIndentifier;
            if (tableColumnIdentifier.isMandatory()) {
                // A cloumn can be mandatory but missing in the data. In this case
                // there must be defined a default value to provide the mandatory
                // value. If the column is mandatory and no default is defined
                // then the column will be added here as the strict mandatory column
                // which is needed to convert the table.
                if (tableColumnIdentifier.getDefaultIfMissing() == null) {
                    mandatoryColumns.add(columnIndentifier);
                }
            }
        }
        return mandatoryColumns.build();
    }

    /**
     * @return A collection with all column names of the table columns needed
     *         for conversion.
     */
    public Collection<String> getMandatoryColumnNames() {
        ImmutableList.Builder<String> mandatoryColumnNames = ImmutableList.builder();
        for (Object columnIndentifier : getMandatoryColumns()) {
            mandatoryColumnNames.add(columnIndentifier.toString());
        }
        return mandatoryColumnNames.build();
    }

    /**
     * @return the identifier for the PID column in the table
     */
    public TableColumnIdentifier getPIDColumnIdentifier() {
        return DefaultTableColumnNames.Patient_ID;
    }

    /**
     * @return the name of the PID colun in the table
     */
    public String getPIDColumnName() {
        return getPIDColumnIdentifier().toString();
    }

    /**
     * @param csvRecord
     * @param result
     * @param validator
     * @return
     * @throws Exception
     */
    public List<? extends Resource> convert(CSVRecord csvRecord, ConverterResult result, FHIRValidator validator) throws Exception {
        if (converterFactory == null) {
            converterFactory = createConverter();
        }
        Converter converter = converterFactory.create(csvRecord, result, validator);
        List<? extends Resource> resources = converter.convert(); //should never return null!
        //resources seems to be Immutable (we cannot remove elements) -> copy the valid elements to a new list
        List<Resource> validResources = new ArrayList<>();
        //validate every resource and remove if invalid
        for (int i = 0; i < resources.size(); i++) {
            Resource resource = resources.get(i);
            ValidationResultType validationResult = ERROR;
            if (resource != null) {
                validationResult = validator == null ? VALID : validator.validate(resource);
            }
            if (validationResult != ERROR) {
                validResources.add(resource);
            }
        }
        result.addAll(this, validResources);
        return validResources;
    }

    /**
     * @return
     * @throws Exception
     */
    private final ConverterFactory createConverter() throws Exception {
        Constructor<? extends ConverterFactory> emptyConverterFactoyryConstructor = converterFactoryClass.getConstructor();
        ConverterFactory converterFactory = emptyConverterFactoyryConstructor.newInstance();
        return converterFactory;
    }

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
        for (TableIdentifier csvFileName : values()) {
            csvFileNames.add(csvFileName.getCsvFileName());
        }
        return csvFileNames;
    }

    /**
     * @return
     */
    public static List<String> getExcelSheetNames() {
        List<String> excelSheetNames = new ArrayList<>();
        for (TableIdentifier csvFileName : values()) {
            excelSheetNames.add(csvFileName.getExcelSheetName());
        }
        return excelSheetNames;
    }

}
