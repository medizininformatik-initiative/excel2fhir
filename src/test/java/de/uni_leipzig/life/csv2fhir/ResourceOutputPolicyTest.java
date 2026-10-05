package de.uni_leipzig.life.csv2fhir;

import static org.junit.Assert.*;
import java.util.List;
import org.hl7.fhir.r4.model.*;
import org.junit.Test;

public class ResourceOutputPolicyTest {
    @Test public void booleanSelectionsOmitEveryConfiguredResourceType() {
        Resource[] resources = {new Condition(), new Procedure(), new MedicationRequest(), new MedicationAdministration(),
                new MedicationStatement(), new Immunization(), new DiagnosticReport(), new CarePlan(), new DocumentReference(), new Consent()};
        String settings = "CONDITION_ENABLED=false\nPROCEDURE_ENABLED=false\nMEDICATION_REQUEST_ENABLED=false\n"
                + "MEDICATION_ADMINISTRATION_ENABLED=false\nMEDICATION_STATEMENT_ENABLED=false\nIMMUNIZATION_ENABLED=false\n"
                + "DIAGNOSTIC_REPORT_ENABLED=false\nCARE_PLAN_ENABLED=false\nDOCUMENT_REFERENCE_ENABLED=false\nCONSENT_ENABLED=false\n";
        for (Resource resource : resources) resource.setId(resource.fhirType());
        var p = policy(settings, resources);
        for (Resource resource : resources) assertNull(resource.fhirType(), p.output(resource));
    }
    private ResourceOutputPolicy policy(String settings, Resource... resources) {
        return new ResourceOutputPolicy(ContractConfiguration.parse("CONFIGURATION_VERSION=1\n" + settings), List.of(resources));
    }
    private Medication medication() {
        Medication m = new Medication(); m.setId("drug");
        m.addIdentifier().setSystem("urn:product").setValue("123");
        m.setCode(new CodeableConcept().setText("Example medication")); return m;
    }
    private MedicationRequest request() {
        MedicationRequest r = new MedicationRequest(); r.setId("request");
        r.setMedication(new Reference("Medication/drug")); return r;
    }
    @Test public void medicationModesCoverEveryCombinationWithoutMutatingInput() {
        var m = medication(); var r = request();
        for (boolean literal : List.of(false, true)) for (boolean description : List.of(false, true)) {
            var p = policy("MEDICATION_MODE=reference-only\nMEDICATION_EXISTING_REFERENCE=" + literal
                    + "\nMEDICATION_DESCRIPTION=" + description + "\n", m, r);
            assertNull(p.output(m));
            var output = (MedicationRequest)p.output(r);
            assertEquals(literal || description, output.hasMedication());
            if (output.hasMedication()) {
                var ref = output.getMedicationReference();
                assertEquals(literal, ref.hasReference());
                assertEquals(description, ref.hasIdentifier());
                assertEquals(description, ref.hasDisplay());
                if (description) {
                    assertEquals("123", ref.getIdentifier().getValue());
                    assertEquals("urn:product", ref.getIdentifier().getSystem());
                    assertEquals("Example medication", ref.getDisplay());
                }
            }
        }
        assertFalse(((MedicationRequest)policy("MEDICATION_MODE=neither\n", m, r).output(r)).hasMedication());
        assertNotNull(policy("", m, r).output(m));
        assertEquals("Medication/drug", r.getMedicationReference().getReference());
        assertFalse(r.getMedicationReference().hasIdentifier());
    }
    @Test public void locationDescriptionUsesKnownIdentityAndPreservesLocationDetails() {
        Location location = new Location(); location.setId("ward"); location.setName("Station A");
        Encounter e = new Encounter(); e.setId("e");
        e.addLocation().setLocation(new Reference("Location/ward")).setStatus(Encounter.EncounterLocationStatus.ACTIVE);
        var p = policy("LOCATION_MODE=reference-only\nLOCATION_EXISTING_REFERENCE=false\nLOCATION_DESCRIPTION=true\n", location, e);
        Encounter output = (Encounter)p.output(e);
        assertNull(p.output(location));
        var reference = output.getLocationFirstRep().getLocation();
        assertFalse(reference.hasReference());
        assertEquals("ward", reference.getIdentifier().getValue());
        assertEquals("Station A", reference.getDisplay());
        assertEquals(Encounter.EncounterLocationStatus.ACTIVE, output.getLocationFirstRep().getStatus());
        assertFalse(((Encounter)policy("LOCATION_MODE=neither\n", location, e).output(e)).hasLocation());
        assertEquals("Location/ward", e.getLocationFirstRep().getLocation().getReference());
    }
    @Test public void disabledObservationCategoryRemovesOnlyItsOwnOutputAndReferences() {
        Observation lab = new Observation(); lab.setId("lab");
        Observation vital = new Observation(); vital.setId("vital");
        vital.addCategory().addCoding().setSystem("http://terminology.hl7.org/CodeSystem/observation-category").setCode("vital-signs");
        DiagnosticReport report = new DiagnosticReport(); report.setId("report");
        report.addResult(new Reference("Observation/lab")); report.addResult(new Reference("Observation/vital"));
        report.addResult(new Reference("https://example.test/fhir/Observation/lab"));
        var p = policy("OBSERVATION_LABORATORY_ENABLED=false\n", lab, vital, report);
        assertNull(p.output(lab)); assertNotNull(p.output(vital));
        var output = (DiagnosticReport)p.output(report);
        assertEquals(2, output.getResult().size());
        assertEquals("Observation/vital", output.getResult().get(0).getReference());
        assertEquals("https://example.test/fhir/Observation/lab", output.getResult().get(1).getReference());
        assertEquals(3, report.getResult().size());
    }
    @Test public void deselectedProcedureAndConditionRemoveTheirEncounterDiagnosisEntries() {
        Procedure procedure = new Procedure(); procedure.setId("p");
        Condition condition = new Condition(); condition.setId("c");
        Encounter e = new Encounter(); e.setId("e");
        e.addDiagnosis().setCondition(new Reference("Procedure/p"));
        e.addDiagnosis().setCondition(new Reference("Condition/c"));
        var p = policy("PROCEDURE_ENABLED=false\nCONDITION_ENABLED=false\n", procedure, condition, e);
        assertNull(p.output(procedure)); assertNull(p.output(condition));
        assertFalse(((Encounter)p.output(e)).hasDiagnosis());
        assertEquals(2, e.getDiagnosis().size());
    }
    @Test public void medicationReferenceModesApplyToStatementsAndAdministrations() {
        MedicationStatement s = new MedicationStatement(); s.setId("s"); s.setMedication(new Reference("Medication/drug"));
        MedicationAdministration a = new MedicationAdministration(); a.setId("a"); a.setMedication(new Reference("Medication/drug"));
        var p = policy("MEDICATION_MODE=reference-only\nMEDICATION_DESCRIPTION=true\n", medication(), s, a);
        assertTrue(((MedicationStatement)p.output(s)).getMedicationReference().hasIdentifier());
        assertTrue(((MedicationAdministration)p.output(a)).getMedicationReference().hasIdentifier());
        p = policy("MEDICATION_MODE=neither\n", medication(), s, a);
        assertFalse(((MedicationStatement)p.output(s)).hasMedication());
        assertFalse(((MedicationAdministration)p.output(a)).hasMedication());
    }
}
