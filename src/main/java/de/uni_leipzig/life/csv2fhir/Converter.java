package de.uni_leipzig.life.csv2fhir;

import static de.uni_leipzig.life.csv2fhir.BundleFunctions.createReference;
import static de.uni_leipzig.life.csv2fhir.Converter.EmptyRecordValueErrorLevel.ERROR;
import static de.uni_leipzig.life.csv2fhir.Converter.EmptyRecordValueErrorLevel.WARNING;
import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Person;
import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Versorgungsfall;
import static de.uni_leipzig.life.csv2fhir.utils.DateUtil.parseDateType;

import java.math.BigDecimal;
import java.util.List;

import javax.annotation.Nullable;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

import de.uni_leipzig.UcumMapper;
import de.uni_leipzig.imise.FHIRValidator;
import de.uni_leipzig.imise.utils.Sys;
import de.uni_leipzig.life.csv2fhir.utils.DateUtil;

/**
 * @author fheuschkel (02.11.2020), AXS (18.11.2021)
 */
public abstract class Converter {

    /**  */
    protected static Logger LOG = LoggerFactory.getLogger(Converter.class);

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
    final String pid;

    /**  */
    final String encounterID;

    /**  */
    final String dizID;

    /**  */
    private final CSVRecord record;

    /**  */
    protected boolean kds = true;

    /**  */
    protected boolean kds_strict = true;

    /**  */
    protected final ConverterResult result;

    /**
     * Validator to validate resources. Can be <code>null</code>. Can be
     * <code>null</code> if nothing should be validated.
     */
    protected final FHIRValidator validator;

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
    }

    /**
     * @return
     * @throws Exception
     */
    public abstract List<? extends Resource> convert() throws Exception;

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
        throw new Exception("Error on " + getErrorMessageBody(msg));
    }

    /**
     * @param msg
     */
    protected void warning(String msg) {
        warning(msg, 0);
    }

    /**
     * @param msg
     */
    protected void warning(String msg, int stackTraceBackwardSteps) {
        LOG.warn(getErrorMessageBody(msg) + "     " + Sys.getStackTraceStep(3 + stackTraceBackwardSteps));
    }

    /**
     * @param msg
     * @return
     */
    protected String getErrorMessageBody(String msg) {
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
    protected final String getEncounterId() throws Exception {
        return encounterID;
    }

    /**
     * Short for <code>record.get(columnIdentifier.toString))</code>
     *
     * @param columnIdentifier
     * @return
     */
    public String get(Object columnIdentifier) {
        String columnName = columnIdentifier.toString();
        boolean tryCatch = columnIdentifier instanceof TableColumnIdentifier && !((TableColumnIdentifier) columnIdentifier).isMandatory();
        if (!tryCatch) {
            return record.get(columnName);
        }
        try {
            return record.get(columnName);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * @return
     * @throws Exception
     */
    protected String parsePatientId() throws Exception {
        Enum<?> patientIDColumnIdentifier = getPatientIDColumnIdentifier();
        String id = get(patientIDColumnIdentifier);
        if (id != null) {
            return id.replace('_', '-'); //AXS: (Some) FHIR Server will not accept IDs with an underscore!
        }
        error(patientIDColumnIdentifier + " empty for Record");
        return null;
    }

    /**
     * @return
     * @throws Exception
     */
    private String parseEncounterId() throws Exception {
        Enum<?> encounterIDColumnIdentifier = getMainEncounterNumberColumnIdentifier();
        //missing encounter number -> always encounter number 1
        String encounterNumber = "1";
        if (encounterIDColumnIdentifier != null) {
            String recordEncounterNumber = get(encounterIDColumnIdentifier);
            if (!Strings.isNullOrEmpty(encounterNumber)) {
                encounterNumber = recordEncounterNumber;
            }
        }
        return pid + "-E-" + encounterNumber;
    }

    /**
     * @return the enum identifier for the column with the patient ID
     */
    protected abstract Enum<?> getPatientIDColumnIdentifier();

    /**
     * @return the enum identifier for the column with the patient ID
     */
    protected Enum<?> getMainEncounterNumberColumnIdentifier() {
        return null;
    }

    /**
     * @return
     * @throws Exception
     */
    protected Reference getPatientReference() throws Exception {
        return getPatientReference(true);
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
        return getEncounterReference(true);
    }

    /**
     * @param checkExistence
     * @return
     * @throws Exception
     */
    protected Reference getEncounterReference(boolean checkExistence) throws Exception {
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
     *
     * @param codeSystem the code system of the contained {@link Coding}
     * @param codeColumnName Name of the column with the code value for the
     *            {@link Coding}
     * @param errorLevelIfCodeIsMissing {@link EmptyRecordValueErrorLevel#ERROR}
     *            means that an error will be thrown if the value in the column
     *            with the codeColumName is <code>null</code>.
     *            {@link EmptyRecordValueErrorLevel#WARNING} only prints a
     *            warning message and {@link EmptyRecordValueErrorLevel#IGNORE}
     *            or <code>null</code> will ignore this case.
     * @return a new {@link CodeableConcept} or <code>null</code> if the code
     *         value is missing
     * @throws Exception if errorLevelIfCodeIsMissing is
     *             {@link EmptyRecordValueErrorLevel#ERROR} and the code value
     *             is missing.
     */
    public CodeableConcept createCodeableConcept(String codeSystem, Enum<?> codeColumnName, EmptyRecordValueErrorLevel errorLevelIfCodeIsMissing) throws Exception {
        return createCodeableConcept(codeSystem, codeColumnName, null, errorLevelIfCodeIsMissing);
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
     * @param errorLevelIfCodeIsMissing {@link EmptyRecordValueErrorLevel#ERROR}
     *            means that an error will be thrown if the value in the column
     *            with the codeColumName is <code>null</code>.
     *            {@link EmptyRecordValueErrorLevel#WARNING} only prints a
     *            warning message and {@link EmptyRecordValueErrorLevel#IGNORE}
     *            or <code>null</code> will ignore this case.
     * @return a new {@link CodeableConcept} or <code>null</code> if the code
     *         value is missing
     * @throws Exception if errorLevelIfCodeIsMissing is
     *             {@link EmptyRecordValueErrorLevel#ERROR} and the code value
     *             is missing.
     */
    public CodeableConcept createCodeableConcept(String codeSystem, Enum<?> codeColumnName, Enum<?> textColumnName, EmptyRecordValueErrorLevel errorLevelIfCodeIsMissing) throws Exception {
        String code = record.get(codeColumnName);
        if (code != null) {
            Coding coding = createCoding(codeSystem, code);
            return createCodeableConcept(coding, textColumnName);
        }
        String errorMessage = codeColumnName + " empty for Record";
        if (errorLevelIfCodeIsMissing == ERROR) {
            error(errorMessage);
        } else if (errorLevelIfCodeIsMissing == WARNING) {
            warning(errorMessage);
        }
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
     * @param errorLevelIfCodeIsMissing {@link EmptyRecordValueErrorLevel#ERROR}
     *            means that an error will be thrown if the value in the column
     *            with the codeColumName is <code>null</code>.
     *            {@link EmptyRecordValueErrorLevel#WARNING} only prints a
     *            warning message and any other value
     *            {@link EmptyRecordValueErrorLevel#IGNORE} or <code>null</code>
     *            will ignore this case.
     * @return a new {@link Coding}
     * @throws Exception if errorLevelIfCodeIsMissing is
     *             {@link EmptyRecordValueErrorLevel#ERROR} and the code value
     *             is missing.
     */
    public Coding createCoding(String codeSystem, Enum<?> codeColumnName, EmptyRecordValueErrorLevel errorLevelIfCodeIsMissing) throws Exception {
        String code = record.get(codeColumnName);
        if (code != null) {
            return createCoding(codeSystem, code);
        }
        String errorMessage = codeColumnName + " empty for Record";
        if (errorLevelIfCodeIsMissing == ERROR) {
            error(errorMessage);
        } else {
            warning(errorMessage);
        }
        return null;
    }

    /**
     * @param codeSystem the code system to set
     * @param code the code to set
     * @return a new {@link Coding} with the given values
     */
    public static Coding createCoding(String codeSystem, String code) {
        return new Coding()
                .setSystem(codeSystem)
                .setCode(code);
    }

    /**
     * @param codeSystem the code system to set
     * @param code the code to set
     * @param display the display text to set for the returned {@link Coding}
     * @return a new {@link Coding} with the given values
     */
    public static Coding createCoding(String codeSystem, String code, String display) {
        return new Coding()
                .setSystem(codeSystem)
                .setCode(code)
                .setDisplay(display);
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
        //        if (startDate != null && startDate.after(endDate)) {
        //            DateTimeType dummy = startDate;
        //            startDate = endDate;
        //            endDate = dummy;
        //        }

        return new Period().setStartElement(startDate).setEndElement(endDate);
    }

    /**
     * @param value
     * @param ucumCode
     * @return
     * @throws Exception
     */
    public static Quantity getUcumQuantity(BigDecimal value, String ucumCode) throws Exception {
        ucumCode = UcumMapper.getValidUcumCode(ucumCode);
        String ucumUnit = UcumMapper.getUcumUnit(ucumCode);
        Quantity quantity = new Quantity()
                .setSystem("http://unitsofmeasure.org")
                .setValue(value);

        if (!ucumCode.isEmpty()) {
            quantity.setCode(ucumCode)
                    .setUnit(ucumUnit);
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
        error(dateColumnName + " empty for Record");
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
        error(dateColumnName + " empty for Record");
        return null;
    }

    /**
     * Return the toString() of the record of this converter.
     */
    @Override
    public String toString() {
        return record.toString();
    }

}
