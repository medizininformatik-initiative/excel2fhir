package de.uni_leipzig.life.csv2fhir.converter;

import static com.google.common.base.Strings.isNullOrEmpty;
import static de.uni_leipzig.life.csv2fhir.BundleFunctions.createReference;
import static de.uni_leipzig.life.csv2fhir.ConverterOptions.IntOption.START_ID_ENCOUNTER_LEVEL_2;
import static de.uni_leipzig.life.csv2fhir.ConverterOptions.IntOption.START_ID_ENCOUNTER_LEVEL_3;
import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Fall;
import static de.uni_leipzig.life.csv2fhir.converter.EncounterConverter.Encounter_Columns.Bett;
import static de.uni_leipzig.life.csv2fhir.converter.EncounterConverter.Encounter_Columns.Einrichtungskontaktklasse;
import static de.uni_leipzig.life.csv2fhir.converter.EncounterConverter.Encounter_Columns.Enddatum;
import static de.uni_leipzig.life.csv2fhir.converter.EncounterConverter.Encounter_Columns.Fachabteilung;
import static de.uni_leipzig.life.csv2fhir.converter.EncounterConverter.Encounter_Columns.Startdatum;
import static de.uni_leipzig.life.csv2fhir.converter.EncounterConverter.Encounter_Columns.Station;
import static de.uni_leipzig.life.csv2fhir.converter.EncounterConverter.Encounter_Columns.Zimmer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Encounter.DiagnosisComponent;
import org.hl7.fhir.r4.model.Encounter.EncounterLocationComponent;
import org.hl7.fhir.r4.model.Encounter.EncounterStatus;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Location.LocationStatus;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.UriType;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import de.uni_leipzig.imise.utils.StringUtils;
import de.uni_leipzig.imise.validate.FHIRValidator;
import de.uni_leipzig.life.csv2fhir.CodeSystemMapper;
import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterOptions;
import de.uni_leipzig.life.csv2fhir.ConverterResult;
import de.uni_leipzig.life.csv2fhir.TableColumnIdentifier;

/**
 * @author jheuschkel (19.10.2020), AXS (05.11.2021)
 */
public class EncounterConverter extends Converter {

    /**
     * toString() result of these enum values are the names of the columns in
     * the correspunding excel sheet.
     */
    public static enum Encounter_Columns implements TableColumnIdentifier {
        Startdatum,
        Enddatum,
        Einrichtungskontaktklasse,
        Fachabteilung,
        Station,
        Zimmer,
        Bett
    }

    /**
     * Value that will be set if the mandatory column "Versorgungsfall-Nr" is
     * missing in the data table sheets.
     */
    public static final String DEFAULT_ENCOUNTER_ID_NUMBER = "1";

    /** Map with the encounter types and its displays. */
    public static final CodeSystemMapper ENCOUNTER_TYPE_RESOURCES = new CodeSystemMapper("Encounter_Type.map");

    /**
     * Maps from human readable encounter types to the correspondig code system
     * code and contains some more resources for the encounters.
     */
    public static final CodeSystemMapper ENCOUNTER_LEVEL1_CLASS_RESOURCES = new CodeSystemMapper("EncounterLevel1_Class.map");

    /**
     * Maps from human readable department description to the number code for
     * the department.
     */
    private final CodeSystemMapper ENCOUNTER_LEVEL2_DEPARTMENT_RESOURCES = new CodeSystemMapper("EncounterLevel2_Department.map");

    /**
     * Maps from human readable diagnosis role description to the correspondig
     * code system code.
     */
    public static final CodeSystemMapper DIAGNOSIS_ROLE_RESOURCES = new CodeSystemMapper("Diagnosis_Role.map");

    /**
     * If the column with the PID is empty then the last valid PID of a previous
     * record is taken. This enables that the tbale mu´st no be filled in all
     * columns with the same values and is better structured and more readable.
     */
    //    private static String previousPatientReference;

    private static Encounter previousEncounterLevel1;
    private static Encounter previousEncounterLevel2;

    private static final String RANDOM_DEFULT_VALUE = "" + System.nanoTime();

    // random string to identify a null value in the first rows
    private static String previousDepartmentName = RANDOM_DEFULT_VALUE;
    //    private static String previousWardName = RANDOM_DEFULT_VALUE;
    //    private static String previousRoomName = RANDOM_DEFULT_VALUE;
    //    private static String previousBedName = RANDOM_DEFULT_VALUE;

    private static final Map<String, Location> locationIDToLocation = new HashMap<>();

    /**
     * Even if they (unfortunately) do not exist in the KDS, they are created
     * here because of actual encounter types. This allows us to distinguish
     * them clearly.
     */
    public static class EncounterLevel1 extends Encounter {
    }
    public static class EncounterLevel2 extends Encounter {
    }
    public static class EncounterLevel3 extends Encounter {
    }

    /**
     * @param record
     * @param previousRecordPID
     * @param result
     * @param validator
     * @param options
     * @throws Exception
     */
    public EncounterConverter(CSVRecord record, String previousRecordPID, ConverterResult result, FHIRValidator validator, ConverterOptions options) throws Exception {
        super(record, previousRecordPID, result, validator, options);
    }

    @Override
    protected List<Resource> convertInternal() throws Exception {
        // return list
        List<Resource> encounters = new ArrayList<>();

        String encounterLevel1Id = getEncounterId();
        String departmentName = get(Fachabteilung);
        String wardName = get(Station);
        String roomName = get(Zimmer);
        String bedName = get(Bett);
        String previousEncounterLevel1ID = previousEncounterLevel1 == null ? null : previousEncounterLevel1.getId();
        String previousEncounterLevel2ID = previousEncounterLevel2 == null ? null : previousEncounterLevel2.getId();

        boolean recordHasLevel1EncounterID = !isNullOrEmpty(encounterLevel1Id);
        boolean createLevel1Encounter = recordHasLevel1EncounterID && encounterLevel1Id != previousEncounterLevel1ID;
        boolean createLevel2Encounter = !isNullOrEmpty(departmentName) || departmentName != previousDepartmentName;
        boolean createLevel3Encounter = createLevel2Encounter || !isNullOrEmpty(wardName) || !isNullOrEmpty(roomName) || !isNullOrEmpty(bedName);
        createLevel2Encounter = createLevel3Encounter; // even if there is no department we must create a Level2 Encounter if Level3 must be cretated

        if (createLevel1Encounter) {
            // generate Encounter Level 1
            Encounter encounterLevel1 = new EncounterLevel1();

            encounterLevel1.setId(encounterLevel1Id);
            encounterLevel1.setIdentifier(convertIdentifier(encounterLevel1Id));
            encounterLevel1.setSubject(getPatientReference());
            encounterLevel1.setMeta(getMeta());
            encounterLevel1.setClass_(getEncounterLevel1Class());
            encounterLevel1.setType(getEncounterType(EncounterLevel1.class));
            setPeriodAndStatus(encounterLevel1);

            encounters.add(encounterLevel1);
            previousEncounterLevel1ID = encounterLevel1Id;
            previousEncounterLevel1 = encounterLevel1;
        }

        if (!recordHasLevel1EncounterID) {
            String encounterLevel1Class = get(Einrichtungskontaktklasse);
            if (!isNullOrEmpty(encounterLevel1Class)) {
                error("Encounter ID is empty but encounter class (" + Einrichtungskontaktklasse + ") is given as " + encounterLevel1Class + "for record " + toString());
            }
        }

        if (createLevel2Encounter) {
            // generate Level 2 Encounter using DEPARTMENT_KEY_RESOURCES
            Encounter encounterLevel2 = new EncounterLevel2();

            int nextId = result.getNextId(Fall, EncounterLevel2.class, START_ID_ENCOUNTER_LEVEL_2);
            String encounterLevel2Id = previousEncounterLevel1ID + ResourceIdSuffix.ENCOUNTER_LEVEL_2 + nextId;
            encounterLevel2.setId(encounterLevel2Id);
            encounterLevel2.setIdentifier(convertIdentifier(encounterLevel2Id));
            encounterLevel2.setSubject(getPatientReference());
            encounterLevel2.setPartOf(getEncounterReference(previousEncounterLevel1ID, false));
            encounterLevel2.setMeta(getMeta());
            encounterLevel2.setClass_(getEncounterLevel2Class());
            encounterLevel2.setType(getEncounterType(EncounterLevel2.class));
            if (!isNullOrEmpty(departmentName)) {
                encounterLevel2.setServiceType(createCodeableConcept(Fachabteilung, ENCOUNTER_LEVEL2_DEPARTMENT_RESOURCES));
            }
            setPeriodAndStatus(encounterLevel2);

            encounters.add(encounterLevel2);
            previousEncounterLevel2 = encounterLevel2;
            previousEncounterLevel2ID = encounterLevel2Id;
            previousDepartmentName = departmentName;
            //            previousWardName = RANDOM_DEFULT_VALUE;
            //            previousRoomName = RANDOM_DEFULT_VALUE;
            //            previousBedName = RANDOM_DEFULT_VALUE;
        }

        if (createLevel3Encounter) {
            // generate Level 3 Encounter
            Encounter encounterLevel3 = new EncounterLevel3();

            int nextId = result.getNextId(Fall, EncounterLevel3.class, START_ID_ENCOUNTER_LEVEL_3);
            String encounterLevel3Id = previousEncounterLevel2ID + ResourceIdSuffix.ENCOUNTER_LEVEL_3 + nextId;
            encounterLevel3.setId(encounterLevel3Id);
            encounterLevel3.setIdentifier(convertIdentifier(encounterLevel3Id));
            encounterLevel3.setSubject(getPatientReference());
            encounterLevel3.setPartOf(getEncounterReference(previousEncounterLevel2ID, false));
            encounterLevel3.setMeta(getMeta());
            encounterLevel3.setClass_(getEncounterLevel3Class());
            encounterLevel3.setType(getEncounterType(EncounterLevel3.class));
            encounterLevel3.setLocation(getOrCreateLocations(departmentName, wardName, roomName, bedName));
            // TODO: find out how to code a valid Servicetype for Encounters Level 3
            setPeriodAndStatus(encounterLevel3);

            encounters.add(encounterLevel3);
        }

        return encounters;
    }

    /**
     * @return
     */
    public static final Collection<Location> getLocations() {
        return locationIDToLocation.values();
    }

    /**
     * @param departmentName
     * @param wardName a not null value only creates a location
     * @return
     */
    private static final EncounterLocationComponent getOrCreateLocationWard(String departmentName, String wardName) {
        return getOrCreateLocationInternal(LocationType.WARD, departmentName, wardName, null, null);
    }

    /**
     * @param departmentName
     * @param wardName
     * @param roomName not null
     * @return
     */
    private static final EncounterLocationComponent getOrCreateLocationRoom(String departmentName, String wardName, String roomName) {
        return getOrCreateLocationInternal(LocationType.ROOM, departmentName, wardName, roomName, null);
    }

    /**
     * @param departmentName
     * @param wardName
     * @param roomName
     * @param bedName not null
     * @return
     */
    private static final EncounterLocationComponent getOrCreateLocationBed(String departmentName, String wardName, String roomName, String bedName) {
        return getOrCreateLocationInternal(LocationType.BED, departmentName, wardName, roomName, bedName);
    }

    /**
     * @param locationType
     * @param departmentName
     * @param wardName
     * @param roomName
     * @param bedName
     * @return
     */
    private static final EncounterLocationComponent getOrCreateLocationInternal(LocationType locationType, String departmentName, String wardName, String roomName, String bedName) {
        // null values will be ignored
        String locationID = StringUtils.concatenate("_", departmentName, wardName, roomName, bedName);
        Location location = locationIDToLocation.get(locationID);
        if (location == null) {
            location = new Location();
            location.setId(locationID);
            location.setName(locationID);
            location.setIdentifier(List.of(createLocationIdentifier(locationType, locationID)));
            location.setStatus(LocationStatus.ACTIVE);

            locationIDToLocation.put(locationID, location);

        }
        EncounterLocationComponent encounterLocationComponent = new EncounterLocationComponent();
        encounterLocationComponent.setLocation(createReference(Location.class, locationID));
        encounterLocationComponent.setLocationTarget(location);
        return encounterLocationComponent;
    }

    /**
     * @param locationType
     * @param departmentName
     * @param wardName
     * @param roomName
     * @param bedName
     * @return
     */
    private static final List<EncounterLocationComponent> getOrCreateLocations(String departmentName, String wardName, String roomName, String bedName) {
        List<EncounterLocationComponent> locationComponents = new ArrayList<>();
        if (!isNullOrEmpty(wardName)) {
            locationComponents.add(getOrCreateLocationWard(departmentName, wardName));
        }
        if (!isNullOrEmpty(roomName)) {
            locationComponents.add(getOrCreateLocationRoom(departmentName, wardName, roomName));
        }
        if (!isNullOrEmpty(bedName)) {
            locationComponents.add(getOrCreateLocationBed(departmentName, wardName, roomName, bedName));
        }
        return locationComponents;
    }

    /**
     * @author AXS (19.08.2024)
     */
    protected static enum LocationType {
        WARD("https://www.hospital.de/fhir/sid/wardnumber", "wa"),
        ROOM("https://www.hospital.de/fhir/sid/roomnumber", "ro"),
        BED("https://www.hospital.de/fhir/sid/bednumber", "bd");

        public final String identifierSystem;
        public final CodeableConcept physicalType;

        private LocationType(String identifierSystem, String physicalTypeCode) {
            this.identifierSystem = identifierSystem;
            physicalType = createCodeableConcept("http://terminology.hl7.org/CodeSystem/location-physical-type", physicalTypeCode);
        }
    }

    /**
     * @param locationType
     * @param value
     * @return
     */
    protected static Identifier createLocationIdentifier(LocationType locationType, String value) {
        Identifier identifier = new Identifier();
        identifier.setValue(value);
        identifier.setSystem(locationType.identifierSystem);
        return identifier;
    }

    /**
     * @param encounter
     * @throws Exception
     */
    protected void setPeriodAndStatus(Encounter encounter) throws Exception {
        Period period = createPeriod(Startdatum, Enddatum);
        encounter.setPeriod(period);
        EncounterStatus status = period.hasEnd() ? EncounterStatus.FINISHED : EncounterStatus.INPROGRESS;
        encounter.setStatus(status);
        Period parentPeriod = null;
        if (encounter instanceof EncounterLevel2 || encounter instanceof EncounterLevel3) {
            parentPeriod = previousEncounterLevel1.getPeriod();
            previousEncounterLevel1.setStatus(status);
        }
        if (encounter instanceof EncounterLevel3) {
            parentPeriod = previousEncounterLevel2.getPeriod();
            previousEncounterLevel2.setStatus(status);
        }
        if (parentPeriod != null) {
            // the top level encounter already has the maximum end
            if (parentPeriod.hasEnd()) {
                Date parentEnd = parentPeriod.getEnd();
                Date end = period.getEnd();
                if (!period.hasEnd() || end.after(parentEnd)) {
                    parentPeriod.setEnd(end);
                }
            }
        }
    }

    /**
     * @param id generated Encounter ID
     * @return
     * @throws Exception
     */
    private List<Identifier> convertIdentifier(String id) throws Exception {
        String dizID = getDIZId();
        return createIdentifier(id, dizID);
    }

    /**
     * @return
     * @throws Exception
     */
    private Coding getEncounterLevel1Class() throws Exception {
        Coding coding = createCoding(ENCOUNTER_LEVEL1_CLASS_RESOURCES.getCodeSystem(), Einrichtungskontaktklasse);
        return setCorrectCodeAndDisplayInClassCoding(coding);
    }

    /**
     * @return
     * @throws Exception
     */
    private static Coding getEncounterLevel2Class() throws Exception {
        // same as Level 1 here (see https://simplifier.net/packages/de.basisprofil.r4/1.4.0/files/656744)
        return previousEncounterLevel1 != null ? previousEncounterLevel1.getClass_() : null;
    }

    /**
     * @return
     * @throws Exception
     */
    private static Coding getEncounterLevel3Class() throws Exception {
        // same as Level 1 here (see https://simplifier.net/packages/de.basisprofil.r4/1.4.0/files/656744)
        return previousEncounterLevel1 != null ? previousEncounterLevel1.getClass_() : null;
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
        Encounter encounter = result.get(Fall, Encounter.class, encounterID);
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
        CodeableConcept diagnosisUse = EncounterConverter.createDiagnosisUse(diagnosisUseIdentifier);
        DiagnosisComponent diagnosisComponent = new DiagnosisComponent(conditionReference);
        diagnosisComponent.setUse(diagnosisUse);
        // maybe the same diagnosis was coded twice
        if (!encounter.getDiagnosis().contains(diagnosisComponent)) {
            encounter.addDiagnosis(diagnosisComponent);
        }
    }

    /**
     * @param encounterClass
     * @return the type of the encounter as {@link CodeableConcept}.
     */
    protected List<CodeableConcept> getEncounterType(Class<? extends Encounter> encounterClass) {
        String simpleClassName = encounterClass.getSimpleName();
        String codeSystemURL = ENCOUNTER_TYPE_RESOURCES.getCodeSystem();
        String typeCode = ENCOUNTER_TYPE_RESOURCES.get(simpleClassName + "_TYPE_CODE");
        String typeDisplay = ENCOUNTER_TYPE_RESOURCES.get(simpleClassName + "_TYPE_DISPLAY");
        return ImmutableList.of(createCodeableConcept(codeSystemURL, typeCode, typeDisplay, null));
    }

    //    /**
    //     * @return
    //     * @throws Exception
    //     */
    //    private List<EncounterLocationComponent> convertLocation() throws Exception {
    //        Identifier identifier = new Identifier();
    //        identifier.setSystem("https://diz.mii.de/fhir/CodeSystem/TestOrganisationAbteilungen");
    //        identifier.setValue(get(Fachabteilung));
    //        Reference reference = new Reference();
    //        reference.setIdentifier(identifier);
    //
    //        EncounterLocationComponent encounterLocationComponent = new EncounterLocationComponent();
    //        encounterLocationComponent.setLocation(reference);
    //        encounterLocationComponent.setStatus(Encounter.EncounterLocationStatus.COMPLETED);
    //        encounterLocationComponent.setPeriod(createPeriod(Startdatum, Enddatum));
    //        return Collections.singletonList(encounterLocationComponent);
    //    }
    //
    /**
     * @param encounter
     * @return
     */
    public static final boolean isLevel1Encounter(Encounter encounter) {
        return !encounter.hasPartOf();
    }

}
