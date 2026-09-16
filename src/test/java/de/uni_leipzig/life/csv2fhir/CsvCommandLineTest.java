package de.uni_leipzig.life.csv2fhir;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import picocli.CommandLine;

public class CsvCommandLineTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private Path input, output;

    @Before public void setup() throws Exception {
        Main.validateBundles = false;
        Main.minLogLevel = null;
        input = temp.newFolder("input").toPath();
        output = input;
        var values = new LinkedHashMap<String, String>();
        for (String name : new LinkedHashSet<>(TableIdentifier.Person.getMandatoryColumnNames())) values.put(name, "");
        values.put("Patient-ID", "p1");
        values.put("Vorname", "Test");
        values.put("Nachname", "Person");
        values.put("Geburtsdatum", "2000-01-01");
        values.put("Geschlecht", "weiblich");
        try (var writer = Files.newBufferedWriter(input.resolve("case_Person.csv"));
                var csv = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
            csv.printRecord(values.keySet());
            csv.printRecord(values.values());
        }
    }

    @After public void resetOptions() {
        Main.validateBundles = false;
        Main.minLogLevel = null;
    }

    private int run(String validation) {
        return new CommandLine(new Main()).execute("-i", input.toString(), "-o", "case", validation);
    }

    @Test public void successfulImportReturnsZeroWithoutValidation() throws Exception {
        assertEquals(0, run("--no-validate-bundles"));
        assertTrue(Files.exists(output.resolve("case.json")));
    }

    @Test public void preflightFailureReturnsNonzeroAndReportWithoutBundle() throws Exception {
        Files.writeString(input.resolve("case_Konvertierungsoptionen.csv"), "VALIDATE_STRICT=treu\n");
        assertEquals(1, run("--no-validate-bundles"));
        assertTrue(Files.readString(output.resolve("case.import.json")).contains("INCOMPLETE"));
        assertFalse(Files.exists(output.resolve("case.json")));
    }

    @Test public void validationFailureReturnsNonzeroAndKeepsBundle() throws Exception {
        Files.writeString(input.resolve("case_Konvertierungsoptionen.csv"), "PID_PREFIX=invalid!\n");
        assertEquals(1, run("--validate-bundles"));
        assertTrue(Files.readString(output.resolve("case_invalid!.json")).contains("invalid!p1"));
        assertTrue(Files.readString(output.resolve("case_invalid!.validation.json")).contains("ERROR"));
    }
}
