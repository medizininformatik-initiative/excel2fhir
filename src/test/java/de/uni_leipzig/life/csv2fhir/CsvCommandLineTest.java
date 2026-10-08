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

    @Test
    public void defaultOutputContainsOnePatientPerJsonBundle() throws Exception {
        Path source = input.resolve("case_Person.csv");
        var rows = Files.readAllLines(source);
        Files.writeString(source, rows.get(1).replace("p1", "p2") + System.lineSeparator(),
                java.nio.file.StandardOpenOption.APPEND);
        assertEquals(0, new CommandLine(new Main()).execute("-i", input.toString(), "-o", output.toString()));
        try (var files = Files.walk(output)) {
            var bundles = files.filter(p -> p.toString().endsWith(".json")
                    && p.getParent().getFileName().toString().equals("fhir")).toList();
            assertEquals(2, bundles.size());
            for (var file : bundles) {
                Bundle bundle = OutputFileType.JSON.getParser().parseResource(Bundle.class, Files.readString(file));
                assertEquals(1, bundle.getEntry().stream()
                        .filter(entry -> entry.getResource() instanceof org.hl7.fhir.r4.model.Patient).count());
            }
        }
    }

    private int run(String... validation) throws Exception {
        java.util.Set<Path> before;
        try (var runs = Files.list(output)) {
            before = runs.collect(java.util.stream.Collectors.toSet());
        }
        var args = new java.util.ArrayList<>(java.util.List.of("-i", input.toString(), "-o", output.toString()));
        if (!java.util.List.of(validation).contains("-p")) args.addAll(java.util.List.of("-p", "1000"));
        args.addAll(java.util.List.of(validation));
        int code = new CommandLine(new Main()).execute(args.toArray(String[]::new));
        try (var runs = Files.list(output)) {
            output = runs.filter(p -> !before.contains(p)).findFirst().orElse(output);
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
        assertEquals(1, run("--converter-options", input.resolve("case_Konvertierungsoptionen.csv").toString()));
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
        assertEquals(1, new CommandLine(command).execute("-i", input.toString(), "-o", output.toString(), "-p", "1000", "-v"));
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
        assertNotEquals(0, new CommandLine(new Main()).execute("-i", input.toString(), "-o", output.toString(), "-p", "1000"));
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

    @Test public void multipleExternalConfigurationsAreRejectedBeforeRun() throws Exception {
        assertEquals(1, run("--converter-options", "a.config", "--converter-options", "b.config"));
        try (var files = Files.list(output)) { assertEquals(0, files.count()); }
    }

    @Test public void embeddedOptionsAreIgnored() throws Exception {
        Files.writeString(input.resolve("case_Konvertierungsoptionen_A.csv"), "PID_PREFIX=A-\n");
        Files.writeString(input.resolve("case_Konvertierungsoptionen_B.csv"), "CHECK_INPUT_CONSISTENCY=invalid\n");
        assertEquals(0, run());
        assertTrue(Files.readString(output.resolve("fhir/case.json")).contains("p1"));
        assertFalse(Files.exists(output.resolve("fhir/Konvertierungsoptionen_A")));
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
        for (String name : java.util.List.of("")) {
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
        assertTrue(Files.readString(output.resolve("fhir/case_Person/case.json")).contains("p1"));
        assertTrue(Files.readString(output.resolve("fhir/case2_Person/case2.json")).contains("p2"));
        var a = new ConverterOptions(output.resolve("details/options/default/case_Person/converter-options.config").toString());
        var b = new ConverterOptions(output.resolve("details/options/default/case2_Person/converter-options.config").toString());
        assertEquals("p1", a.getFullPID("p1"));
        assertEquals("p2", b.getFullPID("p2"));
    }

    @Test
    public void multipleInputsAndVariantsKeepSeparatePatientStreams() throws Exception {
        Files.writeString(input.resolve("case2_Person.csv"),
                Files.readString(input.resolve("case_Person.csv")).replace("p1", "p2"));
        Path a = temp.newFile("KDS-A.config").toPath();
        Path b = temp.newFile("KDS-B.config").toPath();
        Files.writeString(a, "PID_PREFIX=A-\n");
        Files.writeString(b, "PID_PREFIX=B-\n");
        assertEquals(0, run("--converter-options", a.toString()));
        for (String variant : java.util.List.of("")) {
            for (String source : java.util.List.of("case", "case2")) {
                Path directory = output.resolve("fhir/" + variant + "/" + source + "_Person");
                var lines = Files.readAllLines(directory.resolve("patients.ndjson"));
                assertEquals(1, lines.size());
                assertTrue(lines.get(0).contains(source.equals("case") ? "p1" : "p2"));
            }
        }
    }

    @Test
    public void versionedConfigurationExecutesOutputAndTimeSettings() throws Exception {
        Path file = temp.newFile("web.config").toPath();
        Files.writeString(file, "CONFIGURATION_VERSION=1\nOUTPUT_FORMATS=NDJSON\n"
                + "TIME_SHIFT_ENABLED=true\nTIME_SHIFT_BASE_DAYS=1\n");
        assertEquals(0, run("--converter-options", file.toString()));
        assertFalse(Files.exists(output.resolve("fhir/case.json")));
        var bundle = OutputFileType.JSON.getParser().parseResource(Bundle.class,
                Files.readString(output.resolve("fhir/patients.ndjson")));
        var patient = (org.hl7.fhir.r4.model.Patient)bundle.getEntryFirstRep().getResource();
        assertEquals("2000-01-02", patient.getBirthDateElement().getValueAsString());
    }

    @Test public void configurationValidationSelectionOverridesLegacyCliDefault() throws Exception {
        Path file = temp.newFile("unvalidated.config").toPath();
        Files.writeString(file, "CONFIGURATION_VERSION=1\nCHECKS_FHIR_VALIDATION=false\n");
        FHIRValidator validator = mock(FHIRValidator.class);
        Main command = new Main() { @Override FHIRValidator createValidator() { return validator; } };
        assertEquals(0, new CommandLine(command).execute("-i", input.toString(), "-o", output.toString(),
                "--converter-options", file.toString(), "-v"));
        verifyNoInteractions(validator);
    }
    @Test public void versionedConfigurationShiftsBeforeCalendarEndAndReportsActualPolicy() throws Exception {
        var contact = new LinkedHashMap<String, String>();
        for (String name : new LinkedHashSet<>(TableIdentifier.Fall.getMandatoryColumnNames())) contact.put(name, "");
        contact.put("Patient-ID", "p1"); contact.put("Fall-Nr", "1");
        contact.put("Start", "2026-03-31"); contact.put("Ende", "2026-04-02");
        contact.put("Einrichtungskontaktklasse", "stationaer");
        try (var writer = Files.newBufferedWriter(input.resolve("case_Fall.csv"));
                var csv = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
            csv.printRecord(contact.keySet()); csv.printRecord(contact.values());
        }
        Path file = temp.newFile("shifted.config").toPath();
        Files.writeString(file, "CONFIGURATION_VERSION=1\nOUTPUT_FORMATS=NDJSON\nTIME_SHIFT_ENABLED=true\n"
                + "TIME_SHIFT_BASE_DAYS=1\nENCOUNTER_INPATIENT_END_POLICY=quarter-end\n");
        assertEquals(0, run("--converter-options", file.toString()));
        var bundle = OutputFileType.JSON.getParser().parseResource(Bundle.class,
                Files.readString(output.resolve("fhir/patients.ndjson")));
        var encounter = (org.hl7.fhir.r4.model.Encounter)bundle.getEntry().stream().map(e -> e.getResource())
                .filter(r -> r instanceof org.hl7.fhir.r4.model.Encounter).findFirst().orElseThrow();
        assertEquals("2026-04-01", encounter.getPeriod().getStartElement().getValueAsString());
        assertEquals("2026-06-30", encounter.getPeriod().getEndElement().getValueAsString());
        try (var paths = Files.walk(output.resolve("details/reports"))) {
            String report = Files.readString(paths.filter(p -> p.toString().endsWith(".import.json")).findFirst().orElseThrow());
            assertTrue(report.contains("timeShifts")); assertTrue(report.contains("quarter-end"));
        }
    }

    @Test public void nonzeroShiftRejectsPartialBirthDateWithoutPublishingFhir() throws Exception {
        Path person = input.resolve("case_Person.csv");
        Files.writeString(person, Files.readString(person).replace("2000-01-01", "2000"));
        Path file = temp.newFile("partial.config").toPath();
        Files.writeString(file, "CONFIGURATION_VERSION=1\nTIME_SHIFT_ENABLED=true\nTIME_SHIFT_BASE_DAYS=1\n");
        assertEquals(1, run("--converter-options", file.toString()));
        assertFalse(Files.exists(output.resolve("fhir")));
        assertTrue(Files.readString(output.resolve("status.txt")).contains("complete date"));
    }
    @Test public void defaultsCanBeExportedWithoutInputAndLoadedAsOneConfiguration() throws Exception {
        Path file = temp.getRoot().toPath().resolve("defaults.config");
        assertEquals(0, new CommandLine(new Main()).execute("--export-default-options", file.toString()));
        var options = ConverterOptions.fromText(Files.readString(file));
        assertTrue(options.getErrors().toString(), options.getErrors().isEmpty());
        assertNotNull(options.configuration());
        assertEquals(1, options.patientsPerFile(100));
        String original = Files.readString(file);
        assertEquals(1, new CommandLine(new Main()).execute("--export-default-options", file.toString()));
        assertEquals(original, Files.readString(file));
        assertEquals(0, run("--converter-options", file.toString()));
        assertTrue(Files.exists(output.resolve("fhir/patients.ndjson")));
    }
}
