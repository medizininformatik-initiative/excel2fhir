package de.uni_leipzig.imise.validate;

import static org.junit.Assert.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.hl7.fhir.r4.model.*;
import de.uni_leipzig.imise.Excel2FhirMain;
import de.uni_leipzig.life.csv2fhir.OutputFileType;
import de.uni_leipzig.life.csv2fhir.DarOverrides;
import picocli.CommandLine;

/** Real workbook -> converter -> serialized output, including interacting policies. */
public class SelectionDarWorkflowTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private Map<String, Resource> convert(String settings) throws Exception {
        Path root = temp.newFolder().toPath(), config = root.resolve("audit.config");
        Files.writeString(config, "CONFIGURATION_VERSION=1\nOUTPUT_FORMATS=JSON,NDJSON\n" + settings);
        Path output = root.resolve("output");
        assertEquals(0, new CommandLine(new Excel2FhirMain()).execute("-f",
                "input/FHIR_Testdatengenerator_Vorlage.xlsx", "-o", output.toString(),
                "--converter-options", config.toString()));
        Map<String, Resource> json = read(output, ".json"), ndjson = read(output, ".ndjson");
        assertEquals(json.keySet(), ndjson.keySet());
        for (String key : json.keySet()) assertTrue("Format mismatch: " + key, json.get(key).equalsDeep(ndjson.get(key)));
        return json;
    }

    private Map<String, Resource> read(Path root, String extension) throws Exception {
        Map<String, Resource> result = new LinkedHashMap<>();
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().contains("/fhir/") && p.toString().endsWith(extension)).toList()) {
                List<String> values = extension.equals(".ndjson") ? Files.readAllLines(file) : List.of(Files.readString(file));
                for (String value : values) {
                    Resource parsed = (Resource)OutputFileType.JSON.getParser().parseResource(value);
                    List<Resource> resources = parsed instanceof Bundle
                            ? ((Bundle)parsed).getEntry().stream().map(Bundle.BundleEntryComponent::getResource).toList() : List.of(parsed);
                    for (Resource resource : resources) {
                        String key = resource.fhirType() + "/" + resource.getIdElement().getIdPart();
                        Resource previous = result.put(key, resource);
                        if (previous != null) assertTrue("Conflicting resource " + key, previous.equalsDeep(resource));
                    }
                }
            }
        }
        assertFalse(result.isEmpty());
        return result;
    }
    private List<Reference> references(Base base) {
        List<Reference> result = new ArrayList<>();
        if (base instanceof Reference) result.add((Reference)base);
        else if (!(base instanceof PrimitiveType<?>)) for (Property property : base.children())
            for (Base child : property.getValues()) result.addAll(references(child));
        return result;
    }
    private Set<String> ids(Map<String, Resource> resources, String type) {
        return resources.keySet().stream().filter(key -> key.startsWith(type + "/")).collect(Collectors.toSet());
    }
    private String reason(Element element) {
        var extension = element.getExtensionByUrl(DarOverrides.URL);
        return extension == null ? null : ((CodeType)extension.getValue()).getValue();
    }
    @Test public void patientMedicationAndLocationModesPreserveIdsAndReferenceIntentWithDar() throws Exception {
        Map<String, Resource> baseline = convert("");
        for (String type : List.of("Patient", "Medication", "Location"))
            assertFalse("Fixture must exercise " + type, ids(baseline, type).isEmpty());
        for (Resource resource : baseline.values()) for (Reference ref : references(resource)) {
            if (!ref.hasReference()) continue;
            String target = ref.getReference();
            if (target.startsWith("Patient/") || target.startsWith("Medication/") || target.startsWith("Location/"))
                assertTrue("Generated target must exist: " + target, baseline.containsKey(target));
        }
        for (String mode : List.of("reference-only", "neither")) {
            var output = convert("PATIENT_MODE=" + mode + "\nMEDICATION_MODE=" + mode + "\nLOCATION_MODE=" + mode
                    + "\nMEDICATION_EXISTING_REFERENCE=true\nLOCATION_EXISTING_REFERENCE=true\n"
                    + "DAR_PATIENT_NAME_FAMILY=made-up\nDAR_ENCOUNTER_PERIOD_END=custom-end\n");
            for (String type : List.of("Patient", "Medication", "Location")) assertTrue(ids(output, type).isEmpty());
            Set<String> expected = new HashSet<>(baseline.keySet());
            expected.removeIf(key -> key.startsWith("Patient/") || key.startsWith("Medication/") || key.startsWith("Location/"));
            assertEquals(expected, output.keySet());
            int externalTargets = 0;
            for (Resource resource : output.values()) {
                for (Reference ref : references(resource)) {
                    if (!ref.hasReference() || ref.getReference().startsWith("#")) continue;
                    String target = ref.getReference();
                    if (target.startsWith("Patient/") || target.startsWith("Medication/") || target.startsWith("Location/")) {
                        assertEquals("neither must remove references: " + target, "reference-only", mode);
                        assertTrue(baseline.containsKey(target)); externalTargets++;
                    }
                }
                if (resource instanceof Encounter) assertEquals("custom-end", reason(((Encounter)resource).getPeriod().getEndElement()));
            }
            if (mode.equals("reference-only")) assertTrue(externalTargets > 0);
        }
    }
    @Test public void deselectedResourcesAreNotRevivedByDarAndTheirLocalReferencesAreRemoved() throws Exception {
        var baseline = convert("");
        List<String> types = List.of("Condition", "Procedure", "Observation", "MedicationRequest", "MedicationAdministration",
                "MedicationStatement", "Immunization", "DiagnosticReport", "CarePlan", "DocumentReference", "Consent");
        String transformations = "MEDICATION_REQUEST_TREATMENT=replace-administration\nMEDICATION_ADMINISTRATION_TREATMENT=add-statement\n";
        var derivedBaseline = convert(transformations);
        var output = convert(transformations + "CONDITION_ENABLED=false\nPROCEDURE_ENABLED=false\nOBSERVATION_LABORATORY_ENABLED=false\n"
                + "OBSERVATION_VITAL_SIGNS_ENABLED=false\nMEDICATION_REQUEST_ENABLED=false\n"
                + "MEDICATION_ADMINISTRATION_ENABLED=false\nMEDICATION_STATEMENT_ENABLED=false\n"
                + "IMMUNIZATION_ENABLED=false\nDIAGNOSTIC_REPORT_ENABLED=false\nCARE_PLAN_ENABLED=false\n"
                + "DOCUMENT_REFERENCE_ENABLED=false\nCONSENT_ENABLED=false\n"
                + "DAR_LABORATORY_VALUE_X_NUMERIC_MEASUREMENT=made-up\nDAR_PATIENT_NAME_FAMILY=masked\n");
        for (String type : types) {
            if (List.of("Condition", "Procedure", "Observation", "MedicationRequest", "MedicationAdministration", "MedicationStatement").contains(type))
                assertTrue("Fixture must exercise " + type, !ids(baseline, type).isEmpty() || !ids(derivedBaseline, type).isEmpty());
            assertTrue(type, ids(output, type).isEmpty());
        }
        for (Resource resource : output.values()) for (Reference ref : references(resource))
            if (ref.hasReference() && baseline.containsKey(ref.getReference()))
                assertTrue("Dangling local reference " + ref.getReference(), output.containsKey(ref.getReference()));
        for (Resource resource : output.values()) if (resource instanceof Patient)
            assertEquals("masked", reason(((Patient)resource).getNameFirstRep().getFamilyElement()));
    }
    @Test public void customDarPreservesCodingSystemsAndDoesNotInventDerivedDosages() throws Exception {
        String transformations = "MEDICATION_REQUEST_TREATMENT=replace-administration\n";
        var baseline = convert(transformations);
        var output = convert(transformations + "DAR_MEDICATION_ADMINISTRATION_DOSAGE_DOSE=custom-dose\n"
                + "DAR_CONDITION_CODE_CODING_CODE=custom-code\n");
        assertEquals(baseline.keySet(), output.keySet());
        int administrations = 0, codings = 0;
        for (String key : output.keySet()) {
            Resource resource = output.get(key);
            if (resource instanceof MedicationAdministration) {
                var administration = (MedicationAdministration)resource;
                administrations++;
                if (((MedicationAdministration)baseline.get(key)).hasDosage()) {
                    var dose = administration.getDosage().getDose();
                    assertFalse(dose.hasValue()); assertFalse(dose.hasUnit());
                    assertEquals("custom-dose", reason(dose));
                } else {
                    assertFalse("DAR must not invent absent dosage instructions", administration.hasDosage());
                }
            }
            if (resource instanceof Condition) {
                var original = ((Condition)baseline.get(key)).getCode().getCoding();
                var replaced = ((Condition)resource).getCode().getCoding();
                assertEquals(original.size(), replaced.size());
                for (int i = 0; i < original.size(); i++) {
                    assertEquals(original.get(i).getSystem(), replaced.get(i).getSystem());
                    assertFalse(replaced.get(i).getCodeElement().hasValue());
                    assertEquals("custom-code", reason(replaced.get(i).getCodeElement())); codings++;
                }
            }
        }
        assertTrue(administrations > 0); assertTrue(codings > 0);
    }
    @Test public void missingOnlyPreservesValuesAndScopedDarOverridesCommonRuleAfterEndPolicy() throws Exception {
        var baseline = convert("");
        var missing = convert("DAR_PATIENT_NAME_FAMILY=custom-family\nDAR_PATIENT_NAME_FAMILY_ONLY_WHEN_MISSING=true\n"
                + "ENCOUNTER_INPATIENT_END_POLICY=start-plus-second\nDAR_ENCOUNTER_PERIOD_END=common-end\n"
                + "DAR_ENCOUNTER_INPATIENT_PERIOD_END=scoped-end\n");
        var overwrite = convert("DAR_PATIENT_NAME_FAMILY=custom-family\n");
        int namedPatients = 0, inpatient = 0, other = 0;
        for (String key : baseline.keySet()) {
            Resource original = baseline.get(key);
            if (original instanceof Patient) {
                Patient patient = (Patient)original;
                if (patient.getNameFirstRep().hasFamily()) {
                    namedPatients++;
                    assertTrue(patient.getNameFirstRep().getFamilyElement().equalsDeep(((Patient)missing.get(key)).getNameFirstRep().getFamilyElement()));
                }
                var replaced = ((Patient)overwrite.get(key)).getNameFirstRep().getFamilyElement();
                assertFalse(replaced.hasValue()); assertEquals("custom-family", reason(replaced));
            } else if (original instanceof Encounter) {
                Encounter encounter = (Encounter)missing.get(key);
                boolean imp = "IMP".equals(encounter.getClass_().getCode());
                if (imp) inpatient++; else other++;
                assertFalse(encounter.getPeriod().getEndElement().hasValue());
                assertEquals(imp ? "scoped-end" : "common-end", reason(encounter.getPeriod().getEndElement()));
            }
        }
        assertTrue(namedPatients > 0); assertTrue(inpatient > 0); assertTrue(other > 0);
    }
}
