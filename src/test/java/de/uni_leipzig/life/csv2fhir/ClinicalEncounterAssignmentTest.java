package de.uni_leipzig.life.csv2fhir;

import static org.junit.Assert.*;
import static de.uni_leipzig.life.csv2fhir.ContactIndex.Level.*;
import java.util.List;
import org.hl7.fhir.r4.model.*;
import org.junit.Test;

public class ClinicalEncounterAssignmentTest {
    private ConverterResult result(String values) throws Exception {
        return new ConverterResult(ConverterOptions.fromText("CONFIGURATION_VERSION=1\n" + values));
    }
    private Encounter contact(ConverterResult r, String patient, String id, ContactIndex.Level level,
            String start, String end) {
        Encounter e = new Encounter(); e.setId(id);
        e.getPeriod().setStartElement(new DateTimeType(start));
        if (end != null) e.getPeriod().setEndElement(new DateTimeType(end));
        r.contacts().add(e, patient, id, level, null, 1, false);
        return e;
    }
    private ClinicalEncounterAssignment policy(ConverterResult r) {
        return new ClinicalEncounterAssignment(r, new ContactOutputPolicy(r.getConverterOptions().configuration(), r.contacts()));
    }
    private void context(ConverterResult r, Resource resource, boolean explicitTime, String... ids) {
        r.recordInput(resource, new ConverterResult.InputContext("p", List.of(ids), 4, explicitTime));
    }
    @Test public void conditionMatchingUsesInputIdentityEvenWithoutPatientReference() throws Exception {
        var r = result("PATIENT_MODE=neither\nCONTACT_FACILITY_ENABLED=false\n");
        contact(r, "p", "department", DEPARTMENT, "2026-05-01", null);
        contact(r, "other", "wrong-patient", DEPARTMENT, "2026-05-02", null);
        Condition c = new Condition(); c.setId("c"); c.setRecordedDateElement(new DateTimeType("2026-05-03"));
        context(r, c, false);
        var p = policy(r);
        assertEquals("Encounter/department", ((Condition)p.output(c)).getEncounter().getReference());
        assertFalse(c.hasEncounter());
        assertTrue(p.issues().isEmpty());
    }
    @Test public void unavailableLevelDoesNotFallBackAndEncounterOffClearsReferences() throws Exception {
        var r = result("REFERENCE_CONDITION_ENCOUNTER=facility\nCONTACT_FACILITY_ENABLED=false\n");
        contact(r, "p", "department", DEPARTMENT, "2026-05-01", null);
        Condition c = new Condition(); c.setId("c"); c.setEncounter(new Reference("Encounter/old"));
        c.setRecordedDateElement(new DateTimeType("2026-05-03")); context(r, c, false);
        assertFalse(((Condition)policy(r).output(c)).hasEncounter());
        r = result("ENCOUNTER_ENABLED=false\n"); context(r, c, false);
        assertFalse(((Condition)policy(r).output(c)).hasEncounter());
        assertEquals("Encounter/old", c.getEncounter().getReference());
    }
    @Test public void laterTimestampCandidateCanMatchAndMissingTimestampIsReported() throws Exception {
        var r = result(""); contact(r, "p", "department", DEPARTMENT, "2026-05-01", "2026-05-03");
        DiagnosticReport report = new DiagnosticReport(); report.setId("report");
        report.setEffective(new DateTimeType("2026-04-01"));
        report.setIssuedElement(new InstantType("2026-05-02T00:00:00Z")); context(r, report, false);
        var p = policy(r);
        assertEquals("Encounter/department", ((DiagnosticReport)p.output(report)).getEncounter().getReference());
        Condition c = new Condition(); c.setId("c"); context(r, c, false);
        assertFalse(((Condition)p.output(c)).hasEncounter());
        assertEquals("missing-timestamp", p.issues().get(0).get("reason"));
        c.setRecordedDateElement(new DateTimeType("2026-06-01"));
        p.output(c);
        assertEquals("no-matching-contact", p.issues().get(1).get("reason"));
    }
    @Test public void supportedClinicalTypesUseTheirConfiguredFields() throws Exception {
        var r = result(""); contact(r, "p", "department", DEPARTMENT, "2026-05-01", null);
        var date = new DateTimeType("2026-05-02");
        Procedure procedure = new Procedure(); procedure.setPerformed(new Period().setStartElement(date));
        Observation lab = new Observation(); lab.setEffective(date);
        Observation vital = new Observation(); vital.setEffective(date);
        vital.addCategory().addCoding().setSystem("http://terminology.hl7.org/CodeSystem/observation-category").setCode("vital-signs");
        MedicationRequest request = new MedicationRequest(); request.setAuthoredOnElement(date);
        MedicationAdministration administration = new MedicationAdministration(); administration.setEffective(date);
        MedicationStatement statement = new MedicationStatement(); statement.setEffective(new Period().setStartElement(date));
        Immunization immunization = new Immunization(); immunization.setOccurrence(date);
        CarePlan care = new CarePlan(); care.setPeriod(new Period().setStartElement(date));
        var p = policy(r);
        int id = 0;
        for (Resource resource : List.of(procedure, lab, vital, request, administration, statement, immunization, care)) {
            resource.setId("r" + id++); context(r, resource, false);
            String property = resource instanceof MedicationAdministration || resource instanceof MedicationStatement ? "context" : "encounter";
            Resource output = p.output(resource);
            Reference reference = (Reference)output.getNamedProperty(property).getValues().get(0);
            assertEquals("Encounter/department", reference.getReference());
        }
        assertTrue(p.issues().isEmpty());
    }
    @Test public void documentPreservesSuppliedContactAndReportsConflictWithoutMovingLevel() throws Exception {
        var r = result(""); contact(r, "p", "case", FACILITY, "2026-05-01", "2026-05-02");
        contact(r, "p", "department", DEPARTMENT, "2026-05-03", null);
        DocumentReference doc = new DocumentReference(); doc.setId("doc");
        doc.setDateElement(new InstantType("2026-05-04T00:00:00Z")); context(r, doc, true, "case");
        var p = policy(r);
        assertEquals("Encounter/case", ((DocumentReference)p.output(doc)).getContext().getEncounterFirstRep().getReference());
        assertTrue(p.issues().get(0).get("reason").startsWith("explicit-contact-time-conflict"));
    }
    @Test public void documentUnresolvedOrOmittedInputIsNeverTreatedAsAbsent() throws Exception {
        for (String setting : List.of("", "CONTACT_FACILITY_ENABLED=false\n")) {
            var r = result(setting); contact(r, "p", "department", DEPARTMENT, "2026-05-01", null);
            contact(r, setting.isEmpty() ? "other-patient" : "p", "case", FACILITY, "2026-05-01", null);
            DocumentReference doc = new DocumentReference(); doc.setId("doc");
            doc.setDateElement(new InstantType("2026-05-02T00:00:00Z")); context(r, doc, true, "case");
            var p = policy(r);
            assertFalse(((DocumentReference)p.output(doc)).hasContext());
            assertEquals(1, p.issues().size());
        }
    }
    @Test public void documentStrategiesIgnoreSyntheticRunTimeAndRespectNone() throws Exception {
        for (String strategy : List.of("explicit-only", "fill-missing", "timestamp-only")) {
            var r = result("REFERENCE_DOCUMENT_REFERENCE_ASSIGNMENT_STRATEGY=" + strategy + "\n");
            contact(r, "p", "department", DEPARTMENT, "2026-05-01", null);
            DocumentReference doc = new DocumentReference(); doc.setId("doc");
            doc.setDateElement(new InstantType("2026-05-02T00:00:00Z")); context(r, doc, false);
            var p = policy(r);
            assertFalse(((DocumentReference)p.output(doc)).hasContext());
            context(r, doc, true);
            assertEquals(!strategy.equals("explicit-only"), ((DocumentReference)p.output(doc)).hasContext());
        }
        var r = result("REFERENCE_DOCUMENT_REFERENCE_ENCOUNTER=none\n");
        DocumentReference doc = new DocumentReference(); doc.setId("doc");
        doc.getContext().addEncounter(new Reference("Encounter/old")); context(r, doc, true, "old");
        assertFalse(((DocumentReference)policy(r).output(doc)).hasContext());
    }
    @Test public void outputEndRulesDoNotChangeMatchingAndExplicitReferencesKeepAmbulatoryContact() throws Exception {
        var r = result("ENCOUNTER_INPATIENT_END_POLICY=start\n");
        var amb = contact(r, "p", "amb", DEPARTMENT, "2026-05-02", null);
        amb.getClass_().setCode("AMB");
        var imp = contact(r, "p", "imp", DEPARTMENT, "2026-05-01", "2026-05-20");
        imp.getClass_().setCode("IMP");
        var ends = new EncounterOutputPolicy(r.getConverterOptions().configuration(), r.contacts());
        assertEquals("2026-05-01", ((Encounter)ends.output(imp)).getPeriod().getEndElement().getValueAsString());
        var observation = new Observation(); observation.setId("o");
        observation.setEffective(new DateTimeType("2026-05-10")); context(r, observation, false);
        assertEquals("Encounter/imp", ((Observation)policy(r).output(observation)).getEncounter().getReference());
        var document = new DocumentReference(); document.setId("d");
        document.setDateElement(new InstantType("2026-05-10T00:00:00Z")); context(r, document, true, "amb");
        assertEquals("Encounter/amb", ((DocumentReference)policy(r).output(document)).getContext().getEncounterFirstRep().getReference());
        var disabled = result("ENCOUNTER_INPATIENT_ENABLED=false\n");
        var disabledImp = contact(disabled, "p", "imp", DEPARTMENT, "2026-05-01", null);
        disabledImp.getClass_().setCode("IMP");
        var enabledAmb = contact(disabled, "p", "amb", DEPARTMENT, "2026-05-02", null);
        enabledAmb.getClass_().setCode("AMB"); context(disabled, observation, false);
        assertEquals("Encounter/amb", ((Observation)policy(disabled).output(observation)).getEncounter().getReference());
    }
}
