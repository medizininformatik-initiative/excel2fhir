package de.uni_leipzig.life.csv2fhir;

import static org.junit.Assert.*;
import java.util.List;
import org.junit.Test;
import org.hl7.fhir.r4.model.*;

public class AdditionalIdentifiersTest {
    private static final String ID = "a152e771-3d5a-4cb1-9866-35fa6d91fd83";
    private String settings(String pattern) {
        return "CONFIGURATION_VERSION=1\nIDENTIFIER_RULE_1_ID=" + ID
                + "\nIDENTIFIER_RULE_1_ENABLED=true\nIDENTIFIER_RULE_1_RESOURCES=Patient,Observation,MedicationAdministration,MedicationStatement\n"
                + "IDENTIFIER_RULE_1_SYSTEM=urn:extra\nIDENTIFIER_RULE_1_PATTERN=" + pattern + "\n";
    }
    private ConverterResult context(String settings, Resource... resources) throws Exception {
        ConverterResult result = new ConverterResult(ConverterOptions.fromText(settings));
        for (Resource resource : resources) result.recordInput(resource, new ConverterResult.InputContext("effective-patient-2", List.of(), 1, false));
        return result;
    }
    @Test public void selectsContactLevelsAndObservationCategories() throws Exception {
        Encounter facility = new Encounter(); facility.setId("facility"); facility.setClass_(new Coding().setCode("IMP"));
        Encounter department = new Encounter(); department.setId("department"); department.setClass_(new Coding().setCode("IMP"));
        Encounter ward = new Encounter(); ward.setId("ward"); ward.setClass_(new Coding().setCode("IMP"));
        Encounter ambulatory = new Encounter(); ambulatory.setId("ambulatory"); ambulatory.setClass_(new Coding().setCode("AMB"));
        Observation lab = new Observation(); lab.setId("lab");
        lab.addCategory().addCoding().setSystem("http://terminology.hl7.org/CodeSystem/observation-category").setCode("laboratory");
        Observation vital = new Observation(); vital.setId("vital");
        vital.addCategory().addCoding().setSystem("http://terminology.hl7.org/CodeSystem/observation-category").setCode("vital-signs");
        List<Resource> resources = List.of(facility, department, ward, ambulatory, lab, vital);
        var expected = java.util.Map.of("Encounter.inpatient.facility", List.of("facility"),
                "Encounter.inpatient.department", List.of("department"),
                "Encounter.inpatient.ward-service", List.of("ward"),
                "Encounter.inpatient", List.of("facility", "department", "ward"),
                "Encounter.ambulatory", List.of("ambulatory"),
                "Encounter", List.of("facility", "department", "ward", "ambulatory"),
                "Observation.laboratory", List.of("lab"), "Observation.vitalSigns", List.of("vital"),
                "Observation", List.of("lab", "vital"),
                "Encounter.inpatient,Encounter.inpatient.department", List.of("facility", "department", "ward"));
        for (var selection : expected.entrySet()) {
            String config = settings("{resourceType}-{count}").replace("Patient,Observation,MedicationAdministration,MedicationStatement", selection.getKey());
            ConverterResult context = context(config, resources.toArray(Resource[]::new));
            context.contacts().add(facility, "p", "facility", ContactIndex.Level.FACILITY, null, 1, false);
            context.contacts().add(department, "p", "facility", ContactIndex.Level.DEPARTMENT, "facility", 2, false);
            context.contacts().add(ward, "p", "facility", ContactIndex.Level.WARD_SERVICE, "department", 3, false);
            context.contacts().add(ambulatory, "p", "ambulatory", ContactIndex.Level.FACILITY, null, 4, false);
            AdditionalIdentifiers ids = new AdditionalIdentifiers(context.getConverterOptions().configuration());
            ids.reserve(resources, context, 0);
            for (Resource resource : resources) {
                Resource output = ids.output(resource.copy(), 0);
                List<Identifier> identifiers = output instanceof Encounter ? ((Encounter)output).getIdentifier() : ((Observation)output).getIdentifier();
                assertEquals(selection.getKey() + ": " + resource.getId(), selection.getValue().contains(resource.getId()) ? 1 : 0, identifiers.size());
            }
        }
    }
    @Test public void preservesFreelyChosenTypesAcrossResourceKinds() throws Exception {
        String settings = settings("{count}") + "IDENTIFIER_RULE_1_USE=secondary\n"
                + "IDENTIFIER_RULE_1_TYPE_TEXT=Test type\n"
                + "IDENTIFIER_RULE_1_TYPE_CODINGS=[{\"system\":\"http://terminology.hl7.org/CodeSystem/v2-0203\",\"code\":\"VN\"},{\"system\":\"urn:custom\",\"code\":\"deliberately-wrong\",\"display\":\"Example\"}]\n";
        Patient p = new Patient(); p.setId("p"); Observation o = new Observation(); o.setId("o");
        var context = context(settings, p, o);
        var ids = new AdditionalIdentifiers(context.getConverterOptions().configuration());
        ids.reserve(List.of(p, o), context, 0);
        for (Resource resource : List.of(p, o)) {
            Resource out = ids.output(resource, 0);
            Identifier id = out instanceof Patient ? ((Patient)out).getIdentifierFirstRep() : ((Observation)out).getIdentifierFirstRep();
            assertEquals(Identifier.IdentifierUse.SECONDARY, id.getUse());
            assertEquals("Test type", id.getType().getText());
            assertEquals(2, id.getType().getCoding().size());
            assertEquals("VN", id.getType().getCodingFirstRep().getCode());
            assertEquals("deliberately-wrong", id.getType().getCoding().get(1).getCode());
        }
    }
    @Test public void countsAcrossTypesAndIterationsAndCountsRepeatedSerializationOnce() throws Exception {
        String settings = settings("{count:03}-{patientId}-{resourceType}-{iteration}");
        Patient p = new Patient(); p.setId("p"); p.addIdentifier().setSystem("urn:original").setValue("original");
        Observation o = new Observation(); o.setId("o");
        ConverterResult context = context(settings,p,o);
        AdditionalIdentifiers ids = new AdditionalIdentifiers(context.getConverterOptions().configuration());
        ids.reserve(List.of(p,o,p), context,0);
        Patient out = (Patient)ids.output(p,0);
        assertEquals("001-effective-patient-2-Patient-0",out.getIdentifier().get(1).getValue());
        assertEquals(out.getIdentifier().get(1).getValue(),((Patient)ids.output(p,0)).getIdentifier().get(1).getValue());
        assertEquals("002-effective-patient-2-Observation-0",((Observation)ids.output(o,0)).getIdentifierFirstRep().getValue());
        ids.reserve(List.of(o),context,1);
        assertEquals("003-effective-patient-2-Observation-1",((Observation)ids.output(o,1)).getIdentifierFirstRep().getValue());
        assertEquals(1,p.getIdentifier().size());
    }
    @Test public void detectsGeneratedCollisionsAcrossResourcesAndRepetitions() throws Exception {
        Patient p = new Patient(); p.setId("p"); Observation o = new Observation(); o.setId("o");
        ConverterResult context = context(settings("constant"),p,o);
        AdditionalIdentifiers ids = new AdditionalIdentifiers(context.getConverterOptions().configuration());
        ids.reserve(List.of(p,o),context,0); ids.output(p,0); ids.output(p,0);
        var exception = assertThrows(IllegalArgumentException.class, () -> ids.output(o,0));
        assertTrue(exception.getMessage().contains(ID)); assertTrue(exception.getMessage().contains("Patient/p")); assertTrue(exception.getMessage().contains("Observation/o"));
        AdditionalIdentifiers repeated = new AdditionalIdentifiers(context.getConverterOptions().configuration());
        repeated.reserve(List.of(p),context,0); repeated.output(p,0); repeated.reserve(List.of(p),context,1);
        assertThrows(IllegalArgumentException.class, () -> repeated.output(p,1));
    }
    @Test public void detectsCollisionWithExistingIdentifierInEitherOrder() throws Exception {
        Patient p = new Patient(); p.setId("p"); Observation o = new Observation(); o.setId("o");
        o.addIdentifier().setSystem("urn:extra").setValue("p");
        for (boolean existingFirst : List.of(false,true)) {
            ConverterResult context = context(settings("{resourceId}"),p,o);
            AdditionalIdentifiers ids = new AdditionalIdentifiers(context.getConverterOptions().configuration());
            ids.reserve(List.of(p,o),context,0);
            ids.output(existingFirst ? o : p,0);
            assertThrows(IllegalArgumentException.class, () -> ids.output(existingFirst ? p : o,0));
        }
    }
    @Test public void escapingPaddingAndHashFollowContract() {
        Patient p = new Patient(); p.setId("p-ä");
        assertEquals("3dd0c419695b2ff78480f92b89aac4a0",AdditionalIdentifiers.hash(ID.toUpperCase(),p,2));
        assertEquals("{literal}-123-p-ä-Patient-2", AdditionalIdentifiers.expand("{{literal}}-{count:02}-{resourceId}-{resourceType}-{iteration}",123,"",p,2,ID));
        assertEquals("{patientId}",AdditionalIdentifiers.expand("{{patientId}}",1,"",p,0,ID));
        assertThrows(IllegalArgumentException.class, () -> AdditionalIdentifiers.expand("{count:01000001}",1,"",p,0,ID));
    }
    @Test public void potentialDerivationsKeepCountersStableWhenTargetIsDisabled() throws Exception {
        MedicationAdministration a = new MedicationAdministration(); a.setId("a");
        String settings = settings("{count}") + "MEDICATION_ADMINISTRATION_TREATMENT=add-statement\n";
        for (boolean enabled : List.of(true,false)) {
            ConverterResult context = context(settings + "MEDICATION_STATEMENT_ENABLED=" + enabled + "\n",a);
            MedicationTransformations transformations = new MedicationTransformations(context);
            List<Resource> potential = transformations.potentialResources(List.of(a));
            assertEquals(2,potential.size());
            AdditionalIdentifiers ids = new AdditionalIdentifiers(context.getConverterOptions().configuration());
            ids.reserve(potential,context,0);
            for (Resource resource : transformations.apply(List.of(a))) ids.output(resource,0);
            Patient p = new Patient(); p.setId("p"); ids.reserve(List.of(p),context,0);
            assertEquals("3",((Patient)ids.output(p,0)).getIdentifierFirstRep().getValue());
        }
    }
    @Test public void encounterSelectorsShareCountsAndTechnicalTypeWithoutDuplicateAllocation() throws Exception {
        String settings = settings("{count}-{resourceType}").replace(
                "Patient,Observation,MedicationAdministration,MedicationStatement", "Encounter,Encounter.ambulatory,Encounter.inpatient");
        var amb = new Encounter(); amb.setId("amb"); amb.getClass_().setCode("AMB");
        var imp = new Encounter(); imp.setId("imp"); imp.getClass_().setCode("IMP");
        var context = context(settings, amb, imp);
        var ids = new AdditionalIdentifiers(context.getConverterOptions().configuration());
        ids.reserve(List.of(amb, imp), context, 0);
        assertEquals("2-Encounter", ((Encounter)ids.output(imp, 0)).getIdentifierFirstRep().getValue());
        assertEquals("1-Encounter", ((Encounter)ids.output(amb, 0)).getIdentifierFirstRep().getValue());
        var scoped = context(settings.replace("Encounter,Encounter.ambulatory,Encounter.inpatient", "Encounter.inpatient"), amb, imp);
        ids = new AdditionalIdentifiers(scoped.getConverterOptions().configuration());
        ids.reserve(List.of(amb, imp), scoped, 0);
        assertFalse(((Encounter)ids.output(amb, 0)).hasIdentifier());
        assertEquals("1-Encounter", ((Encounter)ids.output(imp, 0)).getIdentifierFirstRep().getValue());
    }
}
