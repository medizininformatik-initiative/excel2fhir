package de.uni_leipzig.life.synthifhir;

import static org.junit.Assert.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.csv.*;
import org.hl7.fhir.r4.model.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

/** Explicit coding versions and profile claims through serialized conversion. */
public class TerminologyWorkflowTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private Path input;
    private ImportReport report;
    private static final String KDS = "https://www.medizininformatik-initiative.de/fhir/core/";

    @Before public void setup() throws Exception {
        input = temp.newFolder().toPath();
        table(TableIdentifier.Person, List.of(Map.of("Patient-ID", "p1", "Vorname", "Test", "Nachname", "Person",
                "Geburtsdatum", "2000-01-01", "Geschlecht", "weiblich")));
        table(TableIdentifier.Fall, List.of(Map.of("Patient-ID", "p1", "Fall-Nr", "1", "Start", "2025-12-31",
                "Ende", "2026-01-02", "Einrichtungskontaktklasse", "stationaer")));
    }
    private void versions(String value) throws Exception {
        table(TableIdentifier.Diagnose, List.of(Map.of("Patient-ID", "p1", "Fall-Nr", "1", "Code", "A01",
                "Codesystem", "ICD-10-GM 2026", "Version", value, "Zusatzcode", "R53", "Zusatzcodesystem", "ICD-10-GM 2025",
                "Zusatzversion", value, "Dokumentationszeitpunkt", "2025-12-31")));
        table(TableIdentifier.Prozedur, List.of(Map.of("Patient-ID", "p1", "Fall-Nr", "1", "Prozedurencode", "5-511.y",
                "Codesystem", "OPS", "Version", value, "Durchführungsbeginn", "2025-12-31")));
        table(TableIdentifier.Medikation, List.of(Map.of("Patient-ID", "p1", "Fall-Nr", "1", "Medikationstyp", "Verordnung",
                "ATC-Code", "A10BA02", "ATC-Version", value, "Dokumentationszeitpunkt", "2025-12-31")));
        table(TableIdentifier.Impfung, List.of(Map.of("Patient-ID", "p1", "Fall-Nr", "1", "Eintrag ID", "v1",
                "Code", "J07BF03", "Codesystem", "ATC", "Version", value, "Zeitpunkt", "2025-12-31")));
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
    private List<Coding> codings(Map<String, Resource> output) {
        var condition = single(output, Condition.class);
        assertEquals(2, condition.getCode().getCoding().size());
        var result = new ArrayList<>(condition.getCode().getCoding());
        result.add(single(output, Procedure.class).getCode().getCodingFirstRep());
        result.add(single(output, Medication.class).getCode().getCodingFirstRep());
        result.add(single(output, Immunization.class).getVaccineCode().getCodingFirstRep());
        assertEquals(List.of("A01", "R53", "5-511.y", "A10BA02", "J07BF03"), result.stream().map(Coding::getCode).toList());
        assertEquals(List.of("icd-10-gm", "icd-10-gm", "ops", "atc", "atc"), result.stream()
                .map(c -> c.getSystem().substring(c.getSystem().lastIndexOf('/') + 1)).toList());
        return result;
    }
    private void profile(Resource resource, String canonical) {
        assertEquals(List.of(canonical), resource.getMeta().getProfile().stream().map(CanonicalType::getValue).toList());
    }
    @Test public void explicitYearsFreeVersionsOmissionAndDarSurviveSerialization() throws Exception {
        for (String version : List.of("2025", "2026", "2015", "custom release #1", "", "!dar:unknown")) {
            versions(version);
            var output = run("");
            for (Coding coding : codings(output)) {
                if (version.startsWith("!dar:")) {
                    assertFalse(coding.getVersionElement().hasValue());
                    assertEquals("unknown", coding.getVersionElement().getExtensionByUrl(DarOverrides.URL).getValue().primitiveValue());
                } else if (version.isEmpty()) assertTrue(coding.getVersionElement().isEmpty());
                else assertEquals(version, coding.getVersion());
            }
            profile(single(output, Condition.class), KDS + "modul-diagnose/StructureDefinition/Diagnose");
            profile(single(output, Procedure.class), KDS + "modul-prozedur/StructureDefinition/Procedure");
            profile(single(output, Medication.class), KDS + "modul-medikation/StructureDefinition/Medication");
            profile(single(output, MedicationRequest.class), KDS + "modul-medikation/StructureDefinition/MedicationRequest");
            assertFalse(single(output, Immunization.class).getMeta().hasProfile());
        }
    }
    @Test public void mappingSettingsAndDateShiftDoNotRewriteExplicitVersionsOrDerivedProfiles() throws Exception {
        versions("custom edition");
        var baseline = run("");
        for (String year : List.of("2025", "2026")) {
            var output = run("SYNTHEA_MAPPING_YEAR=" + year + "\nTERMINOLOGY_VERSION_OUTPUT=omit\n"
                    + "TIME_SHIFT_ENABLED=true\nTIME_SHIFT_BASE_DAYS=2\nMEDICATION_REQUEST_TREATMENT=replace-statement\n");
            for (Coding coding : codings(output)) assertEquals("custom edition", coding.getVersion());
            assertEquals("2026-01-02", single(output, Condition.class).getRecordedDateElement().getValueAsString());
            assertFalse(output.values().stream().anyMatch(MedicationRequest.class::isInstance));
            profile(single(output, MedicationStatement.class), KDS + "modul-medikation/StructureDefinition/MedicationStatement");
            for (var entry : output.entrySet()) if (baseline.containsKey(entry.getKey()))
                assertEquals(baseline.get(entry.getKey()).getMeta().getProfile().stream().map(CanonicalType::getValue).toList(),
                        entry.getValue().getMeta().getProfile().stream().map(CanonicalType::getValue).toList());
        }
    }
}
