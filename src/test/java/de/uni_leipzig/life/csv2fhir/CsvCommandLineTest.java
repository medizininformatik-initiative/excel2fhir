package de.uni_leipzig.life.csv2fhir;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.hl7.fhir.r4.model.Bundle;

import de.uni_leipzig.imise.validate.FHIRValidator;

import picocli.CommandLine;

public class CsvCommandLineTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    private Path input, output;

    @Before
    public void setup() throws Exception {
        input = temp.newFolder("input").toPath();
        output = temp.newFolder("output").toPath();
        var values = new LinkedHashMap<String, String>();
        for (String name : new LinkedHashSet<>(TableIdentifier.Person.getMandatoryColumnNames()))
            values.put(name, "");
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

    private int run(String... validation) throws Exception {
        java.util.Set<Path> before;
        try (var runs = Files.list(output)) {
            before = runs.collect(java.util.stream.Collectors.toSet());
        }
        var args = new java.util.ArrayList<>(java.util.List.of("-i", input.toString(), "-o", output.toString()));
        args.addAll(java.util.List.of(validation));
        int code = new CommandLine(new Main()).execute(args.toArray(String[]::new));
        try (var runs = Files.list(output)) {
            output = runs.filter(p -> !before.contains(p)).findFirst().orElseThrow();
        }
        return code;
    }

    @Test
    public void successfulImportReturnsZeroWithoutValidation() throws Exception {
        assertEquals(0, run());
        assertTrue(Files.exists(output.resolve("fhir/case.json")));
        assertTrue(Files.readString(output.resolve("status.txt")).contains("NOT_VALIDATED"));
        assertTrue(Files.readString(output.resolve("fhir/patients.ndjson")).contains("p1"));
        Path previous = output;
        output = output.getParent();
        assertEquals(0, run());
        assertNotEquals(previous, output);
        assertTrue(Files.exists(previous.resolve("fhir/case.json")));
        assertTrue(Files.exists(input.resolve("case_Person.csv")));
    }

    @Test
    public void preflightFailureReturnsNonzeroAndReportWithoutBundle() throws Exception {
        Files.writeString(input.resolve("case_Konvertierungsoptionen.csv"), "CHECK_INPUT_CONSISTENCY=treu\n");
        assertEquals(1, run());
        assertTrue(Files.readString(output.resolve("details/reports/case.import.json")).contains("INCOMPLETE"));
        assertFalse(Files.exists(output.resolve("fhir/case.json")));
    }

    @Test
    public void validationFailureReturnsNonzeroAndKeepsBundle() throws Exception {
        // Test CLI status propagation without loading a second full profile set.
        // FHIRValidatorTest separately checks validation and raw report contents.
        FHIRValidator validator = mock(FHIRValidator.class);
        when(validator.hasValidationProblems()).thenReturn(true);
        Main command = new Main() {
            @Override
            FHIRValidator createValidator() {
                return validator;
            }
        };
        assertEquals(1, new CommandLine(command).execute("-i", input.toString(), "-o", output.toString(), "-v"));
        try (var runs = Files.list(output)) {
            output = runs.findFirst().orElseThrow();
        }
        assertTrue(Files.readString(output.resolve("fhir/case.json")).contains("p1"));
        verify(validator).validateAndWriteReport(any(Bundle.class),
                eq(output.resolve("details/pending/case_.validation.json").toFile()));
    }

    @Test
    public void multipleCsvSetsProduceOneNdjsonWithTheSamePatientResources() throws Exception {
        Files.writeString(input.resolve("other_Person.csv"),
                Files.readString(input.resolve("case_Person.csv")).replace("p1", "p2"));
        assertEquals(0, run());
        var parser = OutputFileType.JSON.getParser();
        var lines = Files.readAllLines(output.resolve("fhir/patients.ndjson"));
        assertEquals(2, lines.size());
        var ids = new java.util.HashSet<String>();
        for (String line : lines) {
            Bundle bundle = parser.parseResource(Bundle.class, line);
            ids.add(bundle.getEntryFirstRep().getResource().getIdElement().getIdPart());
        }
        assertEquals(java.util.Set.of("p1", "p2"), ids);
        try (var files = Files.walk(output.resolve("fhir"))) {
            assertEquals(2, files.filter(p -> p.toString().endsWith(".json")).count());
        }
    }

    @Test
    public void ambiguousCsvSetsFailBeforeCreatingARun() throws Exception {
        Files.copy(input.resolve("case_Person.csv"), input.resolve("case-Person.csv"));
        assertNotEquals(0, new CommandLine(new Main()).execute("-i", input.toString(), "-o", output.toString()));
        try (var runs = Files.list(output)) {
            assertEquals(0, runs.count());
        }
    }

    @Test
    public void validationFlagsHaveExplicitMeaning() {
        Main command = new Main();
        var cli = new CommandLine(command);
        cli.parseArgs();
        assertFalse(command.validateBundles);
        cli.parseArgs("-v");
        assertTrue(command.validateBundles);
        cli.parseArgs("--no-validate-bundles");
        assertFalse(command.validateBundles);
    }

    @Test
    public void excelValidationIsExplicitlySelected() {
        var cli = new CommandLine(new de.uni_leipzig.imise.Excel2FhirMain());
        cli.parseArgs();
        assertEquals(false, cli.getCommandSpec().findOption("-v").getValue());
        cli.parseArgs("-v");
        assertEquals(true, cli.getCommandSpec().findOption("-v").getValue());
        cli.parseArgs("--no-validate-bundles");
        assertEquals(false, cli.getCommandSpec().findOption("-v").getValue());
    }

}
