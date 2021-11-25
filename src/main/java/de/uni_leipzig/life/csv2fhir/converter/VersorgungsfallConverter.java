package de.uni_leipzig.life.csv2fhir.converter;

import static de.uni_leipzig.life.csv2fhir.Converter.EmptyRecordValueErrorLevel.ERROR;
import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Versorgungsfall;
import static de.uni_leipzig.life.csv2fhir.converterFactory.VersorgungsfallConverterFactory.NeededColumns.Enddatum;
import static de.uni_leipzig.life.csv2fhir.converterFactory.VersorgungsfallConverterFactory.NeededColumns.Patient_ID;
import static de.uni_leipzig.life.csv2fhir.converterFactory.VersorgungsfallConverterFactory.NeededColumns.Startdatum;
import static de.uni_leipzig.life.csv2fhir.converterFactory.VersorgungsfallConverterFactory.NeededColumns.Versorgungsfallklasse;

import java.util.Collections;
import java.util.List;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Encounter.DiagnosisComponent;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

import de.uni_leipzig.imise.FHIRValidator;
import de.uni_leipzig.imise.utils.CodeSystemMapper;
import de.uni_leipzig.life.csv2fhir.Converter;

/**
 * @author jheuschkel (19.10.2020), AXS (05.11.2021)
 */
public class VersorgungsfallConverter extends Converter {

    /**  */
    String PROFILE = "https://www.medizininformatik-initiative.de/fhir/core/modul-fall/StructureDefinition/KontaktGesundheitseinrichtung";
    // https://simplifier.net/medizininformatikinitiative-modulfall/versorgungsfall-duplicate-2
    // https://simplifier.net/medizininformatikinitiative-modulfall/example-versorgungsfall

    /*
     * NotSupported : Terminology service failed while validating code ''
     * (system ''): Cannot retrieve valueset
     * 'https://www.medizininformatik-initiative.de/fhir/core/modul-fall/
     * ValueSet/Versorgungsfallklasse'
     */

    /**
     * Maps from human readable diagnosis role description to the correspondig
     * code system code.
     */
    public static final CodeSystemMapper diagnosisRoleKeyMapper = new CodeSystemMapper("Diagnosis_Role.map");

    /**
     * @param record
     * @param validator
     * @throws Exception
     */
    public VersorgungsfallConverter(CSVRecord record, FHIRValidator validator) throws Exception {
        super(record, validator);
    }

    @Override
    public List<Resource> convert() throws Exception {
        Encounter encounter = new Encounter();
        encounter.setMeta(new Meta().addProfile(PROFILE));
        encounter.setId(getEncounterId());
        encounter.setIdentifier(convertIdentifier());
        encounter.setStatus(Encounter.EncounterStatus.FINISHED);//TODO
        encounter.setClass_(createCoding("https://www.medizininformatik-initiative.de/fhir/core/modul-fall/CodeSystem/Versorgungsfallklasse", Versorgungsfallklasse, ERROR));
        encounter.setSubject(getPatientReference());
        encounter.setPeriod(createPeriod(Startdatum, Enddatum));
        return Collections.singletonList(encounter);
    }

    @Override
    protected Enum<?> getPatientIDColumnIdentifier() {
        return Versorgungsfall.getPIDColumnIdentifier();
    }

    @Override
    protected Reference getPatientReference() throws Exception {
        String patientId = record.get(Patient_ID);
        if (patientId != null) {
            return new Reference().setReference("Patient/" + patientId);
        }
        error("Patient-ID empty");
        return null;
    }

    /**
     * @return
     * @throws Exception
     */
    private List<Identifier> convertIdentifier() throws Exception {
        // generierte Encounternummer
        String id = getEncounterId();
        CodeableConcept identifierCode = createCodeableConcept("http://terminology.hl7.org/CodeSystem/v2-0203", "VN");
        Reference reference = new Reference()
                .setIdentifier(
                        new Identifier()
                                .setSystem("https://www.medizininformatik-initiative.de/fhir/core/NamingSystem/org-identifier")
                                .setValue(getDIZId()));
        return Collections.singletonList(
                new Identifier()
                        .setSystem("http://dummyurl") // must be an formal correct url!
                        .setValue(id)
                        .setType(identifierCode)
                        .setAssigner(reference));
    }

    /**
     * @param useIdentifier
     * @return
     */
    public static CodeableConcept createDiagnosisUse(String useIdentifier) {
        String codeSystem = diagnosisRoleKeyMapper.getCodeSystem();
        String code = diagnosisRoleKeyMapper.getHumanToCode(useIdentifier);
        String display = diagnosisRoleKeyMapper.getCodeToHuman(code);
        CodeableConcept diagnosisUse = new CodeableConcept();
        diagnosisUse.addCoding()
                .setSystem(codeSystem)
                .setCode(code)
                .setDisplay(display);
        return diagnosisUse;
    }

    /**
     * @param encounterID
     * @param procedure
     */
    public static void addDiagnosisToEncounter(String encounterID, Procedure procedure) {
        // The KDS definition needs a diagnosis use (min cardinality 1), but a procedure doesn't have this -> arbitrary default
        addDiagnosisToEncounter(encounterID, procedure, "Comorbidity diagnosis");
    }

    /**
     * @param encounterID
     * @param condition
     * @param diagnosisUseIdentifier
     */
    public static void addDiagnosisToEncounter(String encounterID, Condition condition, String diagnosisUseIdentifier) {
        if (diagnosisUseIdentifier == null) {
            diagnosisUseIdentifier = "Comorbidity diagnosis"; // default for missing values
        }
        addDiagnosisToEncounter(encounterID, (Resource) condition, diagnosisUseIdentifier);
    }

    /**
     * @param encounterID
     * @param conditionOrProcedureAsDiagnosis
     * @param diagnosisUseIdentifier
     */
    public static void addDiagnosisToEncounter(String encounterID, Resource conditionOrProcedureAsDiagnosis, String diagnosisUseIdentifier) {
        // encounter should be only null in error cases, but mybe we
        // should catch and log
        Encounter encounter = (Encounter) Versorgungsfall.getResource(encounterID);
        addDiagnosisToEncounter(encounter, conditionOrProcedureAsDiagnosis, diagnosisUseIdentifier);
    }

    /**
     * @param encounter
     * @param conditionOrProcedureAsDiagnosis
     * @param diagnosisUseIdentifier
     */
    public static void addDiagnosisToEncounter(Encounter encounter, Resource conditionOrProcedureAsDiagnosis, String diagnosisUseIdentifier) {
        // construct a valid DiagnosisComponent from condition or
        // procedure to add it as reference to the encounter
        Reference conditionReference = new Reference(conditionOrProcedureAsDiagnosis);
        // add diagnosis use to the diagnosis component
        CodeableConcept diagnosisUse = VersorgungsfallConverter.createDiagnosisUse(diagnosisUseIdentifier);
        DiagnosisComponent diagnosisComponent = new DiagnosisComponent(conditionReference);
        diagnosisComponent.setUse(diagnosisUse);
        // maybe the same diagnosis was coded twice
        if (!encounter.getDiagnosis().contains(diagnosisComponent)) {
            encounter.addDiagnosis(diagnosisComponent);
        }
    }

}
