package de.uni_leipzig.imise.validate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.hl7.fhir.common.hapi.validation.support.NpmPackageValidationSupport;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.Test;

import ca.uhn.fhir.context.FhirContext;

public class FhirPackageLoaderTest {

    @Test
    public void loadsCurrentProfilesAndVersionedDependencies() {
        NpmPackageValidationSupport support = FhirPackageLoader.load(FhirContext.forR4());
        StructureDefinition encounter = (StructureDefinition) support.fetchStructureDefinition(
                "https://www.medizininformatik-initiative.de/fhir/core/modul-fall/StructureDefinition/KontaktGesundheitseinrichtung");
        assertNotNull(encounter);
        assertEquals("2026.0.1", encounter.getVersion());
        ValueSet classes = (ValueSet) support.fetchValueSet("http://fhir.de/ValueSet/EncounterClassDE");
        assertEquals("1.5.4", classes.getVersion());
        assertNotNull(support.fetchValueSet("http://fhir.de/ValueSet/EncounterClassDE|1.5.4"));
        assertNotNull(support.fetchStructureDefinition(
                "http://hl7.org/fhir/5.0/StructureDefinition/extension-Encounter.plannedStartDate"));
        CodeSystem status = (CodeSystem) support.fetchCodeSystem("http://hl7.org/fhir/encounter-status");
        assertEquals("4.0.1", status.getVersion());
        assertTrue(status.getConcept().stream().anyMatch(c -> "finished".equals(c.getCode())));
        assertNotNull(support.fetchCodeSystem("http://hl7.org/fhir/encounter-status|5.0.0"));
    }
}
