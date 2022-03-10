package de.uni_leipzig.life.csv2fhir;

import static de.uni_leipzig.imise.FHIRValidator.ValidationResultType.ERROR;
import static de.uni_leipzig.imise.FHIRValidator.ValidationResultType.VALID;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.Resource;

import com.google.common.collect.ImmutableList;

import de.uni_leipzig.imise.FHIRValidator;
import de.uni_leipzig.imise.FHIRValidator.ValidationResultType;
import de.uni_leipzig.life.csv2fhir.converterFactory.ConsentConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.ConsentConverterFactory.Consent_Columns;
import de.uni_leipzig.life.csv2fhir.converterFactory.DiagnosisConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.DiagnosisConverterFactory.Diagnosis_Columns;
import de.uni_leipzig.life.csv2fhir.converterFactory.EncounterLevel1ConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.EncounterLevel1ConverterFactory.EncounterLevel1_Columns;
import de.uni_leipzig.life.csv2fhir.converterFactory.EncounterLevel2ConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.EncounterLevel2ConverterFactory.EncounterLevel2_Columns;
import de.uni_leipzig.life.csv2fhir.converterFactory.MedicationConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.MedicationConverterFactory.Medication_Columns;
import de.uni_leipzig.life.csv2fhir.converterFactory.ObservationLaboratoryConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.ObservationLaboratoryConverterFactory.ObservationLaboratory_Columns;
import de.uni_leipzig.life.csv2fhir.converterFactory.ObservationVitalSignsConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.ObservationVitalSignsConverterFactory.ObservationVitalSigns_Columns;
import de.uni_leipzig.life.csv2fhir.converterFactory.PersonConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.PersonConverterFactory.Person_Columns;
import de.uni_leipzig.life.csv2fhir.converterFactory.ProcedureConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.ProcedureConverterFactory.Procedure_Columns;

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

    /** The class with the converter factory for this data type */
    private final Class<? extends ConverterFactory> converterFactoryClass;

    /** The converter factory for this data type */
    private ConverterFactory converterFactory;

    /** The class with the enum with the definition of the table columns */
    private final Class<? extends Enum<? extends TableColumnIdentifier>> columnIdentifiersClass;

    /** The column identifier for the patien ID in the table */
    private final Enum<?> pidColumnIdentifier;

    /**
     * @param columnIdentifiersClass
     * @param converterFactoryClass
     */
    private TableIdentifier(Class<? extends Enum<? extends TableColumnIdentifier>> columnIdentifiersClass, Class<? extends ConverterFactory> converterFactoryClass) {
        this.columnIdentifiersClass = columnIdentifiersClass;
        this.converterFactoryClass = converterFactoryClass;
        pidColumnIdentifier = columnIdentifiersClass.getEnumConstants()[0]; //should always declared as the first value!
    }

    /**
     * @return Enum where the list of toString() methods of the elements
     *         specifies the names of the table columns needed for conversion.
     * @see de.uni_leipzig.life.csv2fhir.ConverterFactory#getNeededColumns()
     */
    public Collection<Enum<? extends TableColumnIdentifier>> getMandatoryColumns() {
        ImmutableList.Builder<Enum<? extends TableColumnIdentifier>> mandatoryColumns = ImmutableList.builder();
        for (Enum<? extends TableColumnIdentifier> columnIndentifier : columnIdentifiersClass.getEnumConstants()) {
            if (((TableColumnIdentifier) columnIndentifier).isMandatory()) {
                mandatoryColumns.add(columnIndentifier);
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
    public final Enum<?> getPIDColumnIdentifier() {
        return pidColumnIdentifier;
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
        //validate every resource and remove if invalid
        for (int i = resources.size() - 1; i >= 0; i--) {
            Resource resource = resources.get(i);
            ValidationResultType validationResult = ERROR;
            if (resource != null) {
                validationResult = validator == null ? VALID : validator.validate(resource);
            }
            if (validationResult == ERROR) {
                resources.remove(i);
            }
        }

        result.addAll(this, resources);
        return resources;
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
