package de.uni_leipzig.life.csv2fhir;

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
        var converter = new Csv2Fhir(input.toFile(),output.toFile(),"case_",null);
        converter.convertFiles(Integer.MAX_VALUE, OutputFileType.JSON);
        assertTrue(Files.isRegularFile(output.resolve("case.import.json")));
        return converter;
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
    @Test public void contactErrorsAcrossPatientsAreReportedBeforeAnyConversion() throws Exception {
        table(TableIdentifier.Person, List.of(patient("p1"), patient("p2")));
        table(TableIdentifier.Fall, List.of(
                Map.of("Patient-ID", "p1", "Fall-Nr", "1", "Start", "2026-01-02", "Ende", "2026-01-01", "Einrichtungskontaktklasse", "stationaer"),
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
        Files.writeString(input.resolve("case_Konvertierungsoptionen.csv"),"PID_LAST_NUMBER_INCREASE_LOOP_COUNT = 1\n");
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
}
