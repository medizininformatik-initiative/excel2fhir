package de.uni_leipzig.life.csv2fhir;

import static org.junit.Assert.*;
import org.hl7.fhir.r4.model.*;
import org.junit.Test;
import de.uni_leipzig.life.csv2fhir.converter.EncounterConverter.*;

public class BundlePostProcessorTest {
    private ConverterOptions options(boolean enabled) throws Exception {
        return ConverterOptions.fromText("ADD_MISSING_DIAGNOSES_FROM_SUPER_ENCOUNTER=" + enabled);
    }

    @Test public void inheritsAcrossTypedAncestorsRegardlessOfEntryOrder() throws Exception {
        var root = new EncounterLevel1(); root.setId("root");
        root.addDiagnosis().setCondition(new Reference("Condition/secondary"))
                .setUse(new CodeableConcept(new Coding("http://terminology.hl7.org/CodeSystem/diagnosis-role", "AD", null)));
        root.addDiagnosis().setCondition(new Reference("Condition/c1"))
                .setUse(new CodeableConcept(new Coding("http://terminology.hl7.org/CodeSystem/diagnosis-role", "CC", null)));
        var department = new EncounterLevel2(); department.setId("department");
        department.setPartOf(new Reference("Encounter/root"));
        var ward = new EncounterLevel3(); ward.setId("ward"); ward.setPartOf(new Reference("Encounter/department"));
        var bundle = new Bundle();
        for (var encounter : new Encounter[] {ward, department, root}) bundle.addEntry().setResource(encounter);
        assertSame(root, BundleFunctions.getResource(bundle, Encounter.class, "Encounter/root"));
        BundlePostProcessor.convert(bundle, options(false));
        assertFalse(ward.hasDiagnosis()); assertFalse(department.hasDiagnosis());
        BundlePostProcessor.convert(bundle, options(true));
        assertEquals("Condition/c1", ward.getDiagnosisFirstRep().getCondition().getReference());
        assertEquals("Condition/c1", department.getDiagnosisFirstRep().getCondition().getReference());
        assertNotSame(root.getDiagnosis().get(1), department.getDiagnosisFirstRep());
        BundlePostProcessor.convert(bundle, options(true));
        assertEquals(1, ward.getDiagnosis().size());
    }

    @Test public void keepsOwnDiagnosesAndHandlesMissingOrCyclicParents() throws Exception {
        var root = new EncounterLevel1(); root.setId("root");
        root.addDiagnosis().setCondition(new Reference("Condition/root"));
        var own = new EncounterLevel2(); own.setId("own"); own.setPartOf(new Reference("Encounter/root"));
        own.addDiagnosis().setCondition(new Reference("Condition/own"));
        var missing = new EncounterLevel3(); missing.setId("missing"); missing.setPartOf(new Reference("Encounter/absent"));
        var cycle = new EncounterLevel2(); cycle.setId("cycle"); cycle.setPartOf(new Reference("Encounter/cycle"));
        var bundle = new Bundle();
        for (var encounter : new Encounter[] {root, own, missing, cycle}) bundle.addEntry().setResource(encounter);
        BundlePostProcessor.convert(bundle, options(true));
        assertEquals("Condition/own", own.getDiagnosisFirstRep().getCondition().getReference());
        assertFalse(missing.hasDiagnosis()); assertFalse(cycle.hasDiagnosis());
    }
}
