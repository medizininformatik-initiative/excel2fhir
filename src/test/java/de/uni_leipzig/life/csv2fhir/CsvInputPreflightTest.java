package de.uni_leipzig.life.csv2fhir;

import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CsvInputPreflightTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private Path directory() throws Exception { return temporary.newFolder().toPath(); }
    private void persons(Path directory, String name) throws Exception {
        Files.writeString(directory.resolve(name), "Patient-ID,Vorname,Nachname,Geschlecht,Geburtsdatum\np1,Test,Person,männlich,2000-01-01\n");
    }

    @Test public void usesConverterRulesForMultipleGroupsAndRowCounts() throws Exception {
        Path directory = directory();
        persons(directory, "A_Person.csv"); persons(directory, "B_Person.csv");
        var report = CsvInputPreflight.inspect(directory);
        assertTrue(report.toString(), report.get("valid").asBoolean());
        assertEquals(2, report.get("groups").size());
        assertEquals(2, report.get("sheets").size());
        try (var files = Files.list(directory)) { assertEquals(2, files.count()); }
    }

    @Test public void rejectsAmbiguousGroupsAndMissingHeaders() throws Exception {
        Path directory = directory();
        persons(directory, "A_Person.csv"); persons(directory, "A-Person.csv");
        assertFalse(CsvInputPreflight.inspect(directory).get("valid").asBoolean());
        Files.delete(directory.resolve("A-Person.csv"));
        Files.writeString(directory.resolve("A_Person.csv"), "Unexpected\nvalue\n");
        assertFalse(CsvInputPreflight.inspect(directory).get("valid").asBoolean());
    }
}
