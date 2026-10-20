package de.uni_leipzig.life.synthifhir;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;
import org.hl7.fhir.r4.model.*;
import de.uni_leipzig.life.synthifhir.converter.ResourceIdSuffix;

public class CaseIdentifiersTest {
    private String config(String pattern) {
        return "CONFIGURATION_VERSION=1\nIDENTIFIER_RULE_1_ID=a152e771-3d5a-4cb1-9866-35fa6d91fd83\n"
                + "IDENTIFIER_RULE_1_ENABLED=true\nIDENTIFIER_RULE_1_SCOPE=case\n"
                + "IDENTIFIER_RULE_1_RESOURCES=Encounter,Observation,Patient\n"
                + "IDENTIFIER_RULE_1_SYSTEM=urn:case\nIDENTIFIER_RULE_1_PATTERN=" + pattern + "\n";
    }
    private List<Resource> add(ConverterResult context, String patient, String number) {
        String facilityId = patient + ResourceIdSuffix.ENCOUNTER_LEVEL_1 + number;
        Encounter facility = new Encounter(); facility.setId(facilityId);
        Encounter department = new Encounter(); department.setId(facilityId + "-dep");
        for (Encounter e : List.of(facility, department)) {
            e.setClass_(new Coding().setCode("IMP"));
            e.setSubject(new Reference("Patient/" + patient));
            context.contacts().add(e, patient, facilityId, e == facility ? ContactIndex.Level.FACILITY : ContactIndex.Level.DEPARTMENT,
                    e == facility ? null : facilityId, 1, false);
        }
        Observation observation = new Observation(); observation.setId(facilityId + "-obs");
        observation.setSubject(new Reference("Patient/" + patient));
        observation.setEncounter(new Reference("Encounter/" + department.getId()));
        List<Resource> resources = List.of(facility, department, observation);
        for (Resource resource : resources) context.recordInput(resource, new ConverterResult.InputContext(patient, List.of(facilityId), 1, false));
        return resources;
    }
    private String value(Resource resource) {
        for (Property property : resource.children()) if (property.getName().equals("identifier"))
            for (Base base : property.getValues()) {
                Identifier id = (Identifier)base;
                if ("urn:case".equals(id.getSystem())) return id.getValue();
            }
        return null;
    }
    @Test public void sharesWithinHierarchyAndSeparatesCasesPatientsAndRepetitions() {
        var options = ConverterOptions.fromText(config("{patientId}-{caseId}"));
        var ids = new AdditionalIdentifiers(options.configuration());
        Set<String> caseValues = new HashSet<>();
        for (String patient : List.of("site-p1", "site-p2", "site-p1-rep-1")) {
            var context = new ConverterResult(options);
            for (String number : List.of("17", "18")) {
                var resources = add(context, patient, number);
                ids.reserve(resources, context, 0);
                for (Resource resource : resources) {
                    ids.captureCase(resource, context, 0);
                    assertEquals(patient + "-" + number, value(ids.output(resource, 0)));
                }
                assertTrue(caseValues.add(patient + "-" + number));
            }
        }
        assertEquals(6, caseValues.size());
    }
    @Test public void respectsSelectedEncounterLevel() {
        var options = ConverterOptions.fromText(config("{patientId}-{caseId}")
                .replace("RESOURCES=Encounter,Observation,Patient", "RESOURCES=Encounter.inpatient.department"));
        var context = new ConverterResult(options);
        var resources = add(context, "p", "17");
        var ids = new AdditionalIdentifiers(options.configuration());
        ids.reserve(resources, context, 0);
        for (int i = 0; i < resources.size(); i++) {
            Resource resource = resources.get(i);
            ids.captureCase(resource, context, 0);
            assertEquals(i == 1 ? "p-17" : null, value(ids.output(resource, 0)));
        }
    }
    @Test public void missingAndAmbiguousCasesProduceNotesAndNoIdentifier() {
        var options = ConverterOptions.fromText(config("{patientId}-{caseId}"));
        var context = new ConverterResult(options);
        var ids = new AdditionalIdentifiers(options.configuration());
        add(context, "p", "17"); add(context, "p", "18");
        Patient patient = new Patient(); patient.setId("p");
        Observation observation = new Observation(); observation.setId("ambiguous");
        context.recordInput(observation, new ConverterResult.InputContext("p",
                List.of("p" + ResourceIdSuffix.ENCOUNTER_LEVEL_1 + "17", "p" + ResourceIdSuffix.ENCOUNTER_LEVEL_1 + "18"), 1, false));
        var notes = new ArrayList<Map<String, String>>();
        ids.reserve(List.of(patient, observation), context, 0);
        for (Resource resource : List.of(patient, observation)) {
            ids.captureCase(resource, context, 0);
            assertNull(value(ids.output(resource, 0, notes)));
        }
        assertEquals(2, notes.size());
        assertEquals("missing-or-ambiguous-case", notes.get(0).get("reason"));
    }
    @Test public void rejectsCrossCaseCollisionsAndResourceDependentPatterns() {
        var options = ConverterOptions.fromText(config("{caseId}"));
        var ids = new AdditionalIdentifiers(options.configuration());
        for (String patient : List.of("p1", "p2")) {
            var context = new ConverterResult(options);
            var resources = add(context, patient, "17");
            ids.reserve(resources, context, 0);
            Resource resource = resources.get(0);
            ids.captureCase(resource, context, 0);
            if (patient.equals("p1")) ids.output(resource, 0);
            else assertThrows(IllegalArgumentException.class, () -> ids.output(resource, 0));
        }
        for (String token : List.of("{count}", "{resourceId}", "{resourceType}", "{hash}", "{iteration}"))
            assertThrows(IllegalArgumentException.class, () -> ContractConfiguration.parse(config(token)));
        assertThrows(IllegalArgumentException.class, () -> ContractConfiguration.parse(config("{caseId}").replace("SCOPE=case", "SCOPE=resource")));
    }
}
