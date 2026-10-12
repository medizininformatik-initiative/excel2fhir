package de.uni_leipzig.life.csv2fhir;

import static org.junit.Assert.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.csv.*;
import org.hl7.fhir.r4.model.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

/** Exercises contact policies together through CSV input and serialized FHIR output. */
public class ContactWorkflowTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private Path input;
    private ImportReport report;

    @Before public void setup() throws Exception {
        input = temp.newFolder().toPath();
        table(TableIdentifier.Person, List.of(patient("p1"), patient("p2")));
        List<Map<String, String>> contacts = new ArrayList<>();
        for (String patient : List.of("p1", "p2")) for (String number : List.of("1", "2")) {
            String month = number.equals("1") ? "01" : "02";
            contacts.add(Map.of("Patient-ID", patient, "Fall-Nr", number, "Start", "2026-" + month + "-01",
                    "Ende", "2026-" + month + "-10", "Einrichtungskontaktklasse", "stationaer"));
            contacts.add(Map.of("Patient-ID", patient, "Fall-Nr", number, "Start", "2026-" + month + "-01",
                    "Ende", "2026-" + month + "-05", "Fachabteilung", "0100", "Station", "A"));
            contacts.add(Map.of("Patient-ID", patient, "Fall-Nr", number, "Start", "2026-" + month + "-05",
                    "Ende", "2026-" + month + "-10", "Fachabteilung", "0200", "Station", "B"));
        }
        table(TableIdentifier.Fall, contacts);
    }
    private Map<String, String> patient(String id) {
        return Map.of("Patient-ID", id, "Vorname", "Test", "Nachname", "Person", "Geburtsdatum", "2000-01-01", "Geschlecht", "weiblich");
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
    private List<Resource> run(String settings) throws Exception {
        Path output = temp.newFolder().toPath();
        var converter = new Csv2Fhir(input.toFile(), output.toFile(), "case_", null,
                ConverterOptions.fromText("CONFIGURATION_VERSION=1\nOUTPUT_PATIENTS_PER_FILE=1000\n" + settings));
        converter.convertFiles(Integer.MAX_VALUE, OutputFileType.JSON);
        report = converter.getImportReport();
        assertFalse(report.issues.toString(), report.hasErrors());
        Bundle bundle = (Bundle)OutputFileType.JSON.getParser().parseResource(Files.readString(output.resolve("case.json")));
        return bundle.getEntry().stream().map(Bundle.BundleEntryComponent::getResource).toList();
    }
    private List<Encounter> encounters(List<Resource> resources) {
        return resources.stream().filter(Encounter.class::isInstance).map(Encounter.class::cast).toList();
    }
    private boolean level(Encounter encounter, String code) {
        return encounter.getType().stream().flatMap(t -> t.getCoding().stream()).anyMatch(c -> code.equals(c.getCode()));
    }
    private Encounter target(List<Resource> resources, Reference reference) {
        assertTrue(reference.hasReference());
        return encounters(resources).stream().filter(e -> e.getIdElement().getIdPart().equals(reference.getReferenceElement().getIdPart()))
                .findFirst().orElseThrow(() -> new AssertionError("Missing target: " + reference.getReference()));
    }
    @Test public void diagnosisPropagationAndClinicalReferencesStayWithinPatientAndCaseAtTransferBoundary() throws Exception {
        List<Map<String, String>> diagnoses = new ArrayList<>();
        for (String patient : List.of("p1", "p2")) for (String number : List.of("1", "2"))
            diagnoses.add(Map.of("Patient-ID", patient, "Fall-Nr", number, "Code", "A01", "Codesystem", "ICD-10-GM 2026",
                    "Typ", "Hauptdiagnose", "Dokumentationszeitpunkt", "2026-0" + number + "-05"));
        table(TableIdentifier.Diagnose, diagnoses);
        var resources = run("REFERENCE_CONDITION_ENCOUNTER=department\nCONTACT_DIAGNOSES_LEVELS=department,ward-service\n");
        int checked = 0;
        for (Resource resource : resources) if (resource instanceof Condition) {
            Condition condition = (Condition)resource;
            Encounter department = target(resources, condition.getEncounter());
            assertTrue(level(department, "abteilungskontakt"));
            assertEquals(condition.getSubject().getReference(), department.getSubject().getReference());
            assertEquals(condition.getRecordedDate(), department.getPeriod().getStart());
            Encounter root = target(resources, department.getPartOf());
            assertTrue(condition.getIdElement().getIdPart().startsWith(root.getIdElement().getIdPart()));
            List<Encounter> carriers = encounters(resources).stream().filter(e -> e.getDiagnosis().stream()
                    .anyMatch(d -> condition.getIdElement().getIdPart().equals(d.getCondition().getReferenceElement().getIdPart()))).toList();
            assertEquals(new com.google.gson.Gson().toJson(report.diagnosisReferenceIssues), 2, carriers.size());
            for (Encounter carrier : carriers) {
                assertEquals(condition.getSubject().getReference(), carrier.getSubject().getReference());
                assertEquals(condition.getRecordedDate(), carrier.getPeriod().getStart());
                assertTrue(carrier.getIdElement().getIdPart().startsWith(root.getIdElement().getIdPart()));
            }
            checked++;
        }
        assertEquals(4, checked);
        assertTrue(report.diagnosisReferenceIssues.isEmpty());
        assertTrue(report.encounterReferenceIssues.isEmpty());
    }
    @Test public void disabledDepartmentDoesNotLeaveParentOrClinicalReferencesAndWardCanPointToFacility() throws Exception {
        table(TableIdentifier.Diagnose, List.of(Map.of("Patient-ID", "p1", "Fall-Nr", "1", "Code", "A01",
                "Codesystem", "ICD-10-GM 2026", "Typ", "Hauptdiagnose", "Dokumentationszeitpunkt", "2026-01-05")));
        for (String parent : List.of("department", "facility")) {
            var resources = run("CONTACT_DEPARTMENT_ENABLED=false\nCONTACT_WARD_SERVICE_PART_OF=" + parent
                    + "\nREFERENCE_CONDITION_ENCOUNTER=department\nCONTACT_DIAGNOSES_LEVELS=department,ward-service\n");
            int wards = 0, conditions = 0, diagnoses = 0;
            for (Encounter encounter : encounters(resources)) {
                assertFalse(level(encounter, "abteilungskontakt"));
                diagnoses += encounter.getDiagnosis().size();
                if (level(encounter, "versorgungsstellenkontakt")) {
                    wards++;
                    if (parent.equals("facility")) assertTrue(level(target(resources, encounter.getPartOf()), "einrichtungskontakt"));
                    else assertFalse(encounter.hasPartOf());
                }
            }
            for (Resource resource : resources) if (resource instanceof Condition) {
                assertFalse(((Condition)resource).hasEncounter()); conditions++;
            }
            assertEquals(8, wards); assertEquals(1, conditions); assertEquals(1, diagnoses);
            assertTrue(report.diagnosisReferenceIssues.isEmpty());
        }
    }
    @Test public void procedureDiagnosisReferencesSurviveDefaultAndDownwardAssignmentAndRespectDeselection() throws Exception {
        table(TableIdentifier.Prozedur, List.of(Map.of("Patient-ID", "p1", "Fall-Nr", "1", "Prozedurencode", "123",
                "Codesystem", "SNOMED CT (Version nicht angegeben)", "Durchführungsbeginn", "2026-01-05")));
        for (String selected : List.of("facility", "department", "ward-service")) {
            var resources = run("CONTACT_PROCEDURE_DIAGNOSES_LEVELS=" + selected + "\n");
            var procedures = resources.stream().filter(Procedure.class::isInstance).toList();
            assertEquals(1, procedures.size());
            var carriers = encounters(resources).stream().filter(e -> !e.getDiagnosis().isEmpty()).toList();
            assertEquals(1, carriers.size());
            Encounter carrier = carriers.get(0);
            assertEquals("Procedure/" + procedures.get(0).getIdElement().getIdPart(), carrier.getDiagnosisFirstRep().getCondition().getReference());
            assertTrue(level(carrier, selected.equals("facility") ? "einrichtungskontakt"
                    : selected.equals("department") ? "abteilungskontakt" : "versorgungsstellenkontakt"));
            assertEquals("Patient/p1", carrier.getSubject().getReference());
            assertTrue(report.diagnosisReferenceIssues.isEmpty());
        }
        var disabled = run("PROCEDURE_ENABLED=false\n");
        assertFalse(disabled.stream().anyMatch(Procedure.class::isInstance));
        assertTrue(encounters(disabled).stream().allMatch(e -> e.getDiagnosis().isEmpty()));
    }
    @Test public void documentStrategiesPreserveExplicitConflictsAndOnlyFillTrulyMissingReferences() throws Exception {
        List<Map<String, String>> documents = new ArrayList<>();
        for (String number : List.of("1", "", "missing")) documents.add(Map.of("Patient-ID", "p1", "Fall-Nr", number,
                "Dokumenttext", "Document", "Ausgabezeitpunkt", "2026-02-05T00:00:00Z"));
        documents.add(Map.of("Patient-ID", "p1", "Fall-Nr", "", "Dokumenttext", "Undated document"));
        table(TableIdentifier.DocumentReference, documents);
        for (String strategy : List.of("explicit-only", "fill-missing", "timestamp-only")) {
            var resources = run("REFERENCE_DOCUMENT_REFERENCE_ENCOUNTER=facility\nREFERENCE_DOCUMENT_REFERENCE_ASSIGNMENT_STRATEGY=" + strategy + "\n");
            var docs = resources.stream().filter(DocumentReference.class::isInstance).map(DocumentReference.class::cast).toList();
            assertEquals(4, docs.size());
            for (int i = 0; i < docs.size(); i++) {
                var refs = docs.get(i).getContext().getEncounter();
                boolean assigned = i < 3 && (strategy.equals("timestamp-only") || i == 0 || (i == 1 && strategy.equals("fill-missing")));
                assertEquals(strategy + " document " + i, assigned ? 1 : 0, refs.size());
                if (assigned) {
                    var contact = target(resources, refs.get(0));
                    assertEquals("Patient/p1", contact.getSubject().getReference());
                    assertEquals(i == 0 && !strategy.equals("timestamp-only") ? "2026-01-01" : "2026-02-01",
                            contact.getPeriod().getStartElement().getValueAsString());
                }
            }
            var reasons = report.encounterReferenceIssues.stream().map(issue -> issue.get("reason")).toList();
            if (!strategy.equals("timestamp-only")) {
                assertTrue(reasons.stream().anyMatch(r -> r.startsWith("explicit-contact-time-conflict:")));
                assertTrue(reasons.stream().anyMatch(r -> r.startsWith("unresolved-input-contact:")));
            }
            if (!strategy.equals("explicit-only")) assertTrue(reasons.contains("missing-timestamp"));
        }
    }
}
