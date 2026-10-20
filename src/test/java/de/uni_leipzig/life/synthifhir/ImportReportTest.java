package de.uni_leipzig.life.synthifhir;

import static org.junit.Assert.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.csv.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import com.google.gson.*;

public class ImportReportTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private Path input, output;
    @Before public void setup() throws Exception {
        input = temp.newFolder("input").toPath(); output = temp.newFolder("output").toPath();
    }
    private void table(TableIdentifier table, List<Map<String,String>> rows) throws Exception {
        Set<String> headers = new LinkedHashSet<>(table.getMandatoryColumnNames());
        for (var row : rows) headers.addAll(row.keySet());
        try (var writer = Files.newBufferedWriter(input.resolve("case_" + table + ".csv"));
                var csv = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
            csv.printRecord(headers);
            for (var row : rows) csv.printRecord(headers.stream().map(h -> row.getOrDefault(h, "")).toList());
        }
    }
    private Map<String,String> patient(String id) {
        return Map.of("Patient-ID",id,"Vorname","Test","Nachname","Person","Geburtsdatum","2000-01-01","Geschlecht","weiblich");
    }
    private Map<String,String> procedure(String id, String date) {
        return Map.of("Patient-ID",id,"Prozedurencode","123","Codesystem", "SNOMED CT (Version nicht angegeben)","Durchführungsbeginn",date);
    }
    private Csv2Fhir run() throws Exception {
        var converter = new Csv2Fhir(input.toFile(),output.toFile(),"case_",null,
                Files.exists(input.resolve("options.config")) ? new ConverterOptions(input.resolve("options.config").toString()) : ConverterOptions.fromText(""));
        converter.convertFiles(Integer.MAX_VALUE, OutputFileType.JSON);
        assertTrue(Files.isRegularFile(output.resolve("case.import.json")));
        return converter;
    }
    private String identifierConfig(String pattern) {
        return "CONFIGURATION_VERSION=1\nOUTPUT_PATIENTS_PER_FILE=1000\nIDENTIFIER_RULE_1_ID=a152e771-3d5a-4cb1-9866-35fa6d91fd83\n"
                + "IDENTIFIER_RULE_1_ENABLED=true\nIDENTIFIER_RULE_1_RESOURCES=Patient\n"
                + "IDENTIFIER_RULE_1_SYSTEM=urn:run-test\nIDENTIFIER_RULE_1_COUNT_START=500\n"
                + "IDENTIFIER_RULE_1_PATTERN=" + pattern + "\n";
    }

    @Test public void identifierAllocationSpansInputConversionsAndRepetitions() throws Exception {
        String config = identifierConfig("{count}") + "PID_LAST_NUMBER_INCREASE_LOOP_COUNT=1\n";
        var identifiers = new AdditionalIdentifiers(ConverterOptions.fromText(config).configuration());
        Set<String> values = new HashSet<>();
        for (String patient : List.of("p1", "p2")) {
            table(TableIdentifier.Person, List.of(patient(patient)));
            Path destination = temp.newFolder(patient).toPath();
            new Csv2Fhir(input.toFile(), destination.toFile(), "case_", null,
                    ConverterOptions.fromText(config), identifiers)
                    .convertFiles(Integer.MAX_VALUE, OutputFileType.JSON, OutputFileType.NDJSON);
            var bundle = JsonParser.parseString(Files.readString(destination.resolve("case.json"))).getAsJsonObject();
            for (var entry : bundle.getAsJsonArray("entry")) {
                var resource = entry.getAsJsonObject().getAsJsonObject("resource");
                if (!resource.get("resourceType").getAsString().equals("Patient")) continue;
                for (var identifier : resource.getAsJsonArray("identifier")) {
                    var id = identifier.getAsJsonObject();
                    if (id.get("system").getAsString().equals("urn:run-test"))
                        assertTrue(values.add(id.get("value").getAsString()));
                }
            }
        }
        assertEquals(Set.of("500", "501", "502", "503"), values);
    }

    @Test public void identifierCollisionsAreDetectedAcrossInputConversions() throws Exception {
        String config = identifierConfig("fixed");
        var identifiers = new AdditionalIdentifiers(ConverterOptions.fromText(config).configuration());
        table(TableIdentifier.Person, List.of(patient("p1")));
        new Csv2Fhir(input.toFile(), output.toFile(), "case_", null,
                ConverterOptions.fromText(config), identifiers).convertFiles(Integer.MAX_VALUE, OutputFileType.JSON);
        table(TableIdentifier.Person, List.of(patient("p2")));
        var next = new Csv2Fhir(input.toFile(), temp.newFolder("next"), "case_", null,
                ConverterOptions.fromText(config), identifiers);
        var error = assertThrows(IllegalArgumentException.class,
                () -> next.convertFiles(Integer.MAX_VALUE, OutputFileType.JSON));
        assertTrue(error.getMessage().contains("Identifier collision"));
        // An independent run starts with an independent identifier namespace/check.
        new Csv2Fhir(input.toFile(), temp.newFolder("independent"), "case_", null,
                ConverterOptions.fromText(config)).convertFiles(Integer.MAX_VALUE, OutputFileType.JSON);
    }

    @Test public void unexpectedConversionFailuresAreCollectedAndPartialOutputIsMarked() throws Exception {
        table(TableIdentifier.Person,List.of(patient("p.1"),patient("px1")));
        table(TableIdentifier.Prozedur,List.of(procedure("p.1","2026-01-01"),procedure("","invalid"),
                procedure("","2026-01-02"),procedure("px1","2026-01-03")));
        var converter=run(); var report=converter.getImportReport();
        assertTrue(converter.hasImportProblems());
        var stats=report.tables.get("Prozedur");
        assertEquals(4,stats.rowsRead); assertEquals(3,stats.processedRows); assertEquals(1,stats.failedRows);
        assertEquals(3,stats.successfulAttempts); assertEquals(1,stats.failedAttempts);
        assertEquals(0,stats.unprocessedRows);
        JsonObject bundle=JsonParser.parseString(Files.readString(output.resolve("case.json"))).getAsJsonObject();
        long procedures=bundle.getAsJsonArray("entry").asList().stream().filter(e->"Procedure".equals(e.getAsJsonObject().getAsJsonObject("resource").get("resourceType").getAsString())).count();
        assertEquals(3,procedures);
        assertEquals(Set.of("CONVERSION_ERROR"),new HashSet<>(report.issues.stream().map(i->i.category).toList()));
    }
    @Test public void inputErrorsPreventAnyBundleButStillWriteTheFullReport() throws Exception {
        table(TableIdentifier.Person,List.of(patient("p1")));
        Files.writeString(input.resolve("case_Prozedur.csv"),"Patient-ID\np1\n");
        var report=run().getImportReport();
        assertTrue(report.hasErrors()); assertTrue(report.tables.get("Prozedur").rejected);
        assertEquals(0,report.tables.get("Person").processedRows);
        assertFalse(Files.exists(output.resolve("case.json")));
        assertEquals(1,report.tables.get("Prozedur").unprocessedRows);
        Files.delete(input.resolve("case_Person.csv"));
        assertTrue(run().hasImportProblems());
    }
    @Test public void reversedContactPeriodIsWrittenWithoutImportError() throws Exception {
        table(TableIdentifier.Person, List.of(patient("p1")));
        table(TableIdentifier.Fall, List.of(Map.of("Patient-ID", "p1", "Fall-Nr", "1",
                "Start", "2026-01-02", "Ende", "2026-01-01", "Einrichtungskontaktklasse", "stationaer")));
        assertFalse(run().getImportReport().hasErrors());
        var bundle = JsonParser.parseString(Files.readString(output.resolve("case.json"))).getAsJsonObject();
        var contact = bundle.getAsJsonArray("entry").asList().stream()
                .map(e -> e.getAsJsonObject().getAsJsonObject("resource"))
                .filter(r -> r.get("resourceType").getAsString().equals("Encounter")).findFirst().orElseThrow();
        assertEquals("2026-01-02", contact.getAsJsonObject("period").get("start").getAsString());
        assertEquals("2026-01-01", contact.getAsJsonObject("period").get("end").getAsString());
    }

    @Test public void contactErrorsAcrossPatientsAreReportedBeforeAnyConversion() throws Exception {
        table(TableIdentifier.Person, List.of(patient("p1"), patient("p2")));
        table(TableIdentifier.Fall, List.of(
                Map.of("Patient-ID", "p1", "Fall-Nr", "1", "Start", "unreadable", "Ende", "2026-01-01", "Einrichtungskontaktklasse", "stationaer"),
                Map.of("Patient-ID", "p2", "Fall-Nr", "1", "Start", "2026-01-01", "Ende", "2026-01-03", "Einrichtungskontaktklasse", "stationaer"),
                Map.of("Patient-ID", "p2", "Fall-Nr", "1", "Start", "2026-01-02", "Station", "OP", "Kontaktart", "Operation")));
        var report = run().getImportReport();
        assertEquals(2, report.issues.size());
        assertEquals(Set.of(1L, 3L), new HashSet<>(report.issues.stream().map(i -> i.record).toList()));
        assertEquals(0, report.tables.get("Person").successfulAttempts);
        assertEquals(0, report.tables.get("Fall").successfulAttempts);
        assertFalse(Files.exists(output.resolve("case.json")));
        assertEquals("INCOMPLETE", report.status);
    }

    @Test public void repeatedOutputsKeepDistinctRowAndAttemptCounts() throws Exception {
        table(TableIdentifier.Person,List.of(patient("p1")));
        Files.writeString(input.resolve("options.config"),"CONFIGURATION_VERSION=1\nOUTPUT_PATIENTS_PER_FILE=1000\nPID_LAST_NUMBER_INCREASE_LOOP_COUNT = 1\n");
        var report=run().getImportReport(); assertFalse(report.hasErrors());
        assertEquals(1,report.tables.get("Person").processedRows);
        assertEquals(2,report.tables.get("Person").successfulAttempts);
    }
    @Test public void duplicateHeadersAreRejectedBeforeAmbiguousValuesAreRead() throws Exception {
        table(TableIdentifier.Person, List.of(patient("p1")));
        Files.writeString(input.resolve("case_Prozedur.csv"), "Patient-ID,Patient-ID\np1,p2\n");
        var report = run().getImportReport();
        assertEquals("DUPLICATE_COLUMNS", report.issues.get(0).category);
        assertTrue(report.tables.get("Prozedur").rejected);
    }

    @Test public void malformedRowsBreakPatientInheritance() throws Exception {
        table(TableIdentifier.Person, List.of(patient("p1")));
        table(TableIdentifier.Prozedur, List.of(procedure("p1", "2026-01-01"), procedure("", "2026-01-02")));
        Path file = input.resolve("case_Prozedur.csv");
        List<String> lines = Files.readAllLines(file);
        lines.add(2, "broken,row");
        Files.write(file, lines);
        var report = run().getImportReport();
        assertEquals(0, report.tables.get("Prozedur").processedRows);
        assertFalse(Files.exists(output.resolve("case.json")));
        assertEquals(2, report.tables.get("Prozedur").failedRows);
        assertEquals(Set.of("MALFORMED_RECORD", "MISSING_PATIENT"),
                new HashSet<>(report.issues.stream().map(i -> i.category).toList()));
    }
    @Test public void invalidOptionsAreCollectedBeforeAnyOutput() throws Exception {
        table(TableIdentifier.Person, List.of(patient("p1")));
        Files.writeString(input.resolve("options.config"),
                "CONFIGURATION_VERSION=1\nCHECK_INPUT_CONSISTENCY=treu\nPID_LAST_NUMBER_INCREASE_LOOP_COUNT=-1\nSTART_ID_CONDITION=not-a-number\n");
        var report = run().getImportReport();
        assertEquals(1, report.issues.size());
        assertTrue(report.issues.stream().allMatch(i -> i.category.equals("OPTION_ERROR")));
        assertFalse(Files.exists(output.resolve("case.json")));
        assertEquals(0, report.tables.get("Person").successfulAttempts);
    }

    @Test public void repetitionSuffixCollisionsAreRejectedBeforeOutput() throws Exception {
        table(TableIdentifier.Person, List.of(patient("p1"), patient("p1-rep-1")));
        Files.writeString(input.resolve("options.config"), "CONFIGURATION_VERSION=1\nOUTPUT_PATIENTS_PER_FILE=1000\nPID_LAST_NUMBER_INCREASE_LOOP_COUNT=1\n");
        var report = run().getImportReport();
        assertEquals("PATIENT_ID_COLLISION", report.issues.get(0).category);
        assertFalse(Files.exists(output.resolve("case.json")));
    }

    @Test public void copiesKeepPrefixSuffixAndDistinctPatientIds() throws Exception {
        table(TableIdentifier.Person, List.of(patient("p1"), patient("p2")));
        Files.writeString(input.resolve("options.config"),
                "CONFIGURATION_VERSION=1\nOUTPUT_PATIENTS_PER_FILE=1000\nPID_LAST_NUMBER_INCREASE_LOOP_COUNT=1\nPID_PREFIX=test-\nPID_SUFFIX=-x\n");
        assertFalse(run().hasImportProblems());
        var bundle = JsonParser.parseString(Files.readString(output.resolve("case_test--x.json"))).getAsJsonObject();
        var ids = bundle.getAsJsonArray("entry").asList().stream()
                .map(e -> e.getAsJsonObject().getAsJsonObject("resource").get("id").getAsString()).toList();
        assertEquals(Set.of("test-p1-x", "test-p2-x", "test-p1-x-rep-1", "test-p2-x-rep-1"), new HashSet<>(ids));
        assertEquals(4, ids.size());
    }

    @Test public void repeatedUuidPatientsHaveMatchingClinicalReferences() throws Exception {
        String uuid = "550e8400-e29b-41d4-a716-446655440000";
        table(TableIdentifier.Person, List.of(patient(uuid)));
        table(TableIdentifier.Prozedur, List.of(procedure(uuid, "2026-01-01")));
        Files.writeString(input.resolve("options.config"), "CONFIGURATION_VERSION=1\nOUTPUT_PATIENTS_PER_FILE=1000\nPID_LAST_NUMBER_INCREASE_LOOP_COUNT=1\n");
        assertFalse(run().hasImportProblems());
        var bundle = JsonParser.parseString(Files.readString(output.resolve("case.json"))).getAsJsonObject();
        Set<String> subjects = new HashSet<>();
        Set<String> patients = new HashSet<>();
        for (var entry : bundle.getAsJsonArray("entry")) {
            var resource = entry.getAsJsonObject().getAsJsonObject("resource");
            if (resource.get("resourceType").getAsString().equals("Patient")) patients.add("Patient/" + resource.get("id").getAsString());
            if (resource.get("resourceType").getAsString().equals("Procedure")) subjects.add(resource.getAsJsonObject("subject").get("reference").getAsString());
        }
        assertEquals(Set.of("Patient/" + uuid, "Patient/" + uuid + "-rep-1"), patients);
        assertEquals(patients, subjects);
    }

    @Test public void normalizedPatientIdCollisionsAreRejected() throws Exception {
        table(TableIdentifier.Person, List.of(patient("p_1"), patient("p-1")));
        assertEquals("PATIENT_ID_COLLISION", run().getImportReport().issues.get(0).category);
        assertFalse(Files.exists(output.resolve("case.json")));
    }
    @Test public void inheritedDiagnosesAreSerializedAsResolvableReferences() throws Exception {
        table(TableIdentifier.Person, List.of(patient("p1")));
        table(TableIdentifier.Fall, List.of(Map.of("Patient-ID", "p1", "Fall-Nr", "1", "Start", "2026-01-01",
                "Ende", "2026-01-03", "Einrichtungskontaktklasse", "stationaer",
                "Fachabteilung", "Allgemeine Chirurgie", "Station", "S1")));
        table(TableIdentifier.Diagnose, List.of(Map.of("Patient-ID", "p1", "Fall-Nr", "1", "Code", "I10.90",
                "Codesystem", "ICD-10-GM 2026", "Typ", "Hauptdiagnose", "Dokumentationszeitpunkt", "2026-01-02")));
        Files.writeString(input.resolve("options.config"), "CONFIGURATION_VERSION=1\nOUTPUT_PATIENTS_PER_FILE=1000\nCONTACT_DIAGNOSES_LEVELS=facility,department,ward-service\n");
        assertFalse(run().hasImportProblems());
        var bundle = JsonParser.parseString(Files.readString(output.resolve("case.json"))).getAsJsonObject();
        var encounters = bundle.getAsJsonArray("entry").asList().stream().map(e -> e.getAsJsonObject().getAsJsonObject("resource"))
                .filter(r -> r.get("resourceType").getAsString().equals("Encounter")).toList();
        assertEquals(3, encounters.size());
        for (var encounter : encounters) assertEquals("Condition/p1-E-1-C-1", encounter.getAsJsonArray("diagnosis")
                .get(0).getAsJsonObject().getAsJsonObject("condition").get("reference").getAsString());
    }

    @Test public void overflowingOutputCountIsRejectedBeforeIteration() throws Exception {
        table(TableIdentifier.Person, List.of(patient("p1")));
        Files.writeString(input.resolve("options.config"), "CONFIGURATION_VERSION=1\nOUTPUT_PATIENTS_PER_FILE=1000\nPID_LAST_NUMBER_INCREASE_LOOP_COUNT=2147483647\n");
        var report = run().getImportReport();
        assertEquals("OPTION_ERROR", report.issues.get(0).category);
        assertFalse(Files.exists(output.resolve("case.json")));
    }
}
