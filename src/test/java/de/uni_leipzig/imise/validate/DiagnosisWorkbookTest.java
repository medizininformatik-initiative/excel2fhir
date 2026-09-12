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
