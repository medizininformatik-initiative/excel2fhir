package de.uni_leipzig.life.csv2fhir;

import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class WorkbookPreflightTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void readsStarterStructureAndRowsWithoutChangingBytes() throws Exception {
        Path input = Path.of("input/FHIR_Testdatengenerator_Vorlage.xlsx");
        byte[] original = Files.readAllBytes(input);
        var report = WorkbookPreflight.inspect(input);
        assertTrue(report.toString(), report.get("valid").asBoolean());
        assertTrue(report.get("sheets").size() > 1);
        boolean found = false;
        for (var sheet : report.get("sheets")) {
            if (sheet.get("name").asText().equals("Person")) {
                found = true;
                assertTrue(sheet.get("rows").asInt() > 0);
            }
        }
        assertTrue(found);
        assertArrayEquals(original, Files.readAllBytes(input));
    }

    @Test public void reportsUnsupportedSheetStructureBeforeConversion() throws Exception {
        Path input = temporary.newFile("unsupported.xlsx").toPath();
        try (var workbook = new XSSFWorkbook(); var output = Files.newOutputStream(input)) {
            workbook.createSheet("Person").createRow(0).createCell(0).setCellValue("Wrong heading");
            workbook.write(output);
        }
        var report = WorkbookPreflight.inspect(input);
        assertFalse(report.get("valid").asBoolean());
        assertTrue(report.get("errors").asInt() > 0);
        assertTrue(report.get("issues").size() > 0);
    }
}
