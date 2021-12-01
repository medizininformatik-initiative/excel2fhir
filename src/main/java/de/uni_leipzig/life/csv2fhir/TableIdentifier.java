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
import de.uni_leipzig.life.csv2fhir.converterFactory.AbteilungsfallConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.AbteilungsfallConverterFactory.Abteilungsfall_Columns;
import de.uni_leipzig.life.csv2fhir.converterFactory.DiagnoseConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.DiagnoseConverterFactory.Diagnose_Columns;
import de.uni_leipzig.life.csv2fhir.converterFactory.KlinischeDokumentationConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.KlinischeDokumentationConverterFactory.KlinischeDokumentation_Columns;
import de.uni_leipzig.life.csv2fhir.converterFactory.LaborbefundConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.LaborbefundConverterFactory.Laborbefund_Columns;
import de.uni_leipzig.life.csv2fhir.converterFactory.MedikationConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.MedikationConverterFactory.Medikation_Columns;
import de.uni_leipzig.life.csv2fhir.converterFactory.PersonConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.PersonConverterFactory.Person_Columns;
import de.uni_leipzig.life.csv2fhir.converterFactory.ProzedurConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.ProzedurConverterFactory.Prozedur_Columns;
import de.uni_leipzig.life.csv2fhir.converterFactory.VersorgungsfallConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.VersorgungsfallConverterFactory.Versorgungsfall_Columns;

/**
 * @author AXS (18.11.2021)
 */
public enum TableIdentifier {

    Person(Person_Columns.class, PersonConverterFactory.class),

    Versorgungsfall(Versorgungsfall_Columns.class, VersorgungsfallConverterFactory.class),

    Abteilungsfall(Abteilungsfall_Columns.class, AbteilungsfallConverterFactory.class),

    Laborbefund(Laborbefund_Columns.class, LaborbefundConverterFactory.class),

    Diagnose(Diagnose_Columns.class, DiagnoseConverterFactory.class),

    Prozedur(Prozedur_Columns.class, ProzedurConverterFactory.class),

    Medikation(Medikation_Columns.class, MedikationConverterFactory.class),

    Klinische_Dokumentation(KlinischeDokumentation_Columns.class, KlinischeDokumentationConverterFactory.class);

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
