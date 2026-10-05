package de.uni_leipzig.life.csv2fhir;

import static org.junit.Assert.*;
import java.util.List;
import java.util.Set;
import org.hl7.fhir.r4.model.*;
import org.junit.Test;

public class MedicationTransformationsTest {
    private ConverterResult result(String settings) throws Exception {
        return new ConverterResult(ConverterOptions.fromText("CONFIGURATION_VERSION=1\n" + settings));
    }
    private MedicationAdministration administration() {
        var a = new MedicationAdministration(); a.setId("admin");
        a.setStatus(MedicationAdministration.MedicationAdministrationStatus.COMPLETED);
        a.setSubject(new Reference("Patient/p")); a.setMedication(new Reference("Medication/m"));
        a.setEffective(new DateTimeType("2026-05-02"));
        a.getDosage().setDose(new Quantity().setValue(2).setUnit("mg").setSystem("http://unitsofmeasure.org").setCode("mg"));
        a.getMeta().addSecurity().setSystem("http://terminology.hl7.org/CodeSystem/v3-Confidentiality").setCode("R");
        return a;
    }
    private MedicationStatement statement() {
        var s = new MedicationStatement(); s.setId("statement");
        s.setStatus(MedicationStatement.MedicationStatementStatus.ACTIVE);
        s.setMedication(new Reference("Medication/m"));
        s.addDosage().setText("Daily regimen");
        return s;
    }
    @Test public void bothDirectionsRunOnceAndDerivedIdsAreStableAndDistinct() throws Exception {
        var r = result("MEDICATION_ADMINISTRATION_TREATMENT=add-statement\nMEDICATION_STATEMENT_TREATMENT=add-administration\n");
        var a = administration(); var s = statement();
        var transformation = new MedicationTransformations(r);
        var output = transformation.apply(List.of(a, s));
        assertEquals(4, output.size());
        assertSame(a, output.get(0)); assertSame(s, output.get(2));
        assertTrue(output.get(1) instanceof MedicationStatement);
        assertTrue(output.get(3) instanceof MedicationAdministration);
        assertEquals(4, output.stream().map(Resource::getId).distinct().count());
        assertEquals(output.get(1).getId(), new MedicationTransformations(r).apply(List.of(a, s)).get(1).getId());
        assertTrue(transformation.replaced().isEmpty());
        assertEquals(2, transformation.changes().size());
        assertEquals("R", output.get(1).getMeta().getSecurityFirstRep().getCode());
        assertEquals("admin", a.getId());
    }
    @Test public void requestReplacementPrecedesTheSingleEventPass() throws Exception {
        var r = result("MEDICATION_REQUEST_TREATMENT=replace-administration\nMEDICATION_ADMINISTRATION_TREATMENT=replace-statement\n"
                + "MEDICATION_STATEMENT_TREATMENT=replace-administration\n");
        MedicationRequest request = new MedicationRequest(); request.setId("request");
        request.setSubject(new Reference("Patient/p")); request.setMedication(new Reference("Medication/m"));
        request.setAuthoredOnElement(new DateTimeType("2026-05-01"));
        request.setStatus(MedicationRequest.MedicationRequestStatus.ACTIVE);
        request.addDosageInstruction().setText("Planned dose");
        var context = new ConverterResult.InputContext("p", List.of("case"), 7, false);
        r.recordInput(request, context);
        var t = new MedicationTransformations(r);
        var output = t.apply(List.of(request));
        assertEquals(1, output.size());
        var derived = (MedicationStatement)output.get(0);
        assertFalse(derived.hasEffective()); assertFalse(derived.hasStatus()); assertFalse(derived.hasDosage());
        assertSame(context, r.inputContext(derived));
        assertEquals(2, t.changes().size());
        assertTrue(t.changes().get(0).get("notTransferred").contains("authoredOn"));
        assertTrue(t.changes().get(0).get("notTransferred").contains("dosageInstruction"));
        assertTrue(t.changes().get(0).get("missingTargetFacts").contains("effective"));
        assertTrue(t.changes().get(0).get("missingTargetFacts").contains("status"));
        assertEquals(2, t.replaced().size());
        assertTrue(request.hasDosageInstruction());
    }
    @Test public void actualDoseTransfersToStatementButRegimenDoesNotBecomeAnAdministeredDose() throws Exception {
        var r = result("MEDICATION_ADMINISTRATION_TREATMENT=replace-statement\nMEDICATION_STATEMENT_TREATMENT=replace-administration\n");
        var output = new MedicationTransformations(r).apply(List.of(administration(), statement()));
        var s = (MedicationStatement)output.get(0);
        assertEquals("2026-05-02", s.getEffectiveDateTimeType().getValueAsString());
        assertEquals(2, s.getDosageFirstRep().getDoseAndRateFirstRep().getDoseQuantity().getValue().intValue());
        assertEquals(MedicationStatement.MedicationStatementStatus.COMPLETED, s.getStatus());
        var a = (MedicationAdministration)output.get(1);
        assertFalse(a.hasDosage()); assertFalse(a.hasStatus()); assertFalse(a.hasEffective());
    }
    @Test public void unavailableTargetsAndDeselectedSourcesDoNotDeriveResources() throws Exception {
        var r = result("MEDICATION_ADMINISTRATION_TREATMENT=replace-statement\nMEDICATION_STATEMENT_ENABLED=false\n");
        var a = administration();
        assertSame(a, new MedicationTransformations(r).apply(List.of(a)).get(0));
        r = result("MEDICATION_ADMINISTRATION_TREATMENT=add-statement\nMEDICATION_ADMINISTRATION_ENABLED=false\n");
        var t = new MedicationTransformations(r);
        assertEquals(List.of(a), t.apply(List.of(a)));
        assertTrue(t.changes().isEmpty());
    }
    @Test public void requestToStatementRetainsRegimenAndReportsMissingEventFacts() throws Exception {
        var r = result("MEDICATION_REQUEST_TREATMENT=replace-statement\n");
        MedicationRequest request = new MedicationRequest(); request.setId("request");
        request.addDosageInstruction().setText("Prescribed regimen");
        request.setIntent(MedicationRequest.MedicationRequestIntent.ORDER);
        var t = new MedicationTransformations(r);
        MedicationStatement output = (MedicationStatement)t.apply(List.of(request)).get(0);
        assertEquals("Prescribed regimen", output.getDosageFirstRep().getText());
        assertFalse(output.hasEffective()); assertFalse(output.hasStatus());
        assertTrue(t.changes().get(0).get("notTransferred").contains("intent"));
    }
    @Test public void outputSelectionRemovesReferencesToReplacedOriginalResources() throws Exception {
        var r = result("MEDICATION_REQUEST_TREATMENT=replace-statement\n");
        MedicationRequest request = new MedicationRequest(); request.setId("request");
        var a = administration(); a.setRequest(new Reference("MedicationRequest/request"));
        var t = new MedicationTransformations(r);
        var transformed = t.apply(List.of(request, a));
        var p = new ResourceOutputPolicy(r.getConverterOptions().configuration(), List.of(request, a, transformed.get(0)), t.replaced());
        assertFalse(((MedicationAdministration)p.output(a)).hasRequest());
        assertTrue(a.hasRequest());
    }

    @Test public void unknownModifierMeaningPreventsTransformation() throws Exception {
        var r = result("MEDICATION_ADMINISTRATION_TREATMENT=add-statement\n");
        var a = administration();
        a.addModifierExtension().setUrl("urn:example:meaning-change").setValue(new BooleanType(true));
        assertThrows(IllegalArgumentException.class, () -> new MedicationTransformations(r).apply(List.of(a)));
    }
}
