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
    public void contactPreflightCollectsIndependentRowsAndOtherSheets() throws Exception {
        java.nio.file.Path file = java.nio.file.Files.createTempFile("contact-input-errors-", ".xlsx");
        try {
            try (var input = new FileInputStream("FHIR_Testdatengenerator_Vorlage.xlsx");
                    var book = new XSSFWorkbook(input)) {
                var fall = book.getSheet("Fall");
                fall.getRow(1).getCell(2).setCellValue("2026-01-03");
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
    public void optionsUseOnlyColumnAAndReportInvalidAndConflictingValues() throws Exception {
        for (String mode : List.of("columnB", "invalid", "duplicate", "false")) {
            var file = java.nio.file.Files.createTempFile("option-input-", ".xlsx");
            try {
                try (var input = new FileInputStream("FHIR_Testdatengenerator_Vorlage.xlsx");
                        var book = new XSSFWorkbook(input)) {
                    book.getSheet("Person").getRow(1).getCell(0).setCellValue("");
                    var sheet = book.getSheet("Konvertierungsoptionen");
                    for (var row : sheet) {
                        var cell = row.getCell(0);
                        if (cell != null && cell.toString().startsWith("VALIDATE_STRICT")) cell.setCellValue("# Default");
                    }
                    sheet.createRow(150).createCell(mode.equals("columnB") ? 1 : 0)
                            .setCellValue("VALIDATE_STRICT=" + (mode.equals("invalid") ? "treu" : "false"));
                    if (mode.equals("duplicate")) sheet.createRow(151).createCell(0).setCellValue("VALIDATE_STRICT=true");
                    if (mode.equals("invalid")) sheet.createRow(151).createCell(0).setCellValue("START_ID_CONDITION=abc");
                    try (var output = java.nio.file.Files.newOutputStream(file)) { book.write(output); }
                }
                var result = new ExcelTemplateValidator().validate(file.toFile());
                if (mode.equals("false")) assertFalse(result.getIssues().toString(), result.hasErrors());
                else assertTrue(mode, result.hasErrors());
                if (mode.equals("columnB")) assertTrue(result.getIssues().stream().anyMatch(i -> i.getSheetName().equals("Person")));
                if (mode.equals("invalid")) assertEquals(2, result.getIssues().stream()
                        .filter(i -> i.getSheetName().equals("Konvertierungsoptionen")).count());
                if (mode.equals("duplicate")) assertTrue(result.getIssues().stream()
                        .anyMatch(i -> i.getMessage().contains("widersprüchliche")));
            } finally { java.nio.file.Files.deleteIfExists(file); }
        }
    }

    @Test
    public void exportedOptionSheetRemainsPropertiesTextIncludingCommentsAndEmptyValues() throws Exception {
        var directory = java.nio.file.Files.createTempDirectory("options-export-");
        var source = directory.resolve("input.xlsx");
        var csv = directory.resolve("csv"); java.nio.file.Files.createDirectory(csv);
        try (var book = new XSSFWorkbook()) {
            var sheet = book.createSheet("Konvertierungsoptionen");
            String[] lines = {"# first, comment", "# second, comment", "PID_PREFIX=demo-", "PID_SUFFIX=", "VALIDATE_STRICT=false"};
            for (int i = 0; i < lines.length; i++) sheet.createRow(i).createCell(0).setCellValue(lines[i]);
            sheet.getRow(0).createCell(1).setCellValue("Ignored column");
            sheet.getRow(1).createCell(1).setCellValue("VALIDATE_STRICT=true");
            try (var output = java.nio.file.Files.newOutputStream(source)) { book.write(output); }
        }
        de.uni_leipzig.imise.utils.Excel2Csv.splitExcel(source.toFile(), null, csv.toFile());
        var config = csv.resolve("input_Konvertierungsoptionen.csv");
        var options = new de.uni_leipzig.life.csv2fhir.ConverterOptions(config.toString());
        assertTrue(options.getErrors().toString(), options.getErrors().isEmpty());
        assertFalse(options.is(de.uni_leipzig.life.csv2fhir.ConverterOptions.BooleanOption.VALIDATE_STRICT));
        assertEquals("demo-p1", options.getFullPID("p1"));
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
                assertEquals("Konvertierungsoptionen", book.getSheetName(0));
                var optionNames = new java.util.HashSet<String>();
                for (var optionRow : book.getSheet("Konvertierungsoptionen")) {
                    var cell = optionRow.getCell(0);
                    if (cell == null || cell.getCellType() != CellType.STRING) continue;
                    String value = cell.getStringCellValue().replaceFirst("^#\\s*", "").trim();
                    if (value.matches("[A-Z][A-Z_0-9]*\\s*=.*")) {
                        String optionName = value.split("=", 2)[0].trim();
                        assertTrue("Duplicate option: " + optionName, optionNames.add(optionName));
                        assertEquals("FFF2CC", ((org.apache.poi.xssf.usermodel.XSSFCellStyle) cell.getCellStyle()).getFillForegroundXSSFColor().getARGBHex().substring(2));
                    }
                }
                var supportedOptions = new java.util.HashSet<String>();
                for (var value : de.uni_leipzig.life.csv2fhir.ConverterOptions.BooleanOption.values()) supportedOptions.add(value.name());
                for (var value : de.uni_leipzig.life.csv2fhir.ConverterOptions.IntOption.values()) supportedOptions.add(value.name());
                for (var value : de.uni_leipzig.life.csv2fhir.ConverterOptions.StringOption.values()) supportedOptions.add(value.name());
                assertEquals(supportedOptions, optionNames);
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
                for (String formula : List.of("Codes!$W$30:$W$48", "Codes!$X$30:$X$41",
                        "Codes!$Y$30:$Y$41", "Codes!$Z$30:$Z$37", "Codes!$D$4:$D$13")) {
                    assertTrue(formula, diagnoses.getDataValidations().stream().map(DataValidation::getValidationConstraint)
                            .anyMatch(v -> formula.equals(v.getFormula1())));
                }
                assertEquals("SNOMED CT (Version nicht angegeben)", book.getSheet("Codes").getRow(29).getCell(22).getStringCellValue());
                assertEquals("ICD-10-GM 2026", book.getSheet("Codes").getRow(47).getCell(22).getStringCellValue());
                assertEquals("PZN", book.getSheet("Codes").getRow(29).getCell(47).getStringCellValue());
                assertTrue(book.getSheet("Medikation").getDataValidations().stream()
                        .map(DataValidation::getValidationConstraint)
                        .anyMatch(v -> "Codes!$AV$30:$AV$33".equals(v.getFormula1())));
                assertNull(book.getSheet("Allergie"));
                assertEquals("ATC 2026", book.getSheet("Codes").getRow(29).getCell(50).getStringCellValue());
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
