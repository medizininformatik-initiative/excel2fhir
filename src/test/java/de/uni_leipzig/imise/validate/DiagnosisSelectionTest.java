package de.uni_leipzig.imise.validate;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;

public class DiagnosisSelectionTest {

    @Test
    public void rejectsMissingSystemsAndInvalidSelections() throws Exception {
        assertTrue(check(Map.of("Code", "00123")).hasErrors());
        assertTrue(check(Map.of("Klinischer Status", "unbekannter Status")).hasErrors());
        assertTrue(check(Map.of("Verifikationsstatus", "!dar:invented")).hasErrors());
        assertTrue(check(Map.of("Code", "A01", "Codesystem", "ICD-10-GM 2026",
                "Zusatzcode", "A02", "Zusatzcodesystem", "ICD-10-GM 2025")).hasErrors());
    }

    @Test
    public void permitsIntentionalInvalidCodesAndExplicitMissingData() throws Exception {
        assertFalse(check(Map.of("Code", "deliberately-invalid", "Codesystem", "ICD-10-GM 2026")).hasErrors());
        assertFalse(check(Map.of("Code", "!dar:masked", "Codesystem", "ICD-10-GM 2026",
                "Klinischer Status", "!dar:unknown")).hasErrors());
        assertFalse(check(Map.of()).hasErrors());
    }

    private TemplateValidationResult check(Map<String, String> input) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var row = workbook.createSheet("Diagnose").createRow(1);
            Map<String, Integer> columns = new LinkedHashMap<>();
            input.forEach((key, value) -> {
                int index = columns.size();
                columns.put(key, index);
                row.createCell(index).setCellValue(value);
            });
            TemplateValidationResult result = new TemplateValidationResult();
            new ExcelTemplateValidator().validateDiagnosisSelections(row, columns, result);
            return result;
        }
    }
}
