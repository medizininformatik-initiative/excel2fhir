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
import de.uni_leipzig.life.csv2fhir.converter.ConsentConverter;
import de.uni_leipzig.life.csv2fhir.converter.ConsentConverter.Consent_Columns;
import de.uni_leipzig.life.csv2fhir.converter.DiagnosisConverter;
import de.uni_leipzig.life.csv2fhir.converter.DiagnosisConverter.Diagnosis_Columns;
import de.uni_leipzig.life.csv2fhir.converter.DocumentReferenceConverter;
import de.uni_leipzig.life.csv2fhir.converter.DocumentReferenceConverter.DocumentReference_Columns;
import de.uni_leipzig.life.csv2fhir.converter.EncounterLevel1Converter;
import de.uni_leipzig.life.csv2fhir.converter.EncounterLevel1Converter.EncounterLevel1_Columns;
import de.uni_leipzig.life.csv2fhir.converter.EncounterLevel2Converter;
import de.uni_leipzig.life.csv2fhir.converter.EncounterLevel2Converter.EncounterLevel2_Columns;
import de.uni_leipzig.life.csv2fhir.converter.MedicationConverter;
import de.uni_leipzig.life.csv2fhir.converter.MedicationConverter.Medication_Columns;
import de.uni_leipzig.life.csv2fhir.converter.ObservationLaboratoryConverter;
import de.uni_leipzig.life.csv2fhir.converter.ObservationLaboratoryConverter.ObservationLaboratory_Columns;
import de.uni_leipzig.life.csv2fhir.converter.ObservationVitalSignsConverter;
import de.uni_leipzig.life.csv2fhir.converter.ObservationVitalSignsConverter.ObservationVitalSigns_Columns;
import de.uni_leipzig.life.csv2fhir.converter.PersonConverter;
import de.uni_leipzig.life.csv2fhir.converter.PersonConverter.Person_Columns;
import de.uni_leipzig.life.csv2fhir.converter.ProzedurConverter;
import de.uni_leipzig.life.csv2fhir.converter.ProzedurConverter.Procedure_Columns;

/**
 * @author AXS (18.11.2021)
 */
public enum TableIdentifier {

    Person(Person_Columns.class, PersonConverter.class),

    Versorgungsfall(EncounterLevel1_Columns.class, EncounterLevel1Converter.class),

    Abteilungsfall(EncounterLevel2_Columns.class, EncounterLevel2Converter.class),

    Laborbefund(ObservationLaboratory_Columns.class, ObservationLaboratoryConverter.class),

    Diagnose(Diagnosis_Columns.class, DiagnosisConverter.class),

    Prozedur(Procedure_Columns.class, ProzedurConverter.class),

    DocumentReference(DocumentReference_Columns.class, DocumentReferenceConverter.class),

    Medikation(Medication_Columns.class, MedicationConverter.class),

    Klinische_Dokumentation(ObservationVitalSigns_Columns.class, ObservationVitalSignsConverter.class),

    Consent(Consent_Columns.class, ConsentConverter.class) {
        @Override
        public String toString() {
            return Person.toString(); // Consent data are on the patient sheet
        }
    },

    Konvertierungsoptionen {
        @Override
        protected String getTableNamePattern() {
            //we can have multiple table sheets with converter options
            return ".*" + toString() + ".*";
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
        };
    }

    /** The class with the converter factory for this data type */
    private Constructor<? extends Converter> converterConstructor = null;

    /** The class with the enum with the definition of the table columns */
    private final Class<? extends Enum<? extends TableColumnIdentifier>> columnIdentifiersClass;

    private TableIdentifier() {
        this(null, null);
    }

    /**
     * @param columnIdentifiersClass
     * @param converterClass
     */
    private TableIdentifier(Class<? extends Enum<? extends TableColumnIdentifier>> columnIdentifiersClass, Class<? extends Converter> converterClass) {
        this.columnIdentifiersClass = columnIdentifiersClass;
        try {
            if (converterClass != null) {
                converterConstructor = converterClass.getConstructor(CSVRecord.class, ConverterResult.class, FHIRValidator.class, ConverterOptions.class);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected String getTableNamePattern() {
        return toString();
    }

    /**
     * @return Enum where the list of toString() methods of the elements
     *         specifies the names of the table columns needed for conversion.
     * @see de.uni_leipzig.life.csv2fhir.ConverterFactory#getNeededColumns()
     */
    public Collection<Enum<? extends TableColumnIdentifier>> getMandatoryColumns() {
        ImmutableList.Builder<Enum<? extends TableColumnIdentifier>> mandatoryColumns = ImmutableList.builder();
        if (columnIdentifiersClass != null) {
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
    protected TableColumnIdentifier getPIDColumnIdentifier() {
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
     * @param options
     * @return
     * @throws Exception
     */
    public List<? extends Resource> convert(CSVRecord csvRecord, ConverterResult result, FHIRValidator validator, ConverterOptions options) throws Exception {
        Converter converter = converterConstructor.newInstance(csvRecord, result, validator, options);
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

    @Override
    public String toString() {
        return name().replace('_', ' ');
    }

    /**
     * @param baseName
     * @return
     */
    public String getCsvFileName(String baseName) {
        return baseName + toString() + ".csv";
    }

    /**
     * @param baseName
     * @return
     */
    public static List<String> getCsvFileNames(String baseName) {
        List<String> csvFileNames = new ArrayList<>();
        for (TableIdentifier csvFileName : values()) {
            csvFileNames.add(csvFileName.getCsvFileName(baseName));
        }
        return csvFileNames;
    }

    /**
     * @return a list of the accepted Excel Sheet Names. These names are
     *         interpreted as regular expressions.
     */
    public static List<String> getExcelSheetNamePatterns() {
        List<String> acceptedSheetNamePatterns = new ArrayList<>();
        for (TableIdentifier csvFileName : values()) {
            acceptedSheetNamePatterns.add(csvFileName.getTableNamePattern());
        }
        return acceptedSheetNamePatterns;
    }

    /**
     * @return <code>true</code> if this table identifier has convertable
     *         columns and a constructor fpr the converter.
     */
    public boolean isConvertableTableSheet() {
        return columnIdentifiersClass != null && converterConstructor != null;
    }

}
