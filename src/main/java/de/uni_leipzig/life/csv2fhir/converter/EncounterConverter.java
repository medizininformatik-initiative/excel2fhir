package de.uni_leipzig.life.csv2fhir.converter;

import static com.google.common.base.Strings.isNullOrEmpty;
import static de.uni_leipzig.life.csv2fhir.BundleFunctions.createReference;
import static de.uni_leipzig.life.csv2fhir.ConverterOptions.IntOption.START_ID_ENCOUNTER_LEVEL_2;
import static de.uni_leipzig.life.csv2fhir.ConverterOptions.IntOption.START_ID_ENCOUNTER_LEVEL_3;
import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Fall;
import static de.uni_leipzig.life.csv2fhir.converter.EncounterConverter.Encounter_Columns.Bett;
import static de.uni_leipzig.life.csv2fhir.converter.EncounterConverter.Encounter_Columns.Einrichtungskontaktklasse;
import static de.uni_leipzig.life.csv2fhir.converter.EncounterConverter.Encounter_Columns.Ende;
import static de.uni_leipzig.life.csv2fhir.converter.EncounterConverter.Encounter_Columns.Fachabteilung;
import static de.uni_leipzig.life.csv2fhir.converter.EncounterConverter.Encounter_Columns.Start;
import static de.uni_leipzig.life.csv2fhir.converter.EncounterConverter.Encounter_Columns.Station;
import static de.uni_leipzig.life.csv2fhir.converter.EncounterConverter.Encounter_Columns.Zimmer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Encounter.DiagnosisComponent;
import org.hl7.fhir.r4.model.Encounter.EncounterStatus;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Location.LocationStatus;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

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
public class EncounterConverter extends Converter {
    private final CSVRecord inputRecord;

    public enum ContactColumn implements TableColumnIdentifier {
        Kontaktart;
        @Override public boolean isMandatory() { return false; }
    }

    public static final Map<String, String> CONTACT_KINDS = Map.of(
            "Normalstationär", "normalstationaer", "Intensivstationär", "intensivstationaer",
            "Operation", "operation", "Untersuchung und Behandlung", "ub", "Konsil", "konsil");
    private static final java.util.Set<String> SECONDARY_KINDS = java.util.Set.of(
            "Operation", "Untersuchung und Behandlung", "Konsil");
    private static ConverterResult activeResult;
    private static Encounter primaryContact;
    private static boolean primaryEndDerived, facilityBound;
    private static final List<Encounter> secondaryContacts = new ArrayList<>();
    private static final Map<Encounter, Map<String, String>> derivedEnds = new java.util.IdentityHashMap<>();

    private String value(Object column) {
        String text = get(column);
        return text == null || text.isBlank() ? null : text.trim();
    }

    private void period(Encounter encounter, Period period) {
        encounter.setPeriod(period);
        encounter.setStatus(period.hasEnd() ? EncounterStatus.FINISHED : EncounterStatus.INPROGRESS);
        for (var location : encounter.getLocation()) {
            location.setPeriod(period.copy());
            location.setStatus(period.hasEnd() ? Encounter.EncounterLocationStatus.COMPLETED
                    : Encounter.EncounterLocationStatus.ACTIVE);
        }
    }

    private void derivedEnd(Encounter child, Encounter source) {
        Period p = child.getPeriod();
        p.setEndElement(source.getPeriod().getEndElement().copy());
        period(child, p);
        Map<String, String> note = derivedEnds.get(child);
        if (note == null) {
            note = new java.util.LinkedHashMap<>();
            note.put("encounter", child.getId());
            note.put("record", Long.toString(inputRecord.getRecordNumber()));
            note.put("reason", "Fehlendes Kontaktende aus dem zugehörigen primären Kontakt bzw. Einrichtungskontakt abgeleitet; keine Prozedurzeit.");
            result.contactEndDerivations.add(note);
            derivedEnds.put(child, note);
        }
        note.put("sourceEncounter", source.getId());
        note.put("end", p.hasEnd() ? p.getEndElement().getValueAsString() : "offen");
    }

    private void closePrimary(org.hl7.fhir.r4.model.DateTimeType boundary) {
        if (primaryContact == null) return;
        if (!primaryContact.getPeriod().hasEnd() || primaryEndDerived) {
            for (Encounter secondary : secondaryContacts) {
                if (secondary.getPeriod().getStart().after(boundary.getValue())
                        || (!derivedEnds.containsKey(secondary) && secondary.getPeriod().hasEnd()
                            && secondary.getPeriod().getEnd().after(boundary.getValue())))
                    throw new IllegalArgumentException("Sekundärkontakt liegt außerhalb des primären Aufenthalts");
            }
            primaryContact.getPeriod().setEndElement(boundary.copy());
            period(primaryContact, primaryContact.getPeriod());
            if (derivedEnds.containsKey(primaryContact)) {
                derivedEnds.get(primaryContact).put("end", boundary.getValueAsString());
                derivedEnds.get(primaryContact).put("reason", "Fehlendes primäres Kontaktende aus dem Beginn des nächsten primären Aufenthalts abgeleitet.");
            }
            for (Encounter secondary : secondaryContacts)
                if (derivedEnds.containsKey(secondary)) derivedEnd(secondary, primaryContact);
        }
        secondaryContacts.clear();
    }

    private Encounter newContact(Encounter encounter, String id, Encounter parent, Period p, String kind) throws Exception {
        encounter.setId(id);
        encounter.setSubject(getPatientReference());
        encounter.setIdentifier(convertIdentifier(id));
        encounter.setMeta(getMeta());
        encounter.setType(new ArrayList<>(getEncounterType(encounter.getClass())));
        if (parent != null) {
            encounter.setPartOf(new Reference("Encounter/" + parent.getId()));
            encounter.setClass_(previousEncounterLevel1.getClass_().copy());
        } else encounter.setClass_(getEncounterLevel1Class());
        if (kind != null) encounter.addType(createCodeableConcept("http://fhir.de/CodeSystem/kontaktart-de", CONTACT_KINDS.get(kind), kind, null));
        period(encounter, p.copy());
        return encounter;
    }

    private void locations(Encounter encounter, String department, List<Resource> resources) throws Exception {
        Location parent = null;
        String path = getDIZId() + "|" + Objects.toString(department, "");
        String[] names = {value(Station), value(Zimmer), value(Bett)};
        String[] types = {"wa", "ro", "bd"};
        for (int i = 0; i < names.length; i++) {
            if (names[i] == null) continue;
            path += "|" + types[i] + "|" + names[i];
            Location location = new Location();
            location.setId(ClinicalValues.resourceId(getDIZId(), "Location", path));
            location.setName(names[i]).setStatus(LocationStatus.ACTIVE);
            location.setPhysicalType(new CodeableConcept(new Coding("http://terminology.hl7.org/CodeSystem/location-physical-type", types[i], null)));
            if (parent != null) location.setPartOf(new Reference("Location/" + parent.getId()));
            encounter.addLocation().setLocation(new Reference("Location/" + location.getId()))
                    .setPhysicalType(location.getPhysicalType().copy());
            locationIDToLocation.put(location.getId(), location);
            resources.add(location);
            parent = location;
        }
        period(encounter, encounter.getPeriod());
    }

    private static void inside(Period child, Period parent) {
        if ((parent.hasStart() && child.getStart().before(parent.getStart()))
                || (parent.hasEnd() && (child.getStart().compareTo(parent.getEnd()) >= 0
                    || (child.hasEnd() && child.getEnd().after(parent.getEnd())))))
            throw new IllegalArgumentException("Kontaktzeitraum liegt außerhalb des übergeordneten Aufenthalts");
    }

    @Override protected List<Resource> convertInternal() throws Exception {
        if (activeResult != result) { resetStateForTesting(); activeResult = result; }
        // Retired explicit hierarchy input must never be silently reinterpreted.
        for (String old : List.of("Kontakt-ID", "Kontaktebene", "Übergeordneter Kontakt")) {
            if (inputRecord.isMapped(old) && !isNullOrEmpty(inputRecord.get(old)))
                throw new IllegalArgumentException("Veraltete Kontaktspalte " + old + ": Fall auf die implizite Eingabe umstellen");
        }
        String kind = value(ContactColumn.Kontaktart);
        if (kind != null && !CONTACT_KINDS.containsKey(kind)) throw new IllegalArgumentException("Unbekannte Kontaktart: " + kind);
        boolean secondary = SECONDARY_KINDS.contains(kind == null ? "" : kind);
        String department = value(Fachabteilung);
        boolean hasPlaces = value(Station) != null || value(Zimmer) != null || value(Bett) != null;
        if (kind != null && !hasPlaces) throw new IllegalArgumentException("Kontaktart benötigt mindestens Station, Zimmer oder Bett");
        Period p = new Period().setStartElement(ClinicalValues.date(value(Start))).setEndElement(ClinicalValues.date(value(Ende)));
        if (!p.hasStart() || !p.getStartElement().hasValue()) throw new IllegalArgumentException("Kontaktbeginn erforderlich");
        if (p.hasEnd() && p.getEnd().before(p.getStart())) throw new IllegalArgumentException("Kontaktende liegt vor Beginn");
        String rootId = getEncounterId();
        boolean newRoot = previousEncounterLevel1 == null || !previousEncounterLevel1.getSubject().getReference().equals(getPatientReference().getReference())
                || (!isNullOrEmpty(rootId) && !rootId.equals(previousEncounterLevel1.getId()));
        if (newRoot && (secondary || isNullOrEmpty(rootId))) throw new IllegalArgumentException("Einrichtungskontakt muss vor seinen Aufenthalten stehen");
        if (!newRoot) {
            if (facilityBound) inside(p, previousEncounterLevel1.getPeriod());
            if (value(Einrichtungskontaktklasse) != null
                    && !Objects.equals(getEncounterLevel1Class().getCode(), previousEncounterLevel1.getClass_().getCode()))
                throw new IllegalArgumentException("Widersprüchliche Einrichtungskontaktklasse im selben Fall");
            if (value(Encounter_Columns.Aufnahmegrund) != null) throw new IllegalArgumentException("Aufnahmegrund nur in der ersten Fallzeile angeben");
        }
        if (secondary) {
            if (primaryContact == null) throw new IllegalArgumentException("Sekundärkontakt benötigt einen vorhergehenden primären Versorgungsstellenkontakt");
            inside(p, primaryContact.getPeriod());
            Encounter parent = previousEncounterLevel2 != null ? previousEncounterLevel2 : previousEncounterLevel1;
            if (previousEncounterLevel2 != null) inside(p, previousEncounterLevel2.getPeriod());
            String id = previousEncounterLevel1.getId() + ResourceIdSuffix.ENCOUNTER_LEVEL_3
                    + result.getNextId(Fall, EncounterLevel3.class, START_ID_ENCOUNTER_LEVEL_3);
            Encounter contact = newContact(new EncounterLevel3(), id, parent, p, kind);
            if (department != null) contact.setServiceType(createCodeableConcept(Fachabteilung, ENCOUNTER_LEVEL2_DEPARTMENT_RESOURCES));
            if (!p.hasEnd()) derivedEnd(contact, primaryContact);
            secondaryContacts.add(contact);
            List<Resource> resources = new ArrayList<>(); resources.add(contact);
            locations(contact, department, resources);
            return resources;
        }
        if (!newRoot && primaryContact != null && (hasPlaces || department != null)) {
            if (p.getStart().before(primaryContact.getPeriod().getStart())
                    || (!primaryEndDerived && primaryContact.getPeriod().hasEnd() && p.getStart().before(primaryContact.getPeriod().getEnd())))
                throw new IllegalArgumentException("Überlappende oder unsortierte primäre Aufenthalte; Zuordnung ist nicht eindeutig");
            closePrimary(p.getStartElement());
        }
        List<Resource> resources = new ArrayList<>();
        if (newRoot) {
            previousEncounterLevel1 = newContact(new EncounterLevel1(), rootId, null, p, null);
            var reason = AdmissionReasonValues.extension(value(Encounter_Columns.Aufnahmegrund));
            if (reason != null) previousEncounterLevel1.addExtension(reason);
            resources.add(previousEncounterLevel1);
            previousEncounterLevel2 = null; previousDepartmentName = RANDOM_DEFULT_VALUE;
            primaryContact = null; secondaryContacts.clear();
            facilityBound = department == null && !hasPlaces;
        } else if (!facilityBound) {
            // Original CSV continuation convention: the first row can also contain
            // the first ward stay; later primary rows extend the facility period.
            updateParentPeriodAndStatus(previousEncounterLevel1, p, p.hasEnd() ? EncounterStatus.FINISHED : EncounterStatus.INPROGRESS);
        }
        if (department != null && !department.equals(previousDepartmentName)) {
            if (previousEncounterLevel2 != null) {
                previousEncounterLevel2.getPeriod().setEndElement(p.getStartElement().copy());
                period(previousEncounterLevel2, previousEncounterLevel2.getPeriod());
            }
            String id = previousEncounterLevel1.getId() + ResourceIdSuffix.ENCOUNTER_LEVEL_2
                    + result.getNextId(Fall, EncounterLevel2.class, START_ID_ENCOUNTER_LEVEL_2);
            previousEncounterLevel2 = newContact(new EncounterLevel2(), id, previousEncounterLevel1, p, null);
            previousEncounterLevel2.setServiceType(createCodeableConcept(Fachabteilung, ENCOUNTER_LEVEL2_DEPARTMENT_RESOURCES));
            previousDepartmentName = department;
            resources.add(previousEncounterLevel2);
        } else if (previousEncounterLevel2 != null && hasPlaces) {
            updateParentPeriodAndStatus(previousEncounterLevel2, p, p.hasEnd() ? EncounterStatus.FINISHED : EncounterStatus.INPROGRESS);
        }
        if (hasPlaces) {
            String id = previousEncounterLevel1.getId() + ResourceIdSuffix.ENCOUNTER_LEVEL_3
                    + result.getNextId(Fall, EncounterLevel3.class, START_ID_ENCOUNTER_LEVEL_3);
            primaryContact = newContact(new EncounterLevel3(), id,
                    previousEncounterLevel2 != null ? previousEncounterLevel2 : previousEncounterLevel1, p, kind);
            primaryEndDerived = !p.hasEnd();
            if (primaryEndDerived) derivedEnd(primaryContact, previousEncounterLevel1);
            updateParentPeriodAndStatus(previousEncounterLevel2, primaryContact.getPeriod(), primaryContact.getStatus());
            resources.add(primaryContact);
            locations(primaryContact, department, resources);
        } else if (department != null) primaryContact = null;
        return resources;
    }

    public static final String ENCOUNTER_IDENTIFIER_SYSTEM = "http://www.hospital_xyz_case_id_system.de";

    /**
     * toString() result of these enum values are the names of the columns in the
     * correspunding excel sheet.
     */
    public static enum Encounter_Columns implements TableColumnIdentifier {
        Start,
        Ende,
        Einrichtungskontaktklasse,
        Fachabteilung,
        Station,
        Zimmer,
        Bett,
        Aufnahmegrund;

        @Override
        public String toString() {
            return this == Aufnahmegrund ? AdmissionReasonValues.COLUMN : name();
        }
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
    public static final CodeSystemMapper ENCOUNTER_LEVEL1_CLASS_RESOURCES = new CodeSystemMapper(
            "EncounterLevel1_Class.map");

    /**
     * Maps from human readable department description to the number code for the
     * department.
     */
    private final CodeSystemMapper ENCOUNTER_LEVEL2_DEPARTMENT_RESOURCES = new CodeSystemMapper(
            "EncounterLevel2_Department.map");

    /**
     * Maps from human readable diagnosis role description to the correspondig code
     * system code.
     */
    public static final CodeSystemMapper DIAGNOSIS_ROLE_RESOURCES = new CodeSystemMapper("Diagnosis_Role.map");

    /**
     * If the column with the PID is empty then the last valid PID of a previous
     * record is taken. This enables that the tbale mu´st no be filled in all
     * columns with the same values and is better structured and more readable.
     */
    // private static String previousPatientReference;

    private static Encounter previousEncounterLevel1;
    private static Encounter previousEncounterLevel2;

    private static final String RANDOM_DEFULT_VALUE = "" + System.nanoTime();

    // random string to identify a null value in the first rows
    private static String previousDepartmentName = RANDOM_DEFULT_VALUE;
    // private static String previousWardName = RANDOM_DEFULT_VALUE;
    // private static String previousRoomName = RANDOM_DEFULT_VALUE;
    // private static String previousBedName = RANDOM_DEFULT_VALUE;

    private static final Map<String, Location> locationIDToLocation = new HashMap<>();

    /**
     * Even if they (unfortunately) do not exist in the KDS, they are created here
     * because of actual encounter types. This allows us to distinguish them
     * clearly.
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
    public EncounterConverter(CSVRecord record, String previousRecordPID, ConverterResult result,
            FHIRValidator validator, ConverterOptions options) throws Exception {
        super(record, previousRecordPID, result, validator, options);
        inputRecord = record;
    }

    /**
     * @return
     */
    public static final Collection<Location> getLocations() {
        return locationIDToLocation.values();
    }

    static void resetStateForTesting() {
        activeResult = null; primaryContact = null; secondaryContacts.clear(); derivedEnds.clear();
        previousEncounterLevel1 = null;
        previousEncounterLevel2 = null;
        previousDepartmentName = RANDOM_DEFULT_VALUE;
        locationIDToLocation.clear();
    }

    private static void updateParentPeriodAndStatus(Encounter parentEncounter, Period period, EncounterStatus status) {
        if (parentEncounter == null) {
            return;
        }
        Period parentPeriod = parentEncounter.getPeriod();
        if (!period.hasEnd() || !parentPeriod.hasEnd() || period.getEnd().after(parentPeriod.getEnd())) {
            parentPeriod.setEndElement(period.getEndElement().copy());
        }
        parentEncounter.setStatus(parentPeriod.hasEnd() ? EncounterStatus.FINISHED : EncounterStatus.INPROGRESS);
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
     * @param coding
     */
    private static Coding setCorrectCodeAndDisplayInClassCoding(Coding coding) {
        if (coding != null) {
            // replace the code string from by the correct code and display from the
            // resource map
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
                                .setSystem(
                                        "https://www.medizininformatik-initiative.de/fhir/core/NamingSystem/org-identifier")
                                .setValue(dizID));

        Identifier identifier = new Identifier()
                .setValue(encounterID)
                .setSystem(ENCOUNTER_IDENTIFIER_SYSTEM)
                .setType(createCodeableConcept("http://terminology.hl7.org/CodeSystem/v2-0203", "VN"))
                .setAssigner(reference);

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
        addDiagnosisResourceToEncounter(result, encounterID, procedure, null);
    }

    /**
     * @param result
     * @param encounterID
     * @param condition
     * @param diagnosisUseIdentifier
     */
    public static void addDiagnosisToEncounter(ConverterResult result, String encounterID, Condition condition,
            String diagnosisUseIdentifier) {
        addDiagnosisResourceToEncounter(result, encounterID, condition, diagnosisUseIdentifier);
    }

    /**
     * @param result
     * @param encounterID
     * @param conditionOrProcedureAsDiagnosis
     * @param diagnosisUseIdentifier
     */
    private static void addDiagnosisResourceToEncounter(ConverterResult result, String encounterID,
            Resource conditionOrProcedureAsDiagnosis, String diagnosisUseIdentifier) {
        // The KDS definition needs a diagnosis use (min cardinality 1), but a
        // procedure doesn't have this -> arbitrary default
        if (diagnosisUseIdentifier == null) { // default for missing values
            String defaultDiagnosisRoleCode = DIAGNOSIS_ROLE_RESOURCES.get("DEFAULT_DIAGNOSIS_ROLE_CODE");
            diagnosisUseIdentifier = DIAGNOSIS_ROLE_RESOURCES.getFirstBackwardKey(defaultDiagnosisRoleCode);
        }
        // encounter should be only null in error cases, but mybe we
        // should catch and log
        Encounter encounter = result.get(Fall, Encounter.class, encounterID);
        addDiagnosisResourceToEncounter(encounter, conditionOrProcedureAsDiagnosis, diagnosisUseIdentifier);
    }

    /**
     * @param encounter
     * @param conditionOrProcedureAsDiagnosis
     * @param diagnosisUseIdentifier
     */
    private static void addDiagnosisResourceToEncounter(Encounter encounter, Resource conditionOrProcedureAsDiagnosis,
            String diagnosisUseIdentifier) {
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

    // /**
    // * @return
    // * @throws Exception
    // */
    // private List<EncounterLocationComponent> convertLocation() throws Exception
    // {
    // Identifier identifier = new Identifier();
    // identifier.setSystem("https://diz.mii.de/fhir/CodeSystem/TestOrganisationAbteilungen");
    // identifier.setValue(get(Fachabteilung));
    // Reference reference = new Reference();
    // reference.setIdentifier(identifier);
    //
    // EncounterLocationComponent encounterLocationComponent = new
    // EncounterLocationComponent();
    // encounterLocationComponent.setLocation(reference);
    // encounterLocationComponent.setStatus(Encounter.EncounterLocationStatus.COMPLETED);
    // encounterLocationComponent.setPeriod(createPeriod(Start, Ende));
    // return Collections.singletonList(encounterLocationComponent);
    // }
    //
    /**
     * @param encounter
     * @return
     */
    public static final boolean isLevel1Encounter(Encounter encounter) {
        return !encounter.hasPartOf();
    }

}
