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
        var lines = new java.util.ArrayList<String>();
        try (var files = Files.walk(output.resolve("fhir"))) {
            for (var file : files.filter(p -> p.toString().endsWith(".ndjson")).toList())
                lines.addAll(Files.readAllLines(file));
        }
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

    @Test
    public void externalDialectsReplaceEmbeddedOptionsAndKeepIdenticalIdsSeparate() throws Exception {
        Files.writeString(input.resolve("case_Konvertierungsoptionen.csv"), "CHECK_INPUT_CONSISTENCY=invalid\n");
        Path a = temp.newFile("DIZ-A.config").toPath();
        Path b = temp.newFile("DIZ-B.config").toPath();
        Files.writeString(a, "SET_REFERENCE_FROM_CONDITION_TO_ENCOUNTER=true\n");
        Files.writeString(b, "SET_REFERENCE_FROM_CONDITION_TO_ENCOUNTER=false\n");
        assertEquals(0, run("--converter-options", a.toString(), "--converter-options", b.toString()));
        for (String name : java.util.List.of("DIZ-A", "DIZ-B")) {
            assertTrue(Files.readString(output.resolve("fhir/" + name + "/case.json")).contains("p1"));
            assertTrue(Files.exists(output.resolve("fhir/" + name + "/patients.ndjson")));
            var snapshot = new ConverterOptions(output.resolve("details/options/" + name + "/converter-options.config").toString());
            assertEquals(name.equals("DIZ-A"), snapshot.is(ConverterOptions.BooleanOption.SET_REFERENCE_FROM_CONDITION_TO_ENCOUNTER));
            assertTrue(snapshot.is(ConverterOptions.BooleanOption.CHECK_INPUT_CONSISTENCY));
        }
        assertFalse(Files.exists(output.resolve("fhir/Konvertierungsoptionen")));
    }

    @Test
    public void embeddedDialectsProduceSeparateOutputs() throws Exception {
        Files.writeString(input.resolve("case_Konvertierungsoptionen_A.csv"), "PID_PREFIX=A-\n");
        Files.writeString(input.resolve("case_B_Konvertierungsoptionen.csv"), "PID_PREFIX=B-\n");
        assertEquals(0, run());
        assertTrue(Files.readString(output.resolve("fhir/Konvertierungsoptionen_A/case_A-.json")).contains("A-p1"));
        assertTrue(Files.readString(output.resolve("fhir/B_Konvertierungsoptionen/case_B-.json")).contains("B-p1"));
    }

    @Test
    public void missingOrAmbiguousExternalFilesFail() throws Exception {
        assertEquals(1, run("--converter-options", input.resolve("missing.config").toString()));
        assertFalse(Files.exists(output.resolve("fhir")));
        output = output.getParent();
        Path a = temp.newFolder("a").toPath().resolve("same.config");
        Path b = temp.newFolder("b").toPath().resolve("same.config");
        Files.writeString(a, ""); Files.writeString(b, "");
        assertEquals(1, run("--converter-options", a.toString(), "--converter-options", b.toString()));
        assertFalse(Files.exists(output.resolve("fhir")));
    }

    @Test
    public void patientSplittingKeepsNdjsonWithinEachVariant() throws Exception {
        Path source = input.resolve("case_Person.csv");
        var rows = Files.readAllLines(source);
        Files.writeString(source, Files.readString(source) + rows.get(1).replace("p1", "p2") + "\n");
        Files.writeString(input.resolve("case_Konvertierungsoptionen_A.csv"), "");
        Files.writeString(input.resolve("case_Konvertierungsoptionen_B.csv"), "");
        assertEquals(0, run("-p", "1"));
        for (String name : java.util.List.of("Konvertierungsoptionen_A", "Konvertierungsoptionen_B")) {
            var directory = output.resolve("fhir/" + name);
            assertEquals(2, Files.readAllLines(directory.resolve("patients.ndjson")).size());
            assertTrue(Files.exists(directory.resolve("case_P1.json")));
            assertTrue(Files.exists(directory.resolve("case_P2.json")));
        }
    }

    @Test
    public void overlappingDatasetNamesKeepTheirOwnOptionsAndSnapshots() throws Exception {
        Files.writeString(input.resolve("case2_Person.csv"),
                Files.readString(input.resolve("case_Person.csv")).replace("p1", "p2"));
        Files.writeString(input.resolve("case_Konvertierungsoptionen.csv"), "PID_PREFIX=A-\n");
        Files.writeString(input.resolve("case2_Konvertierungsoptionen.csv"), "PID_PREFIX=B-\n");
        assertEquals(0, run());
        assertTrue(Files.readString(output.resolve("fhir/case_Person/case_A-.json")).contains("A-p1"));
        assertTrue(Files.readString(output.resolve("fhir/case2_Person/case2_B-.json")).contains("B-p2"));
        var a = new ConverterOptions(output.resolve("details/options/Konvertierungsoptionen/case_Person/converter-options.config").toString());
        var b = new ConverterOptions(output.resolve("details/options/Konvertierungsoptionen/case2_Person/converter-options.config").toString());
        assertEquals("A-p1", a.getFullPID("p1"));
        assertEquals("B-p2", b.getFullPID("p2"));
    }

    @Test
    public void multipleInputsAndVariantsKeepSeparatePatientStreams() throws Exception {
        Files.writeString(input.resolve("case2_Person.csv"),
                Files.readString(input.resolve("case_Person.csv")).replace("p1", "p2"));
        Path a = temp.newFile("KDS-A.config").toPath();
        Path b = temp.newFile("KDS-B.config").toPath();
        Files.writeString(a, "PID_PREFIX=A-\n");
        Files.writeString(b, "PID_PREFIX=B-\n");
        assertEquals(0, run("--converter-options", a.toString(), "--converter-options", b.toString()));
        for (String variant : java.util.List.of("KDS-A", "KDS-B")) {
            for (String source : java.util.List.of("case", "case2")) {
                Path directory = output.resolve("fhir/" + variant + "/" + source + "_Person");
                var lines = Files.readAllLines(directory.resolve("patients.ndjson"));
                assertEquals(1, lines.size());
                assertTrue(lines.get(0).contains(source.equals("case") ? "p1" : "p2"));
            }
        }
    }

}
