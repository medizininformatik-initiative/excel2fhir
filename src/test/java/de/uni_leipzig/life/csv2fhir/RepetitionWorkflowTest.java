package de.uni_leipzig.life.csv2fhir;

import static org.junit.Assert.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import org.apache.commons.csv.*;
import org.hl7.fhir.r4.model.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

/** Combined identity and time-shift checks across repeated serialized datasets. */
public class RepetitionWorkflowTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private Path input;
    private ImportReport report;
    private static final List<String> PATIENTS = List.of("17", "550e8400-e29b-41d4-a716-446655440000");

    private String configuration() {
        return "PID_PREFIX=site-\nPID_SUFFIX=-x\nPID_LAST_NUMBER_INCREASE_LOOP_COUNT=2\n"
                + "TIME_SHIFT_ENABLED=true\nTIME_SHIFT_BASE_DAYS=10\nTIME_SHIFT_ITERATION_DAYS=-7\n"
                + "MEDICATION_ADMINISTRATION_TREATMENT=add-statement\n"
                + "IDENTIFIER_RULE_1_ID=a152e771-3d5a-4cb1-9866-35fa6d91fd83\nIDENTIFIER_RULE_1_ENABLED=true\n"
                + "IDENTIFIER_RULE_1_RESOURCES=Patient,Encounter,Condition,MedicationAdministration,MedicationStatement\n"
                + "IDENTIFIER_RULE_1_SYSTEM=urn:object\nIDENTIFIER_RULE_1_PATTERN={count}\nIDENTIFIER_RULE_1_COUNT_START=500\n"
                + "IDENTIFIER_RULE_2_ID=b152e771-3d5a-4cb1-9866-35fa6d91fd83\nIDENTIFIER_RULE_2_ENABLED=true\n"
                + "IDENTIFIER_RULE_2_SCOPE=case\nIDENTIFIER_RULE_2_RESOURCES=Encounter,Condition,MedicationAdministration,MedicationStatement\n"
                + "IDENTIFIER_RULE_2_SYSTEM=urn:case\nIDENTIFIER_RULE_2_PATTERN={patientId}-{caseId}\n";
    }
    @Before public void setup() throws Exception {
        input = temp.newFolder().toPath();
        List<Map<String, String>> patients = new ArrayList<>(), contacts = new ArrayList<>(), diagnoses = new ArrayList<>(), medications = new ArrayList<>();
        for (String patient : PATIENTS) {
            patients.add(Map.of("Patient-ID", patient, "Vorname", "Test", "Nachname", "Person", "Geburtsdatum", "2000-02-29", "Geschlecht", "weiblich"));
            for (String number : List.of("1", "2")) {
                String start = "2026-0" + number + "-01", event = "2026-0" + number + "-02";
                contacts.add(Map.of("Patient-ID", patient, "Fall-Nr", number, "Start", start, "Ende", "2026-0" + number + "-10",
                        "Einrichtungskontaktklasse", "stationaer", "Fachabteilung", "0100"));
                diagnoses.add(Map.of("Patient-ID", patient, "Fall-Nr", number, "Code", "A01", "Codesystem", "ICD-10-GM 2026",
                        "Typ", "Hauptdiagnose", "Dokumentationszeitpunkt", event));
                medications.add(Map.of("Patient-ID", patient, "Fall-Nr", number, "Medikationstyp", "Verabreichung",
                        "Präparatcode", "00000000", "Präparatcodesystem", "PZN", "Beginn", event, "Einzeldosis", "2", "Dosiereinheit", "mg"));
            }
        }
        table(TableIdentifier.Person, patients); table(TableIdentifier.Fall, contacts);
        table(TableIdentifier.Diagnose, diagnoses); table(TableIdentifier.Medikation, medications);
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
                        + "REFERENCE_CONDITION_ENCOUNTER=department\n"
                        + "REFERENCE_MEDICATION_STATEMENT_ENCOUNTER=department\n" + configuration() + settings));
        converter.convertFiles(Integer.MAX_VALUE, OutputFileType.JSON, OutputFileType.NDJSON);
        report = converter.getImportReport();
        assertFalse(new com.google.gson.Gson().toJson(report.issues), report.hasErrors());
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
                        Resource previous = resources.put(key, resource);
                        if (previous != null) {
                            assertTrue("Duplicate clinical resource " + key, resource instanceof Medication);
                            assertTrue("Conflicting shared medication " + key, previous.equalsDeep(resource));
                        }
                    }
                }
            }
        }
        return resources;
    }
    private String identifier(Resource resource, String system) {
        for (Property property : resource.children()) if (property.getName().equals("identifier"))
            for (Base value : property.getValues()) if (system.equals(((Identifier)value).getSystem())) return ((Identifier)value).getValue();
        return null;
    }
    private List<Reference> references(Base base) {
        List<Reference> refs = new ArrayList<>();
        if (base instanceof Reference) refs.add((Reference)base);
        else if (!(base instanceof PrimitiveType<?>)) for (Property property : base.children())
            for (Base value : property.getValues()) refs.addAll(references(value));
        return refs;
    }
    private String subject(Resource resource) {
        for (Property property : resource.children()) if (property.getName().equals("subject"))
            for (Base value : property.getValues()) return ((Reference)value).getReferenceElement().getIdPart();
        return null;
    }
    @Test public void repeatedNumericAndUuidPatientsKeepDistinctObjectsSharedCasesAndShiftedReferences() throws Exception {
        var output = run("");
        Map<String, Integer> shifts = new HashMap<>();
        for (String patient : PATIENTS) for (int iteration = 0; iteration < 3; iteration++)
            shifts.put("site-" + patient + "-x" + (iteration == 0 ? "" : "-rep-" + iteration), 10 - 7 * iteration);
        Set<String> actualPatients = new HashSet<>(), objectIds = new HashSet<>();
        Map<String, Integer> cases = new HashMap<>();
        int events = 0, encounters = 0;
        for (Resource resource : output.values()) {
            String objectId = identifier(resource, "urn:object");
            if (!(resource instanceof Medication)) {
                assertNotNull(objectId); assertTrue("Duplicate object identifier " + objectId, objectIds.add(objectId));
            }
            String patient = resource instanceof Patient ? resource.getIdElement().getIdPart() : subject(resource);
            if (resource instanceof Patient) {
                actualPatients.add(patient);
                assertEquals(LocalDate.parse("2000-02-29").plusDays(shifts.get(patient)).toString(),
                        ((Patient)resource).getBirthDateElement().getValueAsString());
                assertNull(identifier(resource, "urn:case"));
            } else if (!(resource instanceof Medication)) {
                assertTrue(shifts.containsKey(patient));
                String caseId = identifier(resource, "urn:case"); assertNotNull(caseId);
                assertTrue(caseId.equals(patient + "-1") || caseId.equals(patient + "-2"));
                cases.merge(caseId, 1, Integer::sum);
                String number = caseId.substring(caseId.length() - 1);
                LocalDate start = LocalDate.parse("2026-0" + number + "-01").plusDays(shifts.get(patient));
                if (resource instanceof Encounter) {
                    encounters++;
                    assertEquals(start.toString(), ((Encounter)resource).getPeriod().getStartElement().getValueAsString());
                    assertEquals(start.plusDays(9).toString(), ((Encounter)resource).getPeriod().getEndElement().getValueAsString());
                } else {
                    events++;
                    DateTimeType time = resource instanceof Condition ? ((Condition)resource).getRecordedDateElement()
                            : resource instanceof MedicationAdministration ? ((MedicationAdministration)resource).getEffectiveDateTimeType()
                            : ((MedicationStatement)resource).getEffectiveDateTimeType();
                    assertEquals(start.plusDays(1).toString(), time.getValueAsString());
                    Reference encounter = resource instanceof Condition ? ((Condition)resource).getEncounter()
                            : resource instanceof MedicationAdministration ? ((MedicationAdministration)resource).getContext()
                            : ((MedicationStatement)resource).getContext();
                    assertTrue(encounter.hasReference());
                }
            }
            for (Reference reference : references(resource)) if (reference.hasReference()) {
                Resource target = output.get(reference.getReference());
                assertNotNull("Missing reference " + reference.getReference(), target);
                if (target instanceof Patient) assertEquals(patient, target.getIdElement().getIdPart());
                else if (!(target instanceof Medication)) {
                    assertEquals(patient, subject(target));
                    assertEquals(identifier(resource, "urn:case"), identifier(target, "urn:case"));
                }
            }
        }
        assertEquals(shifts.keySet(), actualPatients); assertEquals(24, encounters); assertEquals(36, events);
        assertEquals(12, cases.size()); assertTrue(cases.values().stream().allMatch(count -> count == 5));
        assertEquals(66, objectIds.size());
        for (int i = 500; i < 566; i++) assertTrue(objectIds.contains(Integer.toString(i)));
        assertEquals(12, report.medicationTransformations.size());
        assertTrue(report.encounterReferenceIssues.isEmpty()); assertTrue(report.diagnosisReferenceIssues.isEmpty());
        var repeat = run(""); assertEquals(output.keySet(), repeat.keySet());
        for (String key : output.keySet()) assertTrue("Unstable resource " + key, output.get(key).equalsDeep(repeat.get(key)));
    }
    @Test public void deselectionKeepsAllocatedIdentifiersStableAcrossAllRepetitions() throws Exception {
        var baseline = run("");
        var selected = run("CONDITION_ENABLED=false\nCONTACT_DEPARTMENT_ENABLED=false\nMEDICATION_STATEMENT_ENABLED=false\n"
                + "REFERENCE_MEDICATION_ADMINISTRATION_ENCOUNTER=facility\n");
        int checked = 0;
        for (var entry : selected.entrySet()) {
            Resource resource = entry.getValue();
            assertNotNull(baseline.get(entry.getKey()));
            assertEquals(identifier(baseline.get(entry.getKey()), "urn:object"), identifier(resource, "urn:object"));
            assertEquals(identifier(baseline.get(entry.getKey()), "urn:case"), identifier(resource, "urn:case"));
            assertFalse(resource instanceof Condition || resource instanceof MedicationStatement);
            for (Reference ref : references(resource)) if (ref.hasReference()) assertTrue(selected.containsKey(ref.getReference()));
            if (resource instanceof MedicationAdministration) { assertTrue(((MedicationAdministration)resource).hasContext()); checked++; }
        }
        assertEquals(12, checked);
        assertTrue(report.medicationTransformations.isEmpty());
    }
}
