package de.uni_leipzig.imise.utils;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class Excel2CsvTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void readOnlyWorkbookKeepsSourceBytesEvenAfterInMemoryChanges() throws Exception {
        File source = temp.newFile("read-only.xlsx");
        Files.copy(new File("FHIR_Testdatengenerator_Vorlage.xlsx").toPath(), source.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        byte[] original = Files.readAllBytes(source.toPath());
        try (var workbook = ExcelWorkbooks.openReadOnly(source)) {
            assertEquals(org.apache.poi.openxml4j.opc.PackageAccess.READ, workbook.getPackage().getPackageAccess());
            workbook.getSheet("Person").getRow(0).getCell(0).setCellValue("temporary evaluation value");
        }
        assertArrayEquals(original, Files.readAllBytes(source.toPath()));
    }

    @Test
    public void dottedDirectoryNamesAreUsedLiterally() throws Exception {
        File target = temp.newFolder("Fall.example.xlsx");
        Excel2Csv.splitExcel(new File("FHIR_Testdatengenerator_Vorlage.xlsx"), List.of("Person"), target);
        assertTrue(Files.isRegularFile(target.toPath().resolve("FHIR_Testdatengenerator_Vorlage_Person.csv")));
    }
    @Test
    public void sharedWorkbookValidationPreservesCsvAndReleasesEvaluator() throws Exception {
        File source = temp.newFile("cached-formulas.xlsx");
        try (var input = Files.newInputStream(new File("FHIR_Testdatengenerator_Vorlage.xlsx").toPath());
                var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(input)) {
            // Deliberately stale caches must survive validation, even when formulas
            // evaluate to different numbers for the consistency checks.
            for (var sheet : workbook) for (var row : sheet) for (var cell : row) {
                if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.FORMULA
                        && cell.getCachedFormulaResultType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
                    ((org.apache.poi.xssf.usermodel.XSSFCell) cell).getCTCell().setV("1");
                }
            }
            try (var output = Files.newOutputStream(source.toPath())) { workbook.write(output); }
        }
        byte[] original = Files.readAllBytes(source.toPath());
        File separate = temp.newFolder("separate");
        File shared = temp.newFolder("shared");
        Excel2Csv.splitExcel(source, null, separate);
        var validator = new de.uni_leipzig.imise.validate.ExcelTemplateValidator();
        try (var input = Files.newInputStream(source.toPath());
                var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(input)) {
            var formulas = new java.util.LinkedHashMap<org.apache.poi.xssf.usermodel.XSSFCell, String>();
            for (var sheet : workbook) for (var row : sheet) for (var cell : row) {
                if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.FORMULA) {
                    var formula = (org.apache.poi.xssf.usermodel.XSSFCell) cell;
                    formulas.put(formula, formula.getCTCell().xmlText());
                }
            }
            assertFalse("Fixture must exercise cached formulas", formulas.isEmpty());
            for (boolean check : List.of(true, false)) {
                var options = de.uni_leipzig.life.csv2fhir.ConverterOptions.fromText(
                        "CHECK_INPUT_CONSISTENCY=" + check + "\n");
                var expected = validator.validate(source, options, "test");
                var actual = validator.validate(workbook, options, "test");
                assertEquals(expected.getIssues().toString(), actual.getIssues().toString());
                var evaluator = validator.getClass().getDeclaredField("formulaEvaluator");
                evaluator.setAccessible(true);
                assertNull("Validator must not retain the workbook", evaluator.get(validator));
            }
            formulas.forEach((cell, xml) -> assertEquals(xml, cell.getCTCell().xmlText()));
            Excel2Csv.splitExcel(workbook, source, null, shared);
        }
        for (File file : separate.listFiles()) {
            assertArrayEquals(file.getName(), Files.readAllBytes(file.toPath()),
                    Files.readAllBytes(shared.toPath().resolve(file.getName())));
        }
        assertEquals(separate.list().length, shared.list().length);
        assertArrayEquals(original, Files.readAllBytes(source.toPath()));
    }

}
