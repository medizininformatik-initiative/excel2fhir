package de.uni_leipzig.imise.validate;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;

public class DiagnosisWorkbookTest {
    @Test
    public void reversedDiagnosisDatesPassInputConsistencyChecks() throws Exception {
        var file = java.nio.file.Files.createTempFile("reversed-diagnosis-", ".xlsx");
        try {
            try (var input = new FileInputStream("FHIR_Testdatengenerator_Vorlage.xlsx");
                    var book = new XSSFWorkbook(input)) {
                var sheet = book.getSheet("Diagnose");
                for (var cell : sheet.getRow(0)) {
                    String name = cell.getStringCellValue();
                    if (name.equals("Beginn") || name.equals("Ende"))
                        sheet.getRow(1).getCell(cell.getColumnIndex(), org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                                .setCellValue(name.equals("Beginn") ? "2026-05-05" : "2026-05-01");
                }
                try (var output = java.nio.file.Files.newOutputStream(file)) { book.write(output); }
            }
            var result = new ExcelTemplateValidator().validate(file.toFile(),
                    de.uni_leipzig.life.csv2fhir.ConverterOptions.fromText("CHECK_INPUT_CONSISTENCY=true"));
            assertFalse(result.getIssues().toString(), result.hasErrors());
        } finally { java.nio.file.Files.deleteIfExists(file); }
    }

    @Test
    public void contactPreflightCollectsIndependentRowsAndOtherSheets() throws Exception {
        java.nio.file.Path file = java.nio.file.Files.createTempFile("contact-input-errors-", ".xlsx");
        try {
            try (var input = new FileInputStream("FHIR_Testdatengenerator_Vorlage.xlsx");
                    var book = new XSSFWorkbook(input)) {
                var fall = book.getSheet("Fall");
                fall.getRow(1).getCell(2).setCellValue("unlesbar");
                fall.getRow(1).getCell(3).setCellValue("2026-01-01");
                fall.getRow(4).getCell(2).setCellValue("unlesbar");
                // Another sheet must still be checked, not hidden by Fall errors.
                book.getSheet("Person").getRow(1).getCell(0).setCellValue("");
                try (var output = java.nio.file.Files.newOutputStream(file)) { book.write(output); }
            }
            var result = new ExcelTemplateValidator().validate(file.toFile());
            assertTrue(result.hasErrors());
            assertTrue(result.getIssues().stream().anyMatch(i -> i.getSheetName().equals("Fall") && i.getRowNumber()==2));
            assertTrue(result.getIssues().stream().anyMatch(i -> i.getSheetName().equals("Fall") && i.getRowNumber()==5));
            assertTrue(result.getIssues().stream().anyMatch(i -> i.getSheetName().equals("Person")));
            assertTrue(result.getIssues().stream().noneMatch(i -> i.getMessage().contains("übergeordneten Aufenthalts") && i.getRowNumber()==3));
        } finally { java.nio.file.Files.deleteIfExists(file); }
    }

    @Test
    public void embeddedOptionsCannotDisableInputChecks() throws Exception {
        var file = java.nio.file.Files.createTempFile("option-input-", ".xlsx");
        try {
            try (var input = new FileInputStream("FHIR_Testdatengenerator_Vorlage.xlsx"); var book = new XSSFWorkbook(input)) {
                book.getSheet("Person").getRow(1).getCell(0).setCellValue("");
                book.createSheet("Konvertierungsoptionen").createRow(0).createCell(0).setCellValue("CHECK_INPUT_CONSISTENCY=false");
                try (var out = java.nio.file.Files.newOutputStream(file)) { book.write(out); }
            }
            assertTrue(new ExcelTemplateValidator().validate(file.toFile()).hasErrors());
        } finally { java.nio.file.Files.delete(file); }
    }

    @Test
    public void optionSheetsAreExcludedFromCsvExport() throws Exception {
        var directory = java.nio.file.Files.createTempDirectory("options-export-");
        var source = directory.resolve("input.xlsx");
        var csv = directory.resolve("csv"); java.nio.file.Files.createDirectory(csv);
        try (var book = new XSSFWorkbook()) {
            var sheet = book.createSheet("Konvertierungsoptionen");
            String[] lines = {"# first, comment", "# second, comment", "PID_PREFIX=demo-", "PID_SUFFIX=", "CHECK_INPUT_CONSISTENCY=false"};
            for (int i = 0; i < lines.length; i++) sheet.createRow(i).createCell(0).setCellValue(lines[i]);
            sheet.getRow(0).createCell(1).setCellValue("Ignored column");
            sheet.getRow(1).createCell(1).setCellValue("CHECK_INPUT_CONSISTENCY=true");
            try (var output = java.nio.file.Files.newOutputStream(source)) { book.write(output); }
        }
        de.uni_leipzig.imise.utils.Excel2Csv.splitExcel(source.toFile(), null, csv.toFile());
        var config = csv.resolve("input_Konvertierungsoptionen.csv");
        assertFalse(java.nio.file.Files.exists(config));
        try (var files = java.nio.file.Files.walk(directory)) {
            for (var path : files.sorted(java.util.Comparator.reverseOrder()).toList()) java.nio.file.Files.delete(path);
        }
    }

    @Test
    public void shippedWorkbooksMatchSchemaAndLinkSharedSelections() throws Exception {
        for (String name : List.of("FHIR_Testdatengenerator_Vorlage.xlsx", "FHIR_Testdatengenerator_Interpolar_Demo.xlsx")) {
            TemplateValidationResult result = new ExcelTemplateValidator().validate(new File(name));
            assertFalse(name + ": " + result.getIssues(), result.hasErrors());
            try (FileInputStream input = new FileInputStream(name);
                    XSSFWorkbook book = new XSSFWorkbook(input)) {
                assertNull(book.getSheet("Konvertierungsoptionen"));
                var person = book.getSheet("Person");
                assertEquals("Geburtsdatum", person.getRow(0).getCell(3).getStringCellValue());
                assertEquals("Straße", person.getRow(0).getCell(11).getStringCellValue());
                assertEquals("Postleitzahl", person.getRow(0).getCell(12).getStringCellValue());
                assertTrue(person.getRow(1).getCell(11).getStringCellValue().contains("1"));
                assertEquals("53121", person.getRow(1).getCell(12).getStringCellValue());
                assertEquals("Bonn", person.getRow(1).getCell(13).getStringCellValue());
                assertEquals("DE", person.getRow(1).getCell(15).getStringCellValue());
                var diagnoses = book.getSheet("Diagnose");
                assertEquals("Code", diagnoses.getRow(0).getCell(3).getStringCellValue());
                assertEquals("Codesystem", diagnoses.getRow(0).getCell(4).getStringCellValue());
                assertEquals("Typ", diagnoses.getRow(0).getCell(12).getStringCellValue());
                assertEquals(CellType.STRING, diagnoses.getRow(1).getCell(3).getCellType());
                assertEquals("@", diagnoses.getRow(1).getCell(3).getCellStyle().getDataFormatString());
                for (String formula : List.of("Codes!$W$30:$W$31", "Codes!$X$30:$X$41",
                        "Codes!$Y$30:$Y$41", "Codes!$Z$30:$Z$37", "Codes!$D$4:$D$13")) {
                    assertTrue(formula, diagnoses.getDataValidations().stream().map(DataValidation::getValidationConstraint)
                            .anyMatch(v -> formula.equals(v.getFormula1())));
                }
                assertEquals("SNOMED CT (Version nicht angegeben)", book.getSheet("Codes").getRow(29).getCell(22).getStringCellValue());
                assertEquals("ICD-10-GM", book.getSheet("Codes").getRow(30).getCell(22).getStringCellValue());
                assertEquals("PZN", book.getSheet("Codes").getRow(29).getCell(47).getStringCellValue());
                assertTrue(book.getSheet("Medikation").getDataValidations().stream()
                        .map(DataValidation::getValidationConstraint)
                        .anyMatch(v -> "Codes!$AV$30:$AV$33".equals(v.getFormula1())));
                assertNull(book.getSheet("Allergie"));
                assertEquals("ATC", book.getSheet("Codes").getRow(29).getCell(50).getStringCellValue());
                var encounters = book.getSheet("Fall");
                assertEquals("Aufnahmegrund (4. Stelle)", encounters.getRow(0).getCell(9).getStringCellValue());
                assertEquals("Kontaktart", encounters.getRow(0).getCell(10).getStringCellValue());
                assertEquals("Erklärung/Ausfüllhilfe", encounters.getRow(0).getCell(11).getStringCellValue());
                for (String formula : List.of("Codes!$BF$30:$BF$34")) {
                    assertTrue(encounters.getDataValidations().stream().map(DataValidation::getValidationConstraint)
                            .anyMatch(v -> formula.equals(v.getFormula1())));
                }
                assertTrue(encounters.getDataValidations().stream().map(DataValidation::getValidationConstraint)
                        .anyMatch(v -> "Codes!$AA$30:$AA$41".equals(v.getFormula1())));
                assertEquals("Notfall", book.getSheet("Codes").getRow(34).getCell(26).getStringCellValue());
            }
        }
    }
}
