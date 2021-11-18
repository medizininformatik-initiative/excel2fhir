package de.uni_leipzig.life.csv2fhir;

import static de.uni_leipzig.life.csv2fhir.Converter.EmptyRecordValueErrorLevel.ERROR;
import static de.uni_leipzig.life.csv2fhir.Converter.EmptyRecordValueErrorLevel.WARNING;

import java.util.List;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

import de.uni_leipzig.imise.utils.CodeSystemMapper;
import de.uni_leipzig.imise.utils.Sys;
import de.uni_leipzig.life.csv2fhir.utils.DateUtil;

/**
 * @author fheuschkel (02.11.2020)
 */
public abstract class Converter {

    /**
     * Specifies how to handle missing values.
     *
     * @author AXS (18.11.2021)
     */
    public static enum EmptyRecordValueErrorLevel {
        /**
         * Throw error on missing value
         */
        ERROR,
        /**
         * Print warning on missing value
         */
        WARNING,
        /**
         * Ignore missing value
         */
        IGNORE,
    }

    /**  */
    final String pid;

    /**  */
    final String dizID;

    /**  */
    protected final CSVRecord record;

    /**  */
    protected boolean kds = true;

    /**  */
    protected boolean kds_strict = true;

    /**
     * @param record
     * @throws Exception
     */
    public Converter(CSVRecord record) throws Exception {
        this.record = record;
        pid = parsePatientId();
        dizID = pid.toUpperCase().replaceAll("[^A-Z]", "");
    }

    /**  */
    public abstract List<Resource> convert() throws Exception;

    /**
     * @param msg
     * @throws Exception
     */
    protected void error(String msg) throws Exception {
        throw new Exception("Error on " + getErrorMessageBody(msg));
    }

    /**
     * @param msg
     */
    protected void warning(String msg) {
        Sys.outm(1, 1, "Warning on " + getErrorMessageBody(msg));
    }

    /**
     * @param msg
     */
    protected void warning(String msg, int stackTraceBackwardSteps) {
        Sys.outm(1, stackTraceBackwardSteps, "Warning on " + getErrorMessageBody(msg));
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
    private String parsePatientId() throws Exception {
        String id = record.get("Patient-ID");
        if (id != null) {
            return id.replace("_", "-");
        }
        error("Patient-ID empty for Record");
        return null;
    }

    /**
     * @return
     * @throws Exception
     */
    protected Reference getPatientReference() throws Exception {
        return createReference(Patient.class, getPatientId());
    }

    /**
     * @return
     * @throws Exception
     */
    protected String getDIZId() throws Exception {
        return dizID;
    }

    /**
     * @return
     * @throws Exception
     */
    protected String getEncounterId() throws Exception {
        return getPatientId() + "-E-1";
    }

    /**
     * @return
     * @throws Exception
     */
    protected Reference getEncounterReference() throws Exception {
        return createReference(Encounter.class, getEncounterId());
    }

    /**
     * @param resourceClass
     * @param idBase
     */
    public static Reference createReference(Class<? extends Resource> resourceClass, String idBase) {
        return new Reference().setReference(resourceClass.getSimpleName() + "/" + idBase);
    }

    /**
     * @param icd
     * @return
     * @throws Exception
     */
    protected String getDiagnoseId(String icd) throws Exception {
        String id;
        if (icd == null) {
            error("ICD empty");
            return null;
        }
        id = icd;
        return getPatientId() + "-C-" + id.hashCode();
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
        Coding coding = createCoding(codeSystem, code).setDisplay(humanText);
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
            warning(errorMessage, 2);
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
    public CodeableConcept createCodeableConcept(String codeSystem, String code) {
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
    public CodeableConcept createCodeableConcept(String codeSystem, String code, String text) {
        Coding coding = createCoding(codeSystem, code);
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
            warning(errorMessage, 2);
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
    public Period createPeriod(Enum<?> startDateColumnName, Enum<?> endDateColumnName) {
        try {
            String startDateValue = record.get(startDateColumnName);
            String endDateValue = record.get(endDateColumnName);
            DateTimeType startDate = DateUtil.parseDateTimeType(startDateValue);
            DateTimeType endDate = DateUtil.parseDateTimeType(endDateValue);
            return new Period().setStartElement(startDate).setEndElement(endDate);
        } catch (Exception e) {
            warning("Can not parse Startdatum or Enddatum for Record", 2);
            return null;
        }
    }

}
