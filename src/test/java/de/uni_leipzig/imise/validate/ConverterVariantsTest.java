package de.uni_leipzig.imise.validate;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import de.uni_leipzig.imise.Excel2FhirMain;
import de.uni_leipzig.imise.utils.Excel2Csv;
import de.uni_leipzig.life.csv2fhir.ConverterOptionSet;
import de.uni_leipzig.life.csv2fhir.ConverterOptions;
import picocli.CommandLine;

public class ConverterVariantsTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private Path workbook() throws Exception {
        Path file = temp.getRoot().toPath().resolve("case.xlsx");
        try (var in = Files.newInputStream(Path.of("FHIR_Testdatengenerator_Vorlage.xlsx"));
                var book = new XSSFWorkbook(in)) {
            book.removeSheetAt(book.getSheetIndex("Konvertierungsoptionen"));
            for (String name : List.of("Konvertierungsoptionen_A", "B_Konvertierungsoptionen")) {
                var sheet = book.createSheet(name);
                sheet.createRow(0).createCell(0).setCellValue("# comment, with comma");
                sheet.createRow(1).createCell(0).setCellValue("SET_REFERENCE_FROM_CONDITION_TO_ENCOUNTER=" + name.endsWith("_A"));
                sheet.getRow(1).createCell(1).setCellValue("CHECK_INPUT_CONSISTENCY=invalid");
            }
            try (var out = Files.newOutputStream(file)) { book.write(out); }
        }
        return file;
    }

    @Test
    public void allOptionsSheetsExportAsTextAndProduceSeparateVariants() throws Exception {
        Path input = workbook();
        Path csv = temp.newFolder("csv").toPath();
        Excel2Csv.splitExcel(input.toFile(), null, csv.toFile());
        var sets = ConverterOptionSet.csv(csv.toFile(), "case-");
        assertEquals(2, sets.size());
        assertTrue(sets.stream().allMatch(s -> s.options().getErrors().isEmpty()));
        Path output = temp.newFolder("output").toPath();
        assertEquals(0, new CommandLine(new Excel2FhirMain()).execute("-f", input.toString(), "-o", output.toString()));
        Path run;
        try (var runs = Files.list(output)) { run = runs.findFirst().orElseThrow(); }
        for (var set : sets) {
            Path directory = run.resolve("fhir").resolve(set.directoryName());
            assertTrue(Files.isRegularFile(directory.resolve("patients.ndjson")));
            assertTrue(Files.isRegularFile(directory.resolve("case-.json")));
            assertTrue(Files.isRegularFile(run.resolve("details/options/case.xlsx").resolve(set.directoryName()).resolve("converter-options.config")));
        }
        var a = Files.readString(run.resolve("fhir/Konvertierungsoptionen_A/case-.json"));
        var b = Files.readString(run.resolve("fhir/B_Konvertierungsoptionen/case-.json"));
        var parser = de.uni_leipzig.life.csv2fhir.OutputFileType.JSON.getParser();
        var first = parser.parseResource(org.hl7.fhir.r4.model.Bundle.class, a);
        var second = parser.parseResource(org.hl7.fhir.r4.model.Bundle.class, b);
        assertTrue(first.getEntry().stream().map(e -> e.getResource())
                .filter(r -> r instanceof org.hl7.fhir.r4.model.Condition)
                .anyMatch(r -> ((org.hl7.fhir.r4.model.Condition) r).hasEncounter()));
        assertFalse(second.getEntry().stream().map(e -> e.getResource())
                .filter(r -> r instanceof org.hl7.fhir.r4.model.Condition)
                .anyMatch(r -> ((org.hl7.fhir.r4.model.Condition) r).hasEncounter()));
    }

    @Test
    public void preflightUsesAllSelectedSheetsAndExternalOptionsReplaceThem() throws Exception {
        Path file = workbook();
        try (var in = Files.newInputStream(file); var book = new XSSFWorkbook(in)) {
            book.getSheet("B_Konvertierungsoptionen").createRow(2).createCell(0).setCellValue("CHECK_INPUT_CONSISTENCY=invalid");
            try (var out = Files.newOutputStream(file)) { book.write(out); }
        }
        assertTrue(new ExcelTemplateValidator().validate(file.toFile()).hasErrors());
        Path options = temp.newFile("DIZ.config").toPath();
        Files.writeString(options, "PID_PREFIX=site-\n");
        Path output = temp.newFolder("external-output").toPath();
        assertEquals(0, new CommandLine(new Excel2FhirMain()).execute("-f", file.toString(), "-o", output.toString(),
                "--converter-options", options.toString()));
        Path run;
        try (var runs = Files.list(output)) { run = runs.findFirst().orElseThrow(); }
        assertTrue(Files.exists(run.resolve("fhir/case-site-.json")));
        var saved = new ConverterOptions(run.resolve("details/options/case.xlsx/DIZ/converter-options.config").toString());
        assertTrue(saved.is(ConverterOptions.BooleanOption.CHECK_INPUT_CONSISTENCY));
        assertFalse(saved.is(ConverterOptions.BooleanOption.SET_REFERENCE_FROM_CONDITION_TO_ENCOUNTER));
    }
    @Test
    public void outputGroupsOnlyMultipleInputsAndVariants() throws Exception {
        for (int inputs : List.of(1, 2)) {
            for (int variants : List.of(1, 2)) {
                Path root = temp.newFolder("layout-" + inputs + "-" + variants).toPath();
                Path input = Files.createDirectory(root.resolve("input"));
                Path output = Files.createDirectory(root.resolve("output"));
                for (int i = 1; i <= inputs; i++) {
                    Files.copy(Path.of("FHIR_Testdatengenerator_Vorlage.xlsx"), input.resolve("case" + i + ".xlsx"));
                }
                var args = new java.util.ArrayList<>(List.of("-i", input.toString(), "-o", output.toString()));
                for (int v = 1; v <= variants; v++) {
                    Path options = root.resolve("KDS-" + v + ".config");
                    Files.writeString(options, "PID_PREFIX=variant" + v + "-\n");
                    args.addAll(List.of("--converter-options", options.toString()));
                }
                assertEquals(0, new CommandLine(new Excel2FhirMain()).execute(args.toArray(String[]::new)));
                Path run;
                try (var runs = Files.list(output)) { run = runs.findFirst().orElseThrow(); }
                for (int v = 1; v <= variants; v++) {
                    for (int i = 1; i <= inputs; i++) {
                        Path directory = run.resolve("fhir");
                        if (variants > 1) directory = directory.resolve("KDS-" + v);
                        if (inputs > 1) directory = directory.resolve("case" + i + ".xlsx");
                        assertTrue(Files.exists(directory.resolve("case" + i + "-variant" + v + "-.json")));
                        assertEquals(10, Files.readAllLines(directory.resolve("patients.ndjson")).size());
                        try (var files = Files.list(directory)) {
                            assertEquals(2, files.count());
                        }
                    }
                }
            }
        }
    }

}
