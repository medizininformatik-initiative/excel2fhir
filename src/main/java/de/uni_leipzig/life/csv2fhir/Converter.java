package de.uni_leipzig.life.csv2fhir;

import static de.uni_leipzig.life.csv2fhir.BundleFunctions.createReference;
import static de.uni_leipzig.life.csv2fhir.TableColumnIdentifier.isMandatory;
import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Person;
import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Versorgungsfall;
import static de.uni_leipzig.life.csv2fhir.utils.DateUtil.parseDateType;
import static org.apache.logging.log4j.util.Strings.isBlank;
import static org.hl7.fhir.r4.model.codesystems.DataAbsentReason.NOTAPPLICABLE;
import static org.hl7.fhir.r4.model.codesystems.DataAbsentReason.UNKNOWN;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Factory;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Quantity.QuantityComparator;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.codesystems.DataAbsentReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import de.uni_leipzig.UcumMapper;
import de.uni_leipzig.imise.utils.Excel2Csv;
import de.uni_leipzig.imise.utils.Sys;
import de.uni_leipzig.imise.validate.FHIRValidator;
import de.uni_leipzig.life.csv2fhir.TableIdentifier.DefaultTableColumnNames;
import de.uni_leipzig.life.csv2fhir.utils.DateUtil;

/**
 * @author fheuschkel (02.11.2020), AXS (18.11.2021)
 */
public abstract class Converter {

    /**  */
    protected static Logger LOG = LoggerFactory.getLogger(Converter.class);

    /** Char of whitesace */
    private static final char WHITE_SPACE = (char) 32;

    /** An empty list as default return value for empty records */
    private static final List<? extends Resource> EMPTY_RESOURCE_LIST = ImmutableList.of();

    /**
     * Specifies how to handle missing values.
     */
    public static enum EmptyRecordValueErrorLevel {
        /** Throw error on missing value */
        ERROR,
        /** Print warning on missing value */
        WARNING,
        /** Ignore missing value */
        IGNORE,
    }

    /**  */
    public static final Extension DATA_ABSENT_REASON_UNKNOWN = createDataAbsentReason(UNKNOWN);

    /**  */
    public static final Extension DATA_ABSENT_REASON_NOTAPPLICABLE = createDataAbsentReason(NOTAPPLICABLE);

    /**  */
    final String pid;

    /**  */
    final String encounterID;

    /**  */
    final String dizID;

    /**  */
    private final CSVRecord record;

    /**  */
    protected final ConverterResult result;

    /**
     * Validator to validate resources. Can be <code>null</code>. Can be
     * <code>null</code> if nothing should be validated.
     */
    protected final FHIRValidator validator;

    /**
     * The enum with the column identifiers (initialized by refelection).
     */
    private final Class<? extends Enum<? extends TableColumnIdentifier>> columnIdentifiersClass;

    /**
     * @param record
     * @param result
     * @param validator Validator to validate resources. Can be
     *            <code>null</code> if nothing should be validated.
     * @throws Exception
     */
    public Converter(CSVRecord record, ConverterResult result, @Nullable FHIRValidator validator) throws Exception {
        this.record = record;
        this.result = result;
        this.validator = validator;
        pid = parsePatientId();
        encounterID = parseEncounterId();
        dizID = pid.toUpperCase().replaceAll("[^A-Z]", "");
        columnIdentifiersClass = reflectColumnIdentifiersClass();
    }

    /**
     * @return
     * @throws Exception
     */
    public List<? extends Resource> convert() throws Exception {
        if (isEmptyCSVRecord()) {
            return EMPTY_RESOURCE_LIST;
        }
        return convertInternal();
    }

    /**
     * @return
     * @throws Exception
     */
    protected abstract List<? extends Resource> convertInternal() throws Exception;

    /**
     * Checks if the records contains no (valid) values. A value is valid if it
     * is not null and not only consists of whitespace characters or minus signs
     * '-'.
     *
     * @return <code>true</code> if the record contains no values.
     */
    private boolean isEmptyCSVRecord() {
        for (Enum<? extends TableColumnIdentifier> columnIndentifier : columnIdentifiersClass.getEnumConstants()) {
            String value = record.get(columnIndentifier);
            if (value != null && !isBlank(value.replace('-', WHITE_SPACE))) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return The class with the enum with the definition of the table columns
     */
    public Class<? extends Enum<? extends TableColumnIdentifier>> getColumnIdentifiersClass() {
        return columnIdentifiersClass;
    }

    /**
     * @return The class with the enum with the definition of the table columns
     */
    private Class<? extends Enum<? extends TableColumnIdentifier>> reflectColumnIdentifiersClass() {
        return reflectColumnIdentifiersClass(getClass());
    }

    /**
     * @param converterClass
     * @return The class with the enum with the definition of the table columns
     *         in the passed converter class
     */
    @SuppressWarnings("unchecked") //it's checked
    public static Class<? extends Enum<? extends TableColumnIdentifier>> reflectColumnIdentifiersClass(Class<? extends Converter> converterClass) {
        for (Class<?> declaredClass : converterClass.getDeclaredClasses()) {
            if (Enum.class.isAssignableFrom(declaredClass) && TableColumnIdentifier.class.isAssignableFrom(declaredClass)) {
                return (Class<? extends Enum<? extends TableColumnIdentifier>>) declaredClass.asSubclass(Enum.class);
            }
        }
        return null;
    }

    /**
     * @param resource
     * @return <code>true</code> if the validation is not to be performed or the
     *         validation does not find an error.
     */
    protected final boolean isValid(Resource resource) {
        return validator == null || !validator.validate(resource).isError();
    }

    /**
     * @param msg
     * @throws Exception
     */
    public void error(String msg) throws Exception {
        throw new Exception("Error on " + getLogMessageBody(msg));
    }

    /**
     * @param msg
     */
    public void err(String msg) {
        err(msg, 1);
    }

    /**
     * @param msg
     */
    protected void err(String msg, int stackTraceBackwardSteps) {
        LOG.error(getLogMessageBody(msg) + "     " + Sys.getStackTraceStep(3 + stackTraceBackwardSteps));
    }

    /**
     * @param msg
     */
    public void warning(String msg) {
        warning(msg, 1);
    }

    /**
     * @param msg
     */
    protected void warning(String msg, int stackTraceBackwardSteps) {
        LOG.warn(getLogMessageBody(msg) + "     " + Sys.getStackTraceStep(3 + stackTraceBackwardSteps));
    }

    /**
     * @param msg
     */
    public void info(String msg) {
        warning(msg, 1);
    }

    /**
     * @param msg
     */
    protected void info(String msg, int stackTraceBackwardSteps) {
        LOG.info(getLogMessageBody(msg) + "     " + Sys.getStackTraceStep(3 + stackTraceBackwardSteps));
    }

    /**
     * @param msg
     * @return
     */
    protected String getLogMessageBody(String msg) {
        return getClass().getSimpleName().replaceFirst("Converter", "") + ": " + msg + ":" + record.getRecordNumber() + "! "
                + record.toString();
    }

    /**
     * @return
     */
    public String getPatientId() {
        return pid;
    }

    /**
     * @return
     * @throws Exception
     */
    public final String getEncounterId() throws Exception {
        return encounterID;
    }

    /**
     * Short for <code>record.get(columnIdentifier.toString))</code>
     *
     * @param columnIdentifier
     * @return
     */
    public String get(Object columnIdentifier) {
        String columnName = Objects.toString(columnIdentifier, null);
        boolean tryCatch = columnIdentifier instanceof TableColumnIdentifier && !((TableColumnIdentifier) columnIdentifier).isMandatory();
        String entry = null;
        if (tryCatch) {
            try {
                entry = record.get(columnName);
            } catch (Exception e) {
                entry = "";
            }
        } else {
            entry = record.get(columnName);
        }
        // replace the escaped quotes from Excel2Csv with
        // real quotes
        if (entry != null) {
            entry = entry.replace(Excel2Csv.QUOTE_ESCAPE, "\"");
        }
        return entry;
    }

    /**
     * @return
     * @throws Exception
     */
    protected String parsePatientId() throws Exception {
        TableColumnIdentifier patientIDColumnIdentifier = getPatientIDColumnIdentifier();
        String id = get(patientIDColumnIdentifier);
        if (id != null) {
            return id.replace('_', '-'); //AXS: (Some) FHIR Server will not accept IDs with an underscore!
        }
        error(patientIDColumnIdentifier + " empty for Record");
        return null;
    }

    /**
     * In old excel files the column with the encounter number does not exists.
     * That means that this number should always be 1 (in every table sheet) and
     * there is always only 1 encounter per patient possible.</br>
     * Now there is an extra column in every sheet for the encounter number so
     * that we can define multiple encounters for 1 patient and add medication,
     * diagnosis etc. to a special encounter.</br>
     * To ensure that the files without the encounter number column will still
     * be converted we set the value 1 if the column is missing. Same happens if
     * the column exists and the value is mandatory but missing.</br>
     * If the column is optional but exists then the value of the column will be
     * inserted in the full ID. Only in this case the resulting ID can be
     * <code>null</code> if the value in the record is empty.</br>
     * If the column is optional and is not in the data then (for backward
     * compatibility reason) the value will set to 1.
     *
     * @return the encounter id in the record or a default if the column is
     *         mandatory.
     * @throws Exception
     */
    private String parseEncounterId() throws Exception {
        TableColumnIdentifier encounterIDColumnIdentifier = getMainEncounterNumberColumnIdentifier();
        //missing encounter number -> set it to 1 if the column does not exists in the table
        if (encounterIDColumnIdentifier == null) {
            return null;
        }
        String encounterIDColumnName = encounterIDColumnIdentifier.toString();
        boolean columnExists = record.isMapped(encounterIDColumnName);
        String encounterNumber = null;
        if (columnExists) {
            String recordEncounterNumber = record.get(encounterIDColumnName);
            boolean valueExists = !isBlank(recordEncounterNumber);
            if (valueExists) {
                encounterNumber = recordEncounterNumber;
            }
        } else {
            encounterNumber = encounterIDColumnIdentifier.getDefaultIfMissing();
        }
        //is still null if the column exists but the value is missing
        if (encounterNumber == null) {
            return null;
        }
        return pid + "-E-" + encounterNumber;
    }

    /**
     * @return the enum identifier for the column with the patient ID
     */
    protected TableColumnIdentifier getPatientIDColumnIdentifier() {
        return DefaultTableColumnNames.Patient_ID;
    }

    /**
     * @return the enum identifier for the column with the patient ID
     */
    protected TableColumnIdentifier getMainEncounterNumberColumnIdentifier() {
        return DefaultTableColumnNames.Versorgungsfall_Nr;
    }

    /**
     * @return
     * @throws Exception
     */
    protected Reference getPatientReference() throws Exception {
        return getPatientReference(false);
    }

    /**
     * @param checkExistence
     * @return
     * @throws Exception
     */
    protected Reference getPatientReference(boolean checkExistence) throws Exception {
        return getReference(checkExistence ? Person : null, pid, Patient.class);
    }

    /**
     * @return
     * @throws Exception
     */
    protected Reference getEncounterReference() throws Exception {
        return getEncounterReference(false);
    }

    /**
     * @param checkExistence
     * @return
     * @throws Exception
     */
    protected Reference getEncounterReference(boolean checkExistence) throws Exception {
        if (encounterID == null) { // can be optional
            return null;
        }
        return getReference(checkExistence ? Versorgungsfall : null, encounterID, Encounter.class);
    }

    /**
     * @param tableIdentifier
     * @param elementID
     * @param referenceType
     * @return
     * @throws Exception
     */
    private Reference getReference(TableIdentifier tableIdentifier, String elementID, Class<? extends Resource> referenceType) throws Exception {
        if (tableIdentifier != null) {
            Resource resource = result.get(tableIdentifier, referenceType, elementID); //must exists in this bundle to create a valid reference
            if (resource == null) {
                return null;
            }
        }
        return createReference(referenceType, elementID);
    }

    /**
     * @return
     * @throws Exception
     */
    protected String getDIZId() throws Exception {
        return dizID;
    }

    /**
     * Creates a {@link CodeableConcept} from the columns with the given name of
     * the {@link CSVRecord} of this converter and the given codeSystemMapper.
     *
     * @param codeColumnName Name of the column with the human readable code
     * @param codeSystemMapper a mapper that maps from the human readable code
     *            to a code from a code system. This mapper must return the code
     *            system via the function
     *            {@link CodeSystemMapper#getCodeSystem()}
     * @return a new {@link CodeableConcept}
     * @throws Exception if the {@link CSVRecord} returns <code>null</code> for
     *             the codeColumnName
     */
    public CodeableConcept createCodeableConcept(Enum<?> codeColumnName, CodeSystemMapper codeSystemMapper) throws Exception {
        String humanText = record.get(codeColumnName);
        if (humanText == null) {
            error(codeColumnName + " empty for Record");
            return null;
        }
        String code = codeSystemMapper.getHumanToCode(humanText);
        if (code == null) {
            code = humanText;
        }
        String codeSystem = codeSystemMapper.getCodeSystem();
        Coding coding = createCoding(codeSystem, code, humanText);
        return new CodeableConcept(coding);
    }

    /**
     * Creates a new {@link CodeableConcept} to which coding is added. This code
     * has the passed {@link CodeSystem} and as code value the value from the
     * column with the name codeColumnName from the {@link CSVRecord}.
     * Additionally the returned {@link CodeableConcept} gets the text from the
     * column textColumnName.
     *
     * @param codeSystem the code system of the contained {@link Coding}
     * @param codeColumnName Name of the column with the code value for the
     *            {@link Coding}
     * @param textColumnName Name of the column with the text for the returned
     *            {@link CodeableConcept}
     * @return a new {@link CodeableConcept} or if missing and mandatory then a
     *         data absent reasond or if not mandatory then <code>null</code>
     */
    public CodeableConcept createCodeableConcept(String codeSystem, Enum<?> codeColumnName, Enum<?> textColumnName) {
        String code = record.get(codeColumnName);
        if (code != null) {
            Coding coding = createCoding(codeSystem, code);
            return createCodeableConcept(coding, textColumnName);
        }
        String errorMessage = codeColumnName + " empty for Record";
        if (isMandatory(codeColumnName)) {
            err(errorMessage + " -> Creating \"unknown\" Data Absent Reason");
            return getUnknownDataAbsentReasonCodeableConcept().setText(record.get(textColumnName));
        }
        warning(errorMessage);
        return null;
    }

    /**
     * Creates an new {@link CodeableConcept} with the given {@link Coding}.
     * Additionally the returned {@link CodeableConcept} gets the text from the
     * column textColumnName.
     *
     * @param coding the {@link Coding} to set for the returned
     *            {@link CodeableConcept}
     * @param textColumnName Name of the column with the text for the returned
     *            {@link CodeableConcept}
     * @return a new {@link CodeableConcept}
     */
    public CodeableConcept createCodeableConcept(Coding coding, Enum<?> textColumnName) {
        String text = record.get(textColumnName);
        return new CodeableConcept(coding).setText(text);
    }

    /**
     * Creates a new {@link CodeableConcept} with the given code and code system
     * for the contained {@link Coding}.
     *
     * @param codeSystem the code system of the contained {@link Coding}
     * @param code the code of the contained {@link Coding}
     * @return a new {@link CodeableConcept}
     */
    public static CodeableConcept createCodeableConcept(String codeSystem, String code) {
        Coding coding = createCoding(codeSystem, code);
        return new CodeableConcept(coding);
    }

    /**
     * Creates a new {@link CodeableConcept} with the given code and code system
     * for the contained {@link Coding}. Additionally the returned
     * {@link CodeableConcept} gets the text from parameter <code>text</code>.
     *
     * @param codeSystem the code system of the contained {@link Coding}
     * @param code the code of the contained {@link Coding}
     * @param text
     * @return a new {@link CodeableConcept}
     */
    public static CodeableConcept createCodeableConcept(String codeSystem, String code, String text) {
        return createCodeableConcept(codeSystem, code, null, text);
    }

    /**
     * Creates a new {@link CodeableConcept} with the given code, code system
     * and display for the contained {@link Coding}. Additionally the returned
     * {@link CodeableConcept} gets the text from parameter <code>text</code>.
     *
     * @param codeSystem the code system of the contained {@link Coding}
     * @param code the code of the contained {@link Coding}
     * @param display
     * @param text
     * @return a new {@link CodeableConcept}
     */
    public static CodeableConcept createCodeableConcept(String codeSystem, String code, String display, String text) {
        Coding coding = createCoding(codeSystem, code);
        coding.setDisplay(display);
        return new CodeableConcept(coding).setText(text);
    }

    /**
     * Creates a new {@link Coding} with the given parameters.
     *
     * @param codeSystem the code system to set
     * @param codeColumnName Name of the column with the code
     * @return a new {@link Coding}
     */
    public Coding createCoding(String codeSystem, Enum<?> codeColumnName) {
        String code = record.get(codeColumnName);
        if (isBlank(code)) {
            String errorMessage = codeColumnName + " empty for Record";
            if (!isMandatory(codeColumnName)) {
                warning(errorMessage);
                return null;
            }
            err(errorMessage + " -> Creating \"unknown\" Data Absent Reason");
        }
        return createCoding(codeSystem, code);
    }

    /**
     * @param codeSystem the code system to set
     * @param code the code to set
     * @return a new {@link Coding} with the given values
     */
    public static Coding createCoding(String codeSystem, String code) {
        Coding coding = new Coding().setSystem(codeSystem);
        if (!isBlank(code)) {
            return coding.setCode(code);
        }
        coding.getCodeElement().addExtension(DATA_ABSENT_REASON_UNKNOWN);
        return coding;
    }

    /**
     * @param codeSystem the code system to set
     * @param code the code to set
     * @param display the display text to set for the returned {@link Coding}
     * @return a new {@link Coding} with the given values
     */
    public static Coding createCoding(String codeSystem, String code, String display) {
        Coding coding = createCoding(codeSystem, code);
        coding.setDisplay(display);
        return coding;
    }

    /**
     * @param coding
     * @return
     */
    public boolean isDataAbsentReason(Coding coding) {
        if (coding != null) {
            String code = coding.getCode();
            DataAbsentReason dataAbsentReason = null;
            try {
                dataAbsentReason = DataAbsentReason.fromCode(code);
            } catch (FHIRException e) {
                //ignore the org.hl7.fhir.exceptions.FHIRException: Unknown DataAbsentReason code
            }
            if (dataAbsentReason != null) {
                String system = coding.getSystem();
                return dataAbsentReason.getSystem().equals(system);
            }
        }
        return false;
    }

    /**
     * @param startDateColumnName
     * @param endDateColumnName
     * @return a {@link Period} object filled with the start and end date given
     *         by the column names in the {@link CSVRecord} of this converter
     */
    public Period createPeriod(Enum<?> startDateColumnName, Enum<?> endDateColumnName) throws Exception {
        DateTimeType startDate = null;
        DateTimeType endDate = null;
        try {
            String startDateValue = record.get(startDateColumnName);
            startDate = DateUtil.parseDateTimeType(startDateValue);
        } catch (Exception e) {
        }
        try {
            String endDateValue = record.get(endDateColumnName);
            endDate = DateUtil.parseDateTimeType(endDateValue);
        } catch (Exception e) {
        }
        try {
            return createPeriod(startDate, endDate);
        } catch (Exception e) {
            error("Can not parse " + startDateColumnName + " or " + endDateColumnName + " as date for Record " + record);
            return null;
        }
    }

    /**
     * @param startDate
     * @param endDate
     * @return
     * @throws Exception
     */
    public Period createPeriod(DateTimeType startDate, DateTimeType endDate) throws Exception {
        if (startDate == null) {
            startDate = endDate;
        }
        if (endDate == null) {
            endDate = startDate;
        }

        //        //set endDate always obe day after startDate. Maybe we should (de)activate this optional if errors in data should be detected
        //        if (startDate != null && endDate != null && (startDate.equals(endDate) || endDate.before(startDate))) {
        //            endDate = DateUtil.addDays(endDate, 1);
        //        }

        //ensure that the period always starts with the lower date
        if (startDate != null && startDate.after(endDate)) {
            DateTimeType dummy = startDate;
            startDate = endDate;
            endDate = dummy;
        }

        return new Period().setStartElement(startDate).setEndElement(endDate);
    }

    /**
     * @param value
     * @param ucumCode
     * @param comparator
     * @return
     * @throws Exception
     */
    public static Quantity getUcumQuantity(BigDecimal value, String ucumCode, String comparator) throws Exception {
        String ucumUnit = null;
        if (!isBlank(ucumCode)) {
            ucumCode = UcumMapper.getValidUcumCode(ucumCode);
            ucumUnit = UcumMapper.getUcumUnit(ucumCode);
        } else {
            ucumCode = null;
        }
        Quantity quantity = new Quantity().setSystem("http://unitsofmeasure.org");
        if (value != null) {
            quantity.setValue(value);
        } else {
            quantity.getValueElement()
                    .addExtension(DATA_ABSENT_REASON_UNKNOWN);
        }
        if (!isBlank(ucumCode)) {
            quantity.setCode(ucumCode);
        } else {
            quantity.getCodeElement()
                    .addExtension(DATA_ABSENT_REASON_UNKNOWN);
        }
        if (!isBlank(ucumUnit)) {
            quantity.setUnit(ucumUnit);
        } else {
            quantity.getUnitElement()
                    .addExtension(DATA_ABSENT_REASON_UNKNOWN);
        }
        if (!isBlank(comparator)) {
            QuantityComparator quantityComparator = QuantityComparator.fromCode(comparator);
            quantity.setComparator(quantityComparator);
        }
        return quantity;
    }

    /**
     * @return
     * @throws Exception
     */
    protected DateType parseDate(Enum<?> dateColumnName) throws Exception {
        String date = get(dateColumnName);
        if (date != null) {
            try {
                return parseDateType(date);
            } catch (Exception e) {
                error("Can not parse " + dateColumnName + " for Record");
                return null;
            }
        }
        if (dateColumnName instanceof TableColumnIdentifier && ((TableColumnIdentifier) dateColumnName).isMandatory()) {
            error(dateColumnName + " empty for Record");
        }
        return null;
    }

    /**
     * @return
     * @throws Exception
     */
    protected DateTimeType parseDateTimeType(Enum<?> dateColumnName) throws Exception {
        String date = get(dateColumnName);
        if (date != null) {
            try {
                return DateUtil.parseDateTimeType(date);
            } catch (Exception e) {
                error("Can not parse " + dateColumnName + " for Record");
                return null;
            }
        }
        if (dateColumnName instanceof TableColumnIdentifier && ((TableColumnIdentifier) dateColumnName).isMandatory()) {
            error(dateColumnName + " empty for Record");
        }
        return null;
    }

    /**
     * Return the toString() of the record of this converter.
     */
    @Override
    public String toString() {
        return record.toString();
    }

    /**
     * @return
     */
    private static Extension createDataAbsentReason(DataAbsentReason dataAbsentReason) {
        return Factory.newExtension(dataAbsentReason.getSystem(), new CodeType(dataAbsentReason.toCode()), true);
    }

    /**
     * @return a {@link CodeableConcept} that represents a valid unknown data
     *         absent reason for {@link Observation} values.
     */
    public static CodeableConcept getUnknownDataAbsentReasonCodeableConcept() {
        return getDataAbsentReasonCodeableConcept(DataAbsentReason.UNKNOWN);
    }

    /**
     * @param dataAbsentReason
     * @return
     */
    public static CodeableConcept getDataAbsentReasonCodeableConcept(DataAbsentReason dataAbsentReason) {
        return createCodeableConcept(dataAbsentReason.getSystem(), dataAbsentReason.toCode());
    }

}
