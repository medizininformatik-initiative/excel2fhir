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
                for (String formula : List.of("Codes!$W$30:$W$48", "Codes!$X$30:$X$50",
                        "Codes!$Y$30:$Y$50", "Codes!$Z$30:$Z$44", "Codes!$D$4:$D$13")) {
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
                assertEquals("Kontakt-ID", encounters.getRow(0).getCell(10).getStringCellValue());
                assertEquals("Erklärung/Ausfüllhilfe", encounters.getRow(0).getCell(14).getStringCellValue());
                for (String formula : List.of("Codes!$BE$30:$BE$32", "Codes!$BF$30:$BF$34")) {
                    assertTrue(encounters.getDataValidations().stream().map(DataValidation::getValidationConstraint)
                            .anyMatch(v -> formula.equals(v.getFormula1())));
                }
                assertTrue(encounters.getDataValidations().stream().map(DataValidation::getValidationConstraint)
                        .anyMatch(v -> "Codes!$AA$30:$AA$50".equals(v.getFormula1())));
                assertEquals("Notfall", book.getSheet("Codes").getRow(34).getCell(26).getStringCellValue());
            }
        }
    }
}
