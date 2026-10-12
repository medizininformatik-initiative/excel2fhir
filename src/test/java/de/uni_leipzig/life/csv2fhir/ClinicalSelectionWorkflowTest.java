package de.uni_leipzig.life.csv2fhir;

import static org.junit.Assert.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.csv.*;
import org.hl7.fhir.r4.model.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

/** Covers clinical types absent from the starter workbook through serialized output. */
public class ClinicalSelectionWorkflowTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private Path input;

    @Before public void setup() throws Exception {
        input = temp.newFolder().toPath();
        table(TableIdentifier.Person, Map.of("Patient-ID", "p1", "Vorname", "Test", "Nachname", "Person",
                "Geburtsdatum", "2000-01-01", "Geschlecht", "weiblich", "Datum Einwilligung", "2026-01-01", "PDAT Einwilligung", "ja"));
        table(TableIdentifier.Fall, Map.of("Patient-ID", "p1", "Fall-Nr", "1", "Start", "2026-01-01", "Ende", "2026-01-10",
                "Einrichtungskontaktklasse", "stationaer"));
        for (TableIdentifier type : List.of(TableIdentifier.Impfung, TableIdentifier.Befundbericht, TableIdentifier.Behandlungsplan)) {
            var row = new LinkedHashMap<>(Map.of("Patient-ID", "p1", "Fall-Nr", "1", "Eintrag ID", "event",
                    "Code", "example", "Codesystem", "SNOMED CT (Version nicht angegeben)", "Zeitpunkt", "2026-01-05", "Beschreibung", "Original text"));
            if (type == TableIdentifier.Befundbericht) row.put("Ergebnisse", "lab;vital");
            table(type, row);
        }
        table(TableIdentifier.Laborbefund, Map.of("Patient-ID", "p1", "Fall-Nr", "1", "Untersuchung ID", "lab",
                "LOINC", "718-7", "Messwert", "12.5", "Einheit", "g/dL", "Zeitstempel (Abnahme)", "2026-01-05"));
        table(TableIdentifier.Klinische_Dokumentation, Map.of("Patient-ID", "p1", "Fall-Nr", "1", "Untersuchung ID", "vital",
                "Untersuchungscode", "8867-4", "Wert", "70", "Einheit", "/min", "Zeitstempel", "2026-01-05"));
        table(TableIdentifier.DocumentReference, Map.of("Patient-ID", "p1", "Fall-Nr", "1", "Dokumenttext", "Original document",
                "Ausgabezeitpunkt", "2026-01-05T00:00:00Z"));
    }
    private void table(TableIdentifier type, Map<String, String> row) throws Exception {
        table(type, List.of(row));
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
                ConverterOptions.fromText("CONFIGURATION_VERSION=1\nOUTPUT_PATIENTS_PER_FILE=1000\n" + settings));
        converter.convertFiles(1000, OutputFileType.JSON, OutputFileType.NDJSON);
        assertFalse(new com.google.gson.Gson().toJson(converter.getImportReport().issues), converter.getImportReport().hasErrors());
        Map<String, Resource> json = new LinkedHashMap<>(), ndjson = new LinkedHashMap<>();
        try (var files = Files.walk(output)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                if (file.getFileName().toString().equals("case.json")) {
                    Bundle bundle = (Bundle)OutputFileType.JSON.getParser().parseResource(Files.readString(file));
                    for (var entry : bundle.getEntry()) add(json, entry.getResource());
                } else if (file.toString().endsWith(".ndjson")) {
                    for (String line : Files.readAllLines(file)) add(ndjson, (Resource)OutputFileType.JSON.getParser().parseResource(line));
                }
            }
        }
        assertFalse(json.isEmpty()); assertEquals(json.keySet(), ndjson.keySet());
        for (String key : json.keySet()) assertTrue(key, json.get(key).equalsDeep(ndjson.get(key)));
        return json;
    }
    private void add(Map<String, Resource> resources, Resource resource) {
        if (resource instanceof Bundle) {
            for (var entry : ((Bundle)resource).getEntry()) add(resources, entry.getResource());
            return;
        }
        String key = resource.fhirType() + "/" + resource.getIdElement().getIdPart();
        assertNull("Duplicate " + key, resources.put(key, resource));
    }
    @Test public void missingOptionalConsentColumnsMatchBlankColumnsWithoutGrantingOtherGroups() throws Exception {
        var missing = run("");
        var patient = new LinkedHashMap<>(Map.of("Patient-ID", "p1", "Vorname", "Test", "Nachname", "Person",
                "Geburtsdatum", "2000-01-01", "Geschlecht", "weiblich", "Datum Einwilligung", "2026-01-01", "PDAT Einwilligung", "ja"));
        for (String column : List.of("KKDAT retro Einwilligung", "KKDAT Einwilligung", "BIOMAT Einwilligung", "BIOMAT Zusatz Einwilligung"))
            patient.put(column, "");
        table(TableIdentifier.Person, patient);
        var blank = run("");
        assertEquals(missing.keySet(), blank.keySet());
        for (String key : missing.keySet()) assertTrue(key, missing.get(key).equalsDeep(blank.get(key)));
        var consent = (Consent)missing.values().stream().filter(Consent.class::isInstance).findFirst().orElseThrow();
        assertEquals(Consent.ConsentProvisionType.DENY, consent.getProvision().getType());
        Set<String> codes = new HashSet<>();
        for (var provision : consent.getProvision().getProvision()) {
            assertEquals(Consent.ConsentProvisionType.PERMIT, provision.getType());
            for (var code : provision.getCode()) for (var coding : code.getCoding()) codes.add(coding.getCode());
        }
        assertEquals(Set.of("2", "3", "4", "5", "6", "7", "8", "9", "37").stream()
                .map(code -> "2.16.840.1.113883.3.1937.777.24.5.3." + code).collect(java.util.stream.Collectors.toSet()), codes);
    }
    @Test public void individualSelectionsRemovePopulatedTypesAndPreserveOtherResources() throws Exception {
        var baseline = run("");
        for (var selection : Map.of("Immunization", "IMMUNIZATION", "DiagnosticReport", "DIAGNOSTIC_REPORT",
                "CarePlan", "CARE_PLAN", "Consent", "CONSENT", "DocumentReference", "DOCUMENT_REFERENCE").entrySet()) {
            String type = selection.getKey();
            assertEquals(type, 1, baseline.values().stream().filter(r -> type.equals(r.fhirType())).count());
            var output = run(selection.getValue() + "_ENABLED=false\n");
            Set<String> expected = new HashSet<>(baseline.keySet());
            expected.removeIf(k -> k.startsWith(type + "/"));
            assertEquals(type, expected, output.keySet());
            for (String key : expected) assertTrue(key, baseline.get(key).equalsDeep(output.get(key)));
        }
    }
    @Test public void observationSelectionPrunesReportResultsByCategoryAndRetainsClinicalContent() throws Exception {
        var baseline = run("");
        var original = (DiagnosticReport)baseline.values().stream().filter(DiagnosticReport.class::isInstance).findFirst().orElseThrow();
        assertEquals(2, original.getResult().size());
        for (Reference ref : original.getResult()) assertTrue(ref.getReference(), baseline.containsKey(ref.getReference()));
        for (String category : List.of("laboratory", "vital-signs")) {
            String option = category.equals("laboratory") ? "LABORATORY" : "VITAL_SIGNS";
            var output = run("OBSERVATION_" + option + "_ENABLED=false\n");
            var observations = output.values().stream().filter(Observation.class::isInstance).map(Observation.class::cast).toList();
            assertEquals(1, observations.size());
            assertFalse(category.equals(observations.get(0).getCategoryFirstRep().getCodingFirstRep().getCode()));
            var expected = original.copy();
            expected.getResult().removeIf(ref -> !output.containsKey(ref.getReference()));
            assertEquals(1, expected.getResult().size());
            String reportKey = "DiagnosticReport/" + original.getIdElement().getIdPart();
            assertTrue(expected.equalsDeep(output.get(reportKey)));
            for (String key : output.keySet()) if (!key.equals(reportKey)) assertTrue(key, baseline.get(key).equalsDeep(output.get(key)));
        }
    }
    @Test public void attachmentDarRetainsDataInMissingModeAndRemovesPayloadMetadataOnOverwrite() throws Exception {
        var baseline = run("");
        String key = baseline.keySet().stream().filter(k -> k.startsWith("DocumentReference/")).findFirst().orElseThrow();
        var original = (DocumentReference)baseline.get(key);
        assertTrue(original.getContentFirstRep().getAttachment().hasData());
        String rule = "DAR_DOCUMENT_REFERENCE_CONTENT_ATTACHMENT_DATA=custom-mask\n";
        assertTrue(original.equalsDeep(run(rule + "DAR_DOCUMENT_REFERENCE_CONTENT_ATTACHMENT_DATA_ONLY_WHEN_MISSING=true\n").get(key)));
        var output = run(rule);
        var attachment = ((DocumentReference)output.get(key)).getContentFirstRep().getAttachment();
        assertFalse(attachment.getDataElement().hasValue()); assertFalse(attachment.hasHash()); assertFalse(attachment.hasSize());
        assertEquals("custom-mask", attachment.getDataElement().getExtensionByUrl(DarOverrides.URL).getValue().primitiveValue());
        assertEquals(baseline.keySet(), output.keySet());
        for (String other : baseline.keySet()) if (!other.equals(key)) assertTrue(other, baseline.get(other).equalsDeep(output.get(other)));
    }
    @Test public void measurementDarPreservesSuppliedValuesAndCustomReasonsInMissingMode() throws Exception {
        List<Map<String, String>> rows = new ArrayList<>();
        for (String kind : List.of("Zahl", "Code", "Fehlend", "Komponenten")) {
            var row = new LinkedHashMap<>(Map.of("Patient-ID", "p1", "Fall-Nr", "1", "Untersuchung ID", kind,
                    "LOINC", "718-7", "Zeitstempel (Abnahme)", "2026-01-05", "Werttyp", kind));
            if (kind.equals("Zahl")) { row.put("Messwert", "-123.5"); row.put("Einheit", "custom-unit"); }
            if (kind.equals("Code")) { row.put("Wertcode", "free-code"); row.put("Wertcodesystem", "LOINC"); row.put("Messwert", "Free text"); }
            if (kind.equals("Fehlend")) row.put("Messwert", "!dar:existing-custom");
            rows.add(row);
        }
        table(TableIdentifier.Laborbefund, rows);
        var baseline = run("");
        for (String variant : List.of("NUMERIC_MEASUREMENT", "MEASUREMENT")) {
            String rule = "DAR_LABORATORY_VALUE_X_" + variant + "=replacement-custom\n";
            var missing = run(rule + "DAR_LABORATORY_VALUE_X_" + variant + "_ONLY_WHEN_MISSING=true\n");
            var overwrite = run(rule);
            int kept = 0, replaced = 0, filled = 0;
            for (String key : baseline.keySet()) {
                Resource original = baseline.get(key);
                if (!(original instanceof Observation) || !"laboratory".equals(((Observation)original).getCategoryFirstRep().getCodingFirstRep().getCode())) {
                    assertTrue(key, original.equalsDeep(missing.get(key)));
                    assertTrue(key, original.equalsDeep(overwrite.get(key)));
                    continue;
                }
                Observation observation = (Observation)original;
                if (observation.getValue() instanceof Quantity) {
                    assertEquals(0, new java.math.BigDecimal("-123.5").compareTo(observation.getValueQuantity().getValue()));
                    assertEquals("custom-unit", observation.getValueQuantity().getUnit());
                } else if (observation.getValue() instanceof CodeableConcept) {
                    assertEquals("free-code", observation.getValueCodeableConcept().getCodingFirstRep().getCode());
                    assertEquals("Free text", observation.getValueCodeableConcept().getText());
                }
                if (observation.hasDataAbsentReason()) assertEquals("existing-custom", observation.getDataAbsentReason().getCodingFirstRep().getCode());
                var actualMissing = (Observation)missing.get(key);
                if (observation.hasValue() || observation.hasDataAbsentReason()) {
                    assertTrue(key, observation.equalsDeep(actualMissing)); kept++;
                } else {
                    var expected = observation.copy();
                    expected.setDataAbsentReason(new CodeableConcept(new Coding("http://terminology.hl7.org/CodeSystem/data-absent-reason", "replacement-custom", null)));
                    assertTrue(key, expected.equalsDeep(actualMissing)); filled++;
                }
                var expected = observation.copy();
                if (observation.hasDataAbsentReason() || observation.hasValue()
                        && (observation.getValue() instanceof Quantity) == variant.equals("NUMERIC_MEASUREMENT")) {
                    expected.setValue(null).setDataAbsentReason(new CodeableConcept(new Coding("http://terminology.hl7.org/CodeSystem/data-absent-reason", "replacement-custom", null)));
                    replaced++;
                }
                assertTrue(key, expected.equalsDeep(overwrite.get(key)));
            }
            assertEquals(3, kept); assertEquals(1, filled); assertEquals(2, replaced);
        }
    }

    @Test public void customSystemsSurviveMainAdditionalAnswerAndMedicationCodings() throws Exception {
        String main = "https://example.invalid/custom#v1", extra = "urn:example:extra";
        table(TableIdentifier.Diagnose, Map.of("Patient-ID", "p1", "Code", "nonsense", "Codesystem", main,
                "Zusatzcode", "also-wrong", "Zusatzcodesystem", extra, "Version", "custom-version"));
        table(TableIdentifier.Prozedur, Map.of("Patient-ID", "p1", "Prozedurencode", "nonsense", "Codesystem", main,
                "Zusatzcode", "also-wrong", "Zusatzcodesystem", extra));
        table(TableIdentifier.Laborbefund, Map.of("Patient-ID", "p1", "LOINC", "nonsense", "Codesystem", main,
                "Zusatzcode", "also-wrong", "Zusatzcodesystem", extra, "Werttyp", "Code",
                "Wertcode", "answer", "Wertcodesystem", "urn:example:answers"));
        table(TableIdentifier.Klinische_Dokumentation, Map.of("Patient-ID", "p1", "Untersuchungscode", "nonsense", "Codesystem", main,
                "Zusatzcode", "also-wrong", "Zusatzcodesystem", extra, "Werttyp", "Code",
                "Wertcode", "answer", "Wertcodesystem", "urn:example:answers"));
        table(TableIdentifier.Medikation, Map.of("Patient-ID", "p1", "Medikationstyp", "Verordnung",
                "Präparatcode", "nonsense", "Präparatcodesystem", main, "Wirkstoffcode", "ingredient", "Wirkstoffcodesystem", extra));
        for (TableIdentifier type : List.of(TableIdentifier.Impfung, TableIdentifier.Befundbericht, TableIdentifier.Behandlungsplan))
            table(type, Map.of("Patient-ID", "p1", "Eintrag ID", "event", "Code", "nonsense", "Codesystem", main));
        table(TableIdentifier.DocumentReference, Map.of("Patient-ID", "p1", "Dokumenttext", "text",
                "Dokumentcode", "nonsense", "Dokumentcodesystem", main));
        var resources = run("CHECK_INPUT_CONSISTENCY=true\n");
        Set<String> checked = new HashSet<>();
        for (Resource resource : resources.values()) {
            CodeableConcept code = null;
            if (resource instanceof Condition) code = ((Condition)resource).getCode();
            if (resource instanceof Procedure) code = ((Procedure)resource).getCode();
            if (resource instanceof Observation) {
                code = ((Observation)resource).getCode();
                assertEquals("urn:example:answers", ((Observation)resource).getValueCodeableConcept().getCodingFirstRep().getSystem());
            }
            if (resource instanceof Medication) {
                code = ((Medication)resource).getCode();
                assertEquals(extra, ((Medication)resource).getIngredientFirstRep().getItemCodeableConcept().getCodingFirstRep().getSystem());
            }
            if (resource instanceof Immunization) code = ((Immunization)resource).getVaccineCode();
            if (resource instanceof DiagnosticReport) code = ((DiagnosticReport)resource).getCode();
            if (resource instanceof CarePlan) code = ((CarePlan)resource).getCategoryFirstRep();
            if (resource instanceof DocumentReference) code = ((DocumentReference)resource).getType();
            if (code == null) continue;
            assertEquals(main, code.getCodingFirstRep().getSystem()); assertEquals("nonsense", code.getCodingFirstRep().getCode());
            if (resource instanceof Condition || resource instanceof Procedure || resource instanceof Observation) {
                assertEquals(2, code.getCoding().size()); assertEquals(extra, code.getCoding().get(1).getSystem());
                assertEquals("also-wrong", code.getCoding().get(1).getCode());
            }
            if (resource instanceof Condition) assertEquals("custom-version", code.getCodingFirstRep().getVersion());
            checked.add(resource.fhirType());
        }
        assertEquals(Set.of("Condition", "Procedure", "Observation", "Medication", "Immunization", "DiagnosticReport", "CarePlan", "DocumentReference"), checked);
    }

}
