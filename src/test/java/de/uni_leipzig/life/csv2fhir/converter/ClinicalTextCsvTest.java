package de.uni_leipzig.life.csv2fhir.converter;

import static org.junit.Assert.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import org.apache.commons.csv.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;
import de.uni_leipzig.imise.utils.Excel2Csv;

public class ClinicalTextCsvTest {
    @Test public void quotedMultilineAndLiteralEscapeTokensSurviveExcelCsv() throws Exception {
        Path dir = Files.createTempDirectory("clinical-csv-test");
        String text = "\n  Clinical note, with \\\"quotes\\\" and literal ~Q~\nSecond line\n";
        Path source = dir.resolve("case.xlsx");
        try {
            try (XSSFWorkbook book = new XSSFWorkbook(); OutputStream output = Files.newOutputStream(source)) {
                var sheet = book.createSheet("Text");
                sheet.createRow(0).createCell(0).setCellValue("Text");
                sheet.createRow(1).createCell(0).setCellValue(text);
                book.write(output);
            }
            Excel2Csv.splitExcel(source.toFile(), null, dir.toFile());
            try (Reader input = Files.newBufferedReader(dir.resolve("case_Text.csv"));
                    CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                        .setIgnoreSurroundingSpaces(true).setTrim(false).get().parse(input)) {
                assertEquals(text, parser.getRecords().get(0).get("Text"));
            }
        } finally {
            try (var files = Files.walk(dir)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
}
