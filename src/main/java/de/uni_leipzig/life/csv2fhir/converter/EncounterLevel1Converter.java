package de.uni_leipzig.life.csv2fhir.converter;

import static de.uni_leipzig.life.csv2fhir.BundleFunctions.createReference;
import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Versorgungsfall;
import static de.uni_leipzig.life.csv2fhir.converter.EncounterLevel1Converter.EncounterLevel1_Columns.Enddatum;
import static de.uni_leipzig.life.csv2fhir.converter.EncounterLevel1Converter.EncounterLevel1_Columns.Startdatum;
import static de.uni_leipzig.life.csv2fhir.converter.EncounterLevel1Converter.EncounterLevel1_Columns.Versorgungsfallklasse;

import java.util.Collections;
import java.util.List;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Encounter.DiagnosisComponent;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.UriType;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import de.uni_leipzig.imise.validate.FHIRValidator;
import de.uni_leipzig.life.csv2fhir.CodeSystemMapper;
import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterOptions;
import de.uni_leipzig.life.csv2fhir.ConverterResult;
import de.uni_leipzig.life.csv2fhir.TableColumnIdentifier;

/**
 * @author jheuschkel (19.10.2020), AXS (05.11.2021)
 */
public class EncounterLevel1Converter extends Converter {

    /**
     * toString() result of these enum values are the names of the columns in
     * the correspunding excel sheet.
     */
    public static enum EncounterLevel1_Columns implements TableColumnIdentifier {
        Startdatum,
        Enddatum,
        Versorgungsfallklasse,
    }

    /**
     * Value that will be set if the mandatory column "Versorgungsfall-Nr" is
     * missing in the data table sheets.
     */
    public static final String DEFAULT_ENCOUNTER_ID_NUMBER = "1";

    /**
     * Maps from human readable encounter types to the correspondig code system
     * code and contains some more resources for the encounters.
     */
    public static final CodeSystemMapper ENCOUNTER_LEVEL1_CLASS_RESOURCES = new CodeSystemMapper("EncounterLevel1_Class.map");

    /** Map with the encounter types and its displays. */
    public static final CodeSystemMapper ENCOUNTER_TYPE_RESOURCES = new CodeSystemMapper("Encounter_Type.map");

    /**
     * Maps from human readable diagnosis role description to the correspondig
     * code system code.
     */
    public static final CodeSystemMapper DIAGNOSIS_ROLE_RESOURCES = new CodeSystemMapper("Diagnosis_Role.map");

    /**
     * @param record
     * @param result
     * @param validator
     * @param options
     * @throws Exception
     */
    public EncounterLevel1Converter(CSVRecord record, ConverterResult result, FHIRValidator validator, ConverterOptions options) throws Exception {
        super(record, result, validator, options);
    }

    /**
     * Resets the static index counter
     */
    public static void reset() {
        //no static counter to reset at the moment
    }

    @Override
    protected List<Resource> convertInternal() throws Exception {
        Encounter encounter = new Encounter();
        encounter.setMeta(getMeta());
        String encounterId = getEncounterId();
        if (encounterId == null) { //should be only null if the column exists but the value is absent
            error("Encounter ID should not be empty! ");
        }
        encounter.setId(encounterId);
        encounter.setIdentifier(convertIdentifier());
        encounter.setStatus(Encounter.EncounterStatus.FINISHED);//TODO
        encounter.setClass_(getClass_());
        encounter.setSubject(getPatientReference());
        encounter.setPeriod(createPeriod(Startdatum, Enddatum));
        encounter.setType(getEncounterType());
        return Collections.singletonList(encounter);
    }

    /**
     * @return
     * @throws Exception
     */
    private List<Identifier> convertIdentifier() throws Exception {
        // generierte Encounternummer
        String id = getEncounterId();
        String dizID = getDIZId();
        return createIdentifier(id, dizID);
    }

    /**
     * @return
     * @throws Exception
     */
    private Coding getClass_() throws Exception {
        Coding coding = createCoding(ENCOUNTER_LEVEL1_CLASS_RESOURCES.getCodeSystem(), Versorgungsfallklasse);
        return setCorrectCodeAndDisplayInClassCoding(coding);
    }

    /**
     * @param coding
     */
    private static Coding setCorrectCodeAndDisplayInClassCoding(Coding coding) {
        if (coding != null) {
            //replace the code string from by the correct code and display from the resource map
            String code = coding.getCode();
            if (!Strings.isNullOrEmpty(code)) { // is null if the Coding has only a Data Absent Reason Extension
                String realCode = ENCOUNTER_LEVEL1_CLASS_RESOURCES.get(code);
                String display = ENCOUNTER_LEVEL1_CLASS_RESOURCES.getFirstBackwardKey(realCode);
                coding.setCode(realCode);
                coding.setDisplay(display);
            }
        }
        return coding;
    }

    /**
     * @return
     */
    protected static Meta getMeta() {
        return new Meta().addProfile(ENCOUNTER_LEVEL1_CLASS_RESOURCES.getProfile());
    }

    /**
     * @param encounterID
     * @param dizID
     * @return
     */
    public static List<Identifier> createIdentifier(String encounterID, String dizID) {
        Reference reference = new Reference()
                .setIdentifier(
                        new Identifier()
                                .setSystem("https://www.medizininformatik-initiative.de/fhir/core/NamingSystem/org-identifier")
                                .setValue(dizID));

        Identifier identifier = new Identifier()
                .setValue(encounterID)
                .setType(createCodeableConcept("http://terminology.hl7.org/CodeSystem/v2-0203", "VN"))
                .setAssigner(reference);

        //identifier.setSystem("http://dummyurl") // must be an formal correct url but we add a Data Absent Reason
        UriType systemElement = identifier.getSystemElement();
        systemElement.addExtension(DATA_ABSENT_REASON_UNKNOWN);

        return Collections.singletonList(identifier);
    }

    /**
     * @param useIdentifier
     * @return
     */
    public static CodeableConcept createDiagnosisUse(String useIdentifier) {
        String codeSystem = DIAGNOSIS_ROLE_RESOURCES.getCodeSystem();
        String code = DIAGNOSIS_ROLE_RESOURCES.getHumanToCode(useIdentifier);
        String display = DIAGNOSIS_ROLE_RESOURCES.getCodeToHuman(code);
        CodeableConcept diagnosisUse = new CodeableConcept();
        diagnosisUse.addCoding()
                .setSystem(codeSystem)
                .setCode(code)
                .setDisplay(display);
        return diagnosisUse;
    }

    /**
     * @param result
     * @param encounterID
     * @param procedure
     */
    public static void addDiagnosisToEncounter(ConverterResult result, String encounterID, Procedure procedure) {
        addDiagnosisToEncounter(result, encounterID, procedure, null);
    }

    /**
     * @param result
     * @param encounterID
     * @param condition
     * @param diagnosisUseIdentifier
     */
    public static void addDiagnosisToEncounter(ConverterResult result, String encounterID, Condition condition, String diagnosisUseIdentifier) {
        addDiagnosisToEncounter(result, encounterID, (Resource) condition, diagnosisUseIdentifier);
    }

    /**
     * @param result
     * @param encounterID
     * @param conditionOrProcedureAsDiagnosis
     * @param diagnosisUseIdentifier
     */
    private static void addDiagnosisToEncounter(ConverterResult result, String encounterID, Resource conditionOrProcedureAsDiagnosis, String diagnosisUseIdentifier) {
        // The KDS definition needs a diagnosis use (min cardinality 1), but a procedure doesn't have this -> arbitrary default
        if (diagnosisUseIdentifier == null) { // default for missing values
            String defaultDiagnosisRoleCode = DIAGNOSIS_ROLE_RESOURCES.get("DEFAULT_DIAGNOSIS_ROLE_CODE");
            diagnosisUseIdentifier = DIAGNOSIS_ROLE_RESOURCES.getFirstBackwardKey(defaultDiagnosisRoleCode);
        }
        // encounter should be only null in error cases, but mybe we
        // should catch and log
        Encounter encounter = result.get(Versorgungsfall, Encounter.class, encounterID);
        addDiagnosisToEncounter(encounter, conditionOrProcedureAsDiagnosis, diagnosisUseIdentifier);
    }

    /**
     * @param encounter
     * @param conditionOrProcedureAsDiagnosis
     * @param diagnosisUseIdentifier
     */
    public static void addDiagnosisToEncounter(Encounter encounter, Resource conditionOrProcedureAsDiagnosis, String diagnosisUseIdentifier) {
        // the encounter can be null, if the diagnosis is defined with an
        // empty or not present encounter number in the current dataset
        if (encounter == null) {
            return;
        }
        // construct a valid DiagnosisComponent from condition or
        // procedure to add it as reference to the encounter
        Reference conditionReference = new Reference(conditionOrProcedureAsDiagnosis);
        // add diagnosis use to the diagnosis component
        CodeableConcept diagnosisUse = EncounterLevel1Converter.createDiagnosisUse(diagnosisUseIdentifier);
        DiagnosisComponent diagnosisComponent = new DiagnosisComponent(conditionReference);
        diagnosisComponent.setUse(diagnosisUse);
        // maybe the same diagnosis was coded twice
        if (!encounter.getDiagnosis().contains(diagnosisComponent)) {
            encounter.addDiagnosis(diagnosisComponent);
        }
    }

    /**
     * Creates a default Encounter for this converter class.
     *
     * @param pid
     * @param dizID
     * @param period
     * @return
     */
    private static Encounter createDefault(String pid, String id, String dizID, Period period) {
        Encounter encounter = new Encounter();
        encounter.setMeta(getMeta());
        encounter.setId(id);
        encounter.setIdentifier(createIdentifier(id, dizID));
        encounter.setStatus(Encounter.EncounterStatus.FINISHED);
        //encounter.setClass_(createCoding(CLASS_CODE_SYSTEM, "stationaer"));
        String defaultClassCode = ENCOUNTER_LEVEL1_CLASS_RESOURCES.get("DEFAULT_CLASS_CODE");
        String defaultDisplay = ENCOUNTER_LEVEL1_CLASS_RESOURCES.getFirstBackwardKey(defaultClassCode);
        encounter.setClass_(createCoding(ENCOUNTER_LEVEL1_CLASS_RESOURCES.getCodeSystem(), defaultClassCode, defaultDisplay));
        encounter.setSubject(createReference(Patient.class, pid));
        try {
            encounter.setPeriod(period);
        } catch (Exception e) {
            //validity (and not null) should be checked later
        }
        return encounter;
    }

    /**
     * @return the type of the encounter as {@link CodeableConcept}.
     */
    protected List<CodeableConcept> getEncounterType() {
        String simpleClassName = getClass().getSimpleName();
        String codeSystemURL = ENCOUNTER_TYPE_RESOURCES.getCodeSystem();
        String typeCode = ENCOUNTER_TYPE_RESOURCES.get(simpleClassName + "_TYPE_CODE");
        String typeDisplay = ENCOUNTER_TYPE_RESOURCES.get(simpleClassName + "_TYPE_DISPLAY");
        return ImmutableList.of(createCodeableConcept(codeSystemURL, typeCode, typeDisplay, null));
    }

}
