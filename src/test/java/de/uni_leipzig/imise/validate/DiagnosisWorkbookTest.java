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
    private static int column(org.apache.poi.ss.usermodel.Sheet sheet, String name) {
        for (var cell : sheet.getRow(0)) {
            if (name.equals(cell.getStringCellValue())) return cell.getColumnIndex();
        }
        throw new AssertionError("Missing field " + name + " on " + sheet.getSheetName());
    }

    private static org.apache.poi.ss.usermodel.Cell field(org.apache.poi.ss.usermodel.Sheet sheet, int row, String name) {
        return sheet.getRow(row).getCell(column(sheet, name), org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
    }

    private static String choice(XSSFWorkbook book, String list, int item) {
        var sheet = book.getSheet("Codes");
        for (var row : sheet) for (var cell : row) {
            if (cell.getCellType() == CellType.STRING && list.equals(cell.getStringCellValue()))
                return sheet.getRow(row.getRowNum() + item).getCell(cell.getColumnIndex()).getStringCellValue();
        }
        throw new AssertionError("Missing selection list " + list);
    }

    @Test
    public void reorderedFieldsPassButDuplicateFieldsFail() throws Exception {
        var file = java.nio.file.Files.createTempFile("reordered-fields-", ".xlsx");
        try {
            try (var input = new FileInputStream("FHIR_Testdatengenerator_Vorlage.xlsx"); var book = new XSSFWorkbook(input)) {
                var sheet = book.getSheet("Medikation");
                int system = column(sheet, "Dosiereinheitcodesystem");
                int text = column(sheet, "Dosierungstext");
                var formatter = new org.apache.poi.ss.usermodel.DataFormatter();
                for (var row : sheet) {
                    String a = formatter.formatCellValue(row.getCell(system));
                    String b = formatter.formatCellValue(row.getCell(text));
                    row.getCell(system, org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(b);
                    row.getCell(text, org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(a);
                }
                try (var output = java.nio.file.Files.newOutputStream(file)) { book.write(output); }
                var result = new ExcelTemplateValidator().validate(file.toFile());
                assertFalse(result.getIssues().toString(), result.hasErrors());
                field(sheet, 0, "Dosierungstext").setCellValue("Dosiereinheitcodesystem");
                try (var output = java.nio.file.Files.newOutputStream(file)) { book.write(output); }
                assertTrue(new ExcelTemplateValidator().validate(file.toFile()).hasErrors());
            }
        } finally { java.nio.file.Files.deleteIfExists(file); }
    }

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
                field(fall, 1, "Start").setCellValue("unlesbar");
                field(fall, 1, "Ende").setCellValue("2026-01-01");
                field(fall, 4, "Start").setCellValue("unlesbar");
                // Another sheet must still be checked, not hidden by Fall errors.
                field(book.getSheet("Person"), 1, "Patient-ID").setCellValue("");
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
                field(book.getSheet("Person"), 1, "Patient-ID").setCellValue("");
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
                assertEquals("Geburtsdatum", field(person, 0, "Geburtsdatum").getStringCellValue());
                assertEquals("Straße", field(person, 0, "Straße").getStringCellValue());
                assertEquals("Postleitzahl", field(person, 0, "Postleitzahl").getStringCellValue());
                assertTrue(field(person, 1, "Straße").getStringCellValue().contains("1"));
                assertEquals("53121", field(person, 1, "Postleitzahl").getStringCellValue());
                assertEquals("Bonn", field(person, 1, "Ort").getStringCellValue());
                assertEquals("DE", field(person, 1, "Land").getStringCellValue());
                var diagnoses = book.getSheet("Diagnose");
                assertEquals("Code", field(diagnoses, 0, "Code").getStringCellValue());
                assertEquals("Codesystem", field(diagnoses, 0, "Codesystem").getStringCellValue());
                assertEquals("Typ", field(diagnoses, 0, "Typ").getStringCellValue());
                assertEquals(CellType.STRING, field(diagnoses, 1, "Code").getCellType());
                assertEquals("@", field(diagnoses, 1, "Code").getCellStyle().getDataFormatString());
                for (String formula : List.of("Codes!$W$30:$W$31", "Codes!$X$30:$X$41",
                        "Codes!$Y$30:$Y$41", "Codes!$Z$30:$Z$37", "Codes!$D$4:$D$13")) {
                    assertTrue(formula, diagnoses.getDataValidations().stream().map(DataValidation::getValidationConstraint)
                            .anyMatch(v -> formula.equals(v.getFormula1())));
                }
                assertEquals("SNOMED CT (Version nicht angegeben)", choice(book, "Diagnosecodesystem", 1));
                assertEquals("ICD-10-GM", choice(book, "Diagnosecodesystem", 2));
                assertEquals("PZN", choice(book, "Medikamentencodes", 1));
                assertTrue(book.getSheet("Medikation").getDataValidations().stream()
                        .map(DataValidation::getValidationConstraint)
                        .anyMatch(v -> "Codes!$AV$30:$AV$33".equals(v.getFormula1())));
                assertEquals("Dosiereinheitcodesystem", field(book.getSheet("Medikation"), 0, "Dosiereinheitcodesystem").getStringCellValue());
                for (String sheetName : List.of("Laborbefund", "Klinische Dokumentation")) {
                    var measurements = book.getSheet(sheetName);
                    assertEquals("Einheitencodesystem", field(measurements, 0, "Einheitencodesystem").getStringCellValue());
                    assertTrue(measurements.getDataValidations().stream()
                            .anyMatch(v -> v.getRegions().getCellRangeAddresses()[0].isInRange(1, column(measurements, "Einheitencodesystem"))
                                    && "Codes!$BP$30".equals(v.getValidationConstraint().getFormula1()) && !v.getShowErrorBox()));
                }
                assertEquals("http://unitsofmeasure.org", choice(book, "Einheitencodesystem", 1));
                assertTrue(book.getSheet("Medikation").getDataValidations().stream()
                        .anyMatch(v -> v.getRegions().getCellRangeAddresses()[0].isInRange(1, column(book.getSheet("Medikation"), "Dosiereinheitcodesystem"))
                                && "Codes!$BP$30".equals(v.getValidationConstraint().getFormula1()) && !v.getShowErrorBox()));
                assertNull(book.getSheet("Allergie"));
                assertEquals("ATC", choice(book, "Impfstoffcodes", 1));
                var encounters = book.getSheet("Fall");
                assertEquals("Aufnahmegrund (4. Stelle)", field(encounters, 0, "Aufnahmegrund (4. Stelle)").getStringCellValue());
                assertEquals("Kontaktart", field(encounters, 0, "Kontaktart").getStringCellValue());
                assertEquals("Erklärung/Ausfüllhilfe", field(encounters, 0, "Erklärung/Ausfüllhilfe").getStringCellValue());
                for (String formula : List.of("Codes!$BF$30:$BF$34")) {
                    assertTrue(encounters.getDataValidations().stream().map(DataValidation::getValidationConstraint)
                            .anyMatch(v -> formula.equals(v.getFormula1())));
                }
                assertTrue(encounters.getDataValidations().stream().map(DataValidation::getValidationConstraint)
                        .anyMatch(v -> "Codes!$AA$30:$AA$41".equals(v.getFormula1())));
                assertEquals("Notfall", choice(book, "Aufnahmegrund (4. Stelle)", 6));
            }
        }
    }
    @Test public void customDiagnosisSystemsPassPreflightAndWorkbookListsAllowTyping() throws Exception {
        try (var book = new XSSFWorkbook()) {
            var sheet = book.createSheet("Diagnose"); var row = sheet.createRow(1);
            var columns = new java.util.HashMap<String, Integer>();
            String[] headers = {"Code", "Codesystem", "Zusatzcode", "Zusatzcodesystem"};
            String[] values = {"nonsense", "https://example.invalid/system", "other", "urn:example:extra"};
            for (int i = 0; i < headers.length; i++) { columns.put(headers[i], i); row.createCell(i).setCellValue(values[i]); }
            var result = new TemplateValidationResult();
            new ExcelTemplateValidator().validateDiagnosisSelections(row, columns, result);
            assertFalse(result.getIssues().toString(), result.hasErrors());
        }
        for (String file : List.of("FHIR_Testdatengenerator_Vorlage.xlsx", "input/FHIR_Testdatengenerator_Vorlage.xlsx",
                "FHIR_Testdatengenerator_Interpolar_Demo.xlsx")) {
            int checked = 0;
            try (var input = new FileInputStream(file); var book = new XSSFWorkbook(input)) {
                for (var sheet : book) {
                    if (sheet.getRow(0) == null) continue;
                    for (var cell : sheet.getRow(0)) {
                        if (cell.getCellType() != CellType.STRING || !cell.getStringCellValue().toLowerCase(java.util.Locale.ROOT).endsWith("codesystem")) continue;
                        for (var validation : sheet.getDataValidations()) for (var range : validation.getRegions().getCellRangeAddresses())
                            if (range.isInRange(1, cell.getColumnIndex())) {
                                assertFalse(file + " " + sheet.getSheetName() + " " + cell.getStringCellValue(), validation.getShowErrorBox()); checked++;
                            }
                    }
                }
            }
            assertTrue(file, checked >= 10);
        }
    }

}
