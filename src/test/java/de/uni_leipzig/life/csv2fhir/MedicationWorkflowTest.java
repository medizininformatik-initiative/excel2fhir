package de.uni_leipzig.life.csv2fhir;

import static org.junit.Assert.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.csv.*;
import org.hl7.fhir.r4.model.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

/** Medication conversion, transformation and output policies through serialized exports. */
public class MedicationWorkflowTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private Path input;
    private ImportReport report;

    @Before public void setup() throws Exception {
        input = temp.newFolder().toPath();
        table(TableIdentifier.Person, List.of(Map.of("Patient-ID", "p1", "Vorname", "Test", "Nachname", "Person",
                "Geburtsdatum", "2000-01-01", "Geschlecht", "weiblich")));
        table(TableIdentifier.Fall, List.of(Map.of("Patient-ID", "p1", "Fall-Nr", "1", "Start", "2026-01-01",
                "Ende", "2026-01-10", "Einrichtungskontaktklasse", "stationaer")));
        List<Map<String, String>> rows = new ArrayList<>();
        for (String type : List.of("Verordnung", "Verabreichung", "Medikationsaussage")) {
            var row = new LinkedHashMap<>(Map.of("Patient-ID", "p1", "Fall-Nr", "1", "Medikationstyp", type,
                    "Präparatcode", "00000000", "Präparatcodesystem", "PZN", "Einzeldosis", "2", "Dosiereinheit", "mg"));
            if (!type.equals("Verabreichung")) {
                row.put("Dosen pro Tag", "3"); row.put("Dokumentationszeitpunkt", "2026-01-04");
            }
            if (!type.equals("Verordnung")) {
                row.put("Beginn", "2026-01-02"); row.put("Ende", "2026-01-03"); row.put("Status", "completed");
            }
            rows.add(row);
        }
        table(TableIdentifier.Medikation, rows);
    }
    private void table(TableIdentifier type, List<Map<String, String>> rows) throws Exception {
        Set<String> headers = new LinkedHashSet<>(type.getMandatoryColumnNames());
        rows.forEach(row -> headers.addAll(row.keySet()));
        try (var writer = Files.newBufferedWriter(input.resolve("case_" + type + ".csv"));
                var csv = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
            csv.printRecord(headers);
            for (var row : rows) csv.printRecord(headers.stream().map(h -> row.getOrDefault(h, "")).toList());
        }
    }
    private Map<String, Resource> run(String settings) throws Exception {
        Path output = temp.newFolder().toPath();
        var converter = new Csv2Fhir(input.toFile(), output.toFile(), "case_", null,
                ConverterOptions.fromText("CONFIGURATION_VERSION=1\nOUTPUT_PATIENTS_PER_FILE=1000\n"
                        + "REFERENCE_MEDICATION_REQUEST_ENCOUNTER=facility\nREFERENCE_MEDICATION_ADMINISTRATION_ENCOUNTER=facility\n"
                        + "REFERENCE_MEDICATION_STATEMENT_ENCOUNTER=facility\n" + settings));
        converter.convertFiles(Integer.MAX_VALUE, OutputFileType.JSON, OutputFileType.NDJSON);
        report = converter.getImportReport();
        assertFalse(report.issues.toString(), report.hasErrors());
        var json = read(output, ".json"); var ndjson = read(output, ".ndjson");
        assertFalse(json.isEmpty()); assertEquals(json.keySet(), ndjson.keySet());
        for (String id : json.keySet()) assertTrue("Format mismatch: " + id, json.get(id).equalsDeep(ndjson.get(id)));
        return json;
    }
    private Map<String, Resource> read(Path output, String extension) throws Exception {
        Map<String, Resource> resources = new LinkedHashMap<>();
        try (var files = Files.walk(output)) {
            for (Path file : files.filter(p -> p.toString().endsWith(extension) && !p.toString().endsWith(".import.json")).toList()) {
                for (String text : extension.equals(".ndjson") ? Files.readAllLines(file) : List.of(Files.readString(file))) {
                    Resource parsed = (Resource)OutputFileType.JSON.getParser().parseResource(text);
                    var entries = parsed instanceof Bundle ? ((Bundle)parsed).getEntry().stream()
                            .map(Bundle.BundleEntryComponent::getResource).toList() : List.of(parsed);
                    for (Resource resource : entries) {
                        String key = resource.fhirType() + "/" + resource.getIdElement().getIdPart();
                        assertNull("Duplicate resource " + key, resources.put(key, resource));
                    }
                }
            }
        }
        return resources;
    }
    private <T extends Resource> T single(Map<String, Resource> resources, Class<T> type) {
        var matches = resources.values().stream().filter(type::isInstance).map(type::cast).toList();
        assertEquals(1, matches.size()); return matches.get(0);
    }
    private Map<String, String> change(String sourceType) {
        return report.medicationTransformations.stream().filter(c -> c.get("source").startsWith(sourceType + "/")).findFirst().orElseThrow();
    }
    private void localReferences(Base base, Map<String, Resource> resources) {
        if (base instanceof Reference && ((Reference)base).hasReference()) {
            String ref = ((Reference)base).getReference();
            assertTrue("Dangling reference: " + ref, resources.containsKey(ref));
        } else if (!(base instanceof PrimitiveType<?>)) for (Property property : base.children())
            for (Base child : property.getValues()) localReferences(child, resources);
    }
    @Test public void bidirectionalAdditionPreservesActualDoseAndRegimenMeaningWithoutRecursion() throws Exception {
        var baseline = run("");
        var originalAdministration = single(baseline, MedicationAdministration.class);
        var originalStatement = single(baseline, MedicationStatement.class);
        String settings = "MEDICATION_ADMINISTRATION_TREATMENT=add-statement\nMEDICATION_STATEMENT_TREATMENT=add-administration\n";
        var output = run(settings);
        assertEquals(baseline.size() + 2, output.size()); assertEquals(2, report.medicationTransformations.size());
        for (String key : baseline.keySet()) assertTrue(baseline.get(key).equalsDeep(output.get(key)));
        var statement = (MedicationStatement)output.get(change("MedicationAdministration").get("target"));
        assertTrue(originalAdministration.getEffective().equalsDeep(statement.getEffective()));
        assertTrue(originalAdministration.getDosage().getDose().equalsDeep(statement.getDosageFirstRep().getDoseAndRateFirstRep().getDose()));
        assertFalse(statement.getDosageFirstRep().hasTiming());
        assertEquals("completed", statement.getStatusElement().getValueAsString());
        var administration = (MedicationAdministration)output.get(change("MedicationStatement").get("target"));
        assertTrue(originalStatement.getEffective().equalsDeep(administration.getEffective()));
        assertFalse(administration.hasDosage());
        assertEquals("completed", administration.getStatusElement().getValueAsString());
        assertTrue(change("MedicationStatement").get("notTransferred").contains("dosage"));
        assertTrue(change("MedicationStatement").get("notTransferred").contains("dateAsserted"));
        for (Resource resource : output.values()) localReferences(resource, output);
        var repeat = run(settings);
        assertEquals(output.keySet(), repeat.keySet());
        for (String key : output.keySet()) assertTrue(output.get(key).equalsDeep(repeat.get(key)));
    }
    @Test public void requestReplacementPreservesOnlyApplicableFactsAndReportsOmissions() throws Exception {
        var baseline = run(""); var request = single(baseline, MedicationRequest.class);
        for (String target : List.of("statement", "administration")) {
            var output = run("MEDICATION_REQUEST_TREATMENT=replace-" + target + "\n");
            assertFalse(output.values().stream().anyMatch(MedicationRequest.class::isInstance));
            assertEquals(1, report.medicationTransformations.size());
            var change = change("MedicationRequest");
            assertFalse(output.containsKey(change.get("source")));
            Resource derived = output.get(change.get("target"));
            if (derived instanceof MedicationStatement) {
                var s = (MedicationStatement)derived;
                assertTrue(request.getDosageInstructionFirstRep().equalsDeep(s.getDosageFirstRep()));
                assertFalse(s.hasStatus()); assertFalse(s.hasEffective()); assertFalse(s.hasContext());
            } else {
                var a = (MedicationAdministration)derived;
                assertFalse(a.hasDosage()); assertFalse(a.hasStatus()); assertFalse(a.hasEffective()); assertFalse(a.hasContext());
                assertTrue(change.get("notTransferred").contains("dosageInstruction"));
            }
            assertTrue(change.get("notTransferred").contains("authoredOn"));
            assertTrue(change.get("missingTargetFacts").contains("effective"));
            assertTrue(change.get("missingTargetFacts").contains("status"));
            assertTrue(report.encounterReferenceIssues.stream().anyMatch(i -> "missing-timestamp".equals(i.get("reason"))));
            for (Resource resource : output.values()) localReferences(resource, output);
        }
    }
    @Test public void requestAndEventPassComposeOnceAndDisabledTypesDoNotReappear() throws Exception {
        String settings = "MEDICATION_REQUEST_TREATMENT=replace-administration\nMEDICATION_ADMINISTRATION_TREATMENT=replace-statement\n"
                + "MEDICATION_STATEMENT_TREATMENT=replace-administration\n";
        var output = run(settings);
        assertEquals(4, report.medicationTransformations.size());
        assertEquals(0, output.values().stream().filter(MedicationRequest.class::isInstance).count());
        assertEquals(1, output.values().stream().filter(MedicationAdministration.class::isInstance).count());
        assertEquals(2, output.values().stream().filter(MedicationStatement.class::isInstance).count());
        for (Resource resource : output.values()) localReferences(resource, output);
        for (String disabled : List.of("MEDICATION_REQUEST", "MEDICATION_ADMINISTRATION", "MEDICATION_STATEMENT")) {
            var selected = run(settings + disabled + "_ENABLED=false\n");
            String type = disabled.equals("MEDICATION_REQUEST") ? "MedicationRequest"
                    : disabled.equals("MEDICATION_ADMINISTRATION") ? "MedicationAdministration" : "MedicationStatement";
            assertFalse(selected.values().stream().anyMatch(r -> r.fhirType().equals(type)));
            for (Resource resource : selected.values()) localReferences(resource, selected);
            assertFalse(report.medicationTransformations.stream().anyMatch(c -> c.get("source").startsWith(type + "/") || c.get("target").startsWith(type + "/")));
        }
    }
    @Test public void shiftedDerivedEventsKeepTheirContactAndRespectMedicationReferenceModes() throws Exception {
        String settings = "MEDICATION_ADMINISTRATION_TREATMENT=replace-statement\nTIME_SHIFT_ENABLED=true\nTIME_SHIFT_BASE_DAYS=7\n";
        var baseline = run(settings);
        var derived = (MedicationStatement)baseline.get(change("MedicationAdministration").get("target"));
        assertEquals("2026-01-09", derived.getEffectivePeriod().getStartElement().getValueAsString());
        assertEquals("2026-01-10", derived.getEffectivePeriod().getEndElement().getValueAsString());
        Encounter contact = (Encounter)baseline.get(derived.getContext().getReference());
        assertNotNull(contact);
        assertEquals("2026-01-08", contact.getPeriod().getStartElement().getValueAsString());
        assertEquals("2026-01-17", contact.getPeriod().getEndElement().getValueAsString());
        for (String mode : List.of("reference-only", "neither")) {
            var output = run(settings + "MEDICATION_MODE=" + mode + "\nMEDICATION_EXISTING_REFERENCE=true\n"
                    + "DAR_MEDICATION_STATEMENT_DOSAGE_DOSE_AND_RATE_DOSE_X=custom-dose\n");
            assertFalse(output.values().stream().anyMatch(Medication.class::isInstance));
            var changed = (MedicationStatement)output.get(change("MedicationAdministration").get("target"));
            assertTrue(derived.getEffective().equalsDeep(changed.getEffective()));
            assertTrue(derived.getContext().equalsDeep(changed.getContext()));
            var dose = changed.getDosageFirstRep().getDoseAndRateFirstRep().getDose();
            assertEquals("custom-dose", dose.getExtensionByUrl(DarOverrides.URL).getValue().primitiveValue());
            for (Resource resource : output.values()) {
                Type medication = resource instanceof MedicationStatement ? ((MedicationStatement)resource).getMedication()
                        : resource instanceof MedicationRequest ? ((MedicationRequest)resource).getMedication() : null;
                if (medication == null) continue;
                if (mode.equals("neither")) assertTrue(medication.isEmpty());
                else {
                    assertTrue(medication instanceof Reference);
                    assertTrue(baseline.containsKey(((Reference)medication).getReference()));
                }
            }
        }
    }

    private String reason(Element element) {
        var extension = element.getExtensionByUrl(DarOverrides.URL);
        return extension == null ? null : extension.getValue().primitiveValue();
    }
    @Test public void complexMedicationDarReplacesWholeValuesAndPreservesUnselectedFields() throws Exception {
        String transformations = "MEDICATION_STATEMENT_TREATMENT=add-administration\nMEDICATION_ADMINISTRATION_TREATMENT=add-statement\n";
        String rules = "DAR_MEDICATION_ADMINISTRATION_EFFECTIVE_X=free-period\n"
                + "DAR_MEDICATION_ADMINISTRATION_DOSAGE_DOSE=free-dose\n"
                + "DAR_MEDICATION_STATEMENT_EFFECTIVE_X=free-statement-period\n";
        var baseline = run(transformations);
        var missing = run(transformations + rules
                + "DAR_MEDICATION_ADMINISTRATION_EFFECTIVE_X_ONLY_WHEN_MISSING=true\n"
                + "DAR_MEDICATION_ADMINISTRATION_DOSAGE_DOSE_ONLY_WHEN_MISSING=true\n"
                + "DAR_MEDICATION_STATEMENT_EFFECTIVE_X_ONLY_WHEN_MISSING=true\n");
        var overwrite = run(transformations + rules);
        assertEquals(baseline.keySet(), overwrite.keySet());
        assertEquals(baseline.keySet(), missing.keySet());
        int periods = 0, doses = 0, statements = 0;
        for (String key : baseline.keySet()) {
            Resource original = baseline.get(key), expected = original.copy();
            if (original instanceof MedicationAdministration) {
                var source = (MedicationAdministration)original;
                var changed = (MedicationAdministration)overwrite.get(key);
                assertTrue(source.hasEffective());
                assertTrue(source.getEffective().equalsDeep(((MedicationAdministration)missing.get(key)).getEffective()));
                assertEquals("free-period", reason(changed.getEffective()));
                if (changed.getEffective() instanceof Period) {
                    assertFalse(changed.getEffectivePeriod().hasStart()); assertFalse(changed.getEffectivePeriod().hasEnd()); periods++;
                }
                var expectedAdministration = (MedicationAdministration)expected;
                expectedAdministration.setEffective(changed.getEffective().copy());
                if (source.hasDosage()) {
                    assertEquals("free-dose", reason(changed.getDosage().getDose()));
                    assertFalse(changed.getDosage().getDose().hasValue()); assertFalse(changed.getDosage().getDose().hasUnit());
                    assertFalse(changed.getDosage().getDose().hasSystem()); assertFalse(changed.getDosage().getDose().hasCode());
                    if (source.getDosage().getDose().hasValue()) {
                        assertTrue(source.getDosage().getDose().equalsDeep(((MedicationAdministration)missing.get(key)).getDosage().getDose()));
                        doses++;
                    }
                    expectedAdministration.getDosage().setDose(changed.getDosage().getDose().copy());
                }
            } else if (original instanceof MedicationStatement) {
                var source = (MedicationStatement)original;
                var changed = (MedicationStatement)overwrite.get(key);
                assertTrue(source.hasEffective());
                assertTrue(source.getEffective().equalsDeep(((MedicationStatement)missing.get(key)).getEffective()));
                assertEquals("free-statement-period", reason(changed.getEffective()));
                if (changed.getEffective() instanceof Period) {
                    assertFalse(changed.getEffectivePeriod().hasStart()); assertFalse(changed.getEffectivePeriod().hasEnd());
                }
                ((MedicationStatement)expected).setEffective(changed.getEffective().copy()); statements++;
            } else assertTrue(key, original.equalsDeep(missing.get(key)));
            assertTrue("Unselected fields changed: " + key, expected.equalsDeep(overwrite.get(key)));
        }
        assertTrue(periods > 0); assertTrue(doses > 0); assertTrue(statements > 0);
    }


}
