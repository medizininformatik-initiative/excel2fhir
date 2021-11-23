package de.uni_leipzig.life.csv2fhir.converter;

import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Abteilungsfall;
import static de.uni_leipzig.life.csv2fhir.converterFactory.AbteilungsfallConverterFactory.NeededColumns.Enddatum;
import static de.uni_leipzig.life.csv2fhir.converterFactory.AbteilungsfallConverterFactory.NeededColumns.Fachabteilung;
import static de.uni_leipzig.life.csv2fhir.converterFactory.AbteilungsfallConverterFactory.NeededColumns.Startdatum;

import java.util.Collections;
import java.util.List;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Encounter.EncounterLocationComponent;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

import de.uni_leipzig.imise.utils.CodeSystemMapper;
import de.uni_leipzig.life.csv2fhir.Converter;

/**
 * @author fheuschkel (29.10.2020), fmeinecke, AXS
 */
public class AbteilungsfallConverter extends Converter {

    /** Simple counter to generate unique identifier */
    static int n = 1;

    /**
     * Maps from human readable department description to the number code for
     * the department.
     */
    private final CodeSystemMapper departmentKeyMapper = new CodeSystemMapper("Department_Key.map");

    String PROFILE = "https://www.medizininformatik-initiative.de/fhir/core/modul-fall/StructureDefinition/KontaktGesundheitseinrichtung";
    // https://simplifier.net/medizininformatikinitiative-modulfall/abteilungsfall-duplicate-2
    // https://simplifier.net/medizininformatikinitiative-modulfall/example-abteilungsfall

    /*
     * NotSupported : Terminology service failed while validating code ''
     * (system ''): Cannot retrieve valueset
     * 'https://www.medizininformatik-initiative.de/fhir/core/modul-fall/
     * ValueSet/Abteilungsfallklasse' Invalid : Instance count for
     * 'Encounter.serviceType.coding:fab' is 0, which is not within the
     * specified cardinality of 1..1 Invalid : Instance count for
     * 'Encounter.location' is 0, which is not within the specified cardinality
     * of 1..*
     */
    public AbteilungsfallConverter(CSVRecord record) throws Exception {
        super(record);
    }

    @Override
    protected Enum<?> getPatientIDColumnIdentifier() {
        return Abteilungsfall.getPIDColumnIdentifier();
    }

    @Override
    public List<Resource> convert() throws Exception {
        Encounter encounter = new Encounter();
        encounter.setId(getEncounterId() + "-A-" + n++);
        encounter.setMeta(new Meta().addProfile(PROFILE));
        encounter.setStatus(Encounter.EncounterStatus.FINISHED);
        encounter.setClass_(createCoding("https://www.medizininformatik-initiative.de/fhir/core/modul-fall/CodeSystem/Abteilungsfallklasse", "ub"));
        encounter.setServiceType(createCodeableConcept(Fachabteilung, departmentKeyMapper));
        encounter.setSubject(getPatientReference());
        encounter.setPeriod(createPeriod(Startdatum, Enddatum));
        //        encounter.setLocation(convertLocation()); bringt nichts
        encounter.setPartOf(getEncounterReference());
        return Collections.singletonList(encounter);
    }

    /**
     * @return
     * @throws Exception
     */
    private List<EncounterLocationComponent> convertLocation() throws Exception {
        Identifier identifier = new Identifier();
        identifier.setSystem("https://diz.mii.de/fhir/CodeSystem/TestOrganisationAbteilungen");
        identifier.setValue(record.get(Fachabteilung));
        Reference reference = new Reference();
        reference.setIdentifier(identifier);

        EncounterLocationComponent encounterLocationComponent = new EncounterLocationComponent();
        encounterLocationComponent.setLocation(reference);
        encounterLocationComponent.setStatus(Encounter.EncounterLocationStatus.COMPLETED);
        encounterLocationComponent.setPeriod(createPeriod(Startdatum, Enddatum));
        return Collections.singletonList(encounterLocationComponent);
    }

    /**
     * @param encounterID
     * @param conditionOrProcedureAsDiagnosis
     * @param diagnosisUseIdentifier
     */
    public static void addDiagnosisToEncounterInternal(String encounterID, Resource conditionOrProcedureAsDiagnosis, String diagnosisUseIdentifier) {
        // encounter should be only null in error cases, but mybe we
        // should catch and log
        Encounter encounter = (Encounter) Abteilungsfall.getResource(encounterID);
        VersorgungsfallConverter.addDiagnosisToEncounter(encounter, conditionOrProcedureAsDiagnosis, diagnosisUseIdentifier);
    }

}
