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
            try (XSSFWorkbook book = new XSSFWorkbook(new FileInputStream(name))) {
                var diagnoses = book.getSheet("Diagnose");
                assertEquals("Code", diagnoses.getRow(0).getCell(3).getStringCellValue());
                assertEquals("Codesystem", diagnoses.getRow(0).getCell(4).getStringCellValue());
                assertEquals("Typ", diagnoses.getRow(0).getCell(12).getStringCellValue());
                assertEquals(CellType.STRING, diagnoses.getRow(1).getCell(3).getCellType());
                assertEquals("@", diagnoses.getRow(1).getCell(3).getCellStyle().getDataFormatString());
                for (String formula : List.of("Codes!$W$4:$W$22", "Codes!$X$4:$X$24",
                        "Codes!$Y$4:$Y$24", "Codes!$Z$4:$Z$18", "Codes!$D$4:$D$13")) {
                    assertTrue(formula, diagnoses.getDataValidations().stream().map(DataValidation::getValidationConstraint)
                            .anyMatch(v -> formula.equals(v.getFormula1())));
                }
                assertEquals("SNOMED CT (Version nicht angegeben)", book.getSheet("Codes").getRow(3).getCell(22).getStringCellValue());
                assertEquals("ICD-10-GM 2026", book.getSheet("Codes").getRow(21).getCell(22).getStringCellValue());
            }
        }
    }
}
