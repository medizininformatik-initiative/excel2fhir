package de.uni_leipzig.life.csv2fhir;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.uni_leipzig.imise.validate.ExcelTemplateValidator;

/** Read-only structural inspection of uploaded workbooks using the shared validator. */
public final class WorkbookPreflight {
    private WorkbookPreflight() {}

    public static ObjectNode inspect(Path path) throws Exception {
        var checked = new ExcelTemplateValidator().validate(path.toFile(),
                ConverterOptions.fromText("CHECK_INPUT_CONSISTENCY=false\n"));
        var result = new ObjectMapper().createObjectNode();
        result.put("valid", !checked.hasErrors());
        result.put("errors", checked.getErrorCount());
        result.put("warnings", checked.getWarningCount());
        var issues = result.putArray("issues");
        checked.getIssues().stream().limit(100).forEach(issue -> issues.add(issue.toString()));
        var sheets = result.putArray("sheets");
        DataFormatter formatter = new DataFormatter();
        try (var input = Files.newInputStream(path); var workbook = new XSSFWorkbook(input)) {
            for (var sheet : workbook) {
                int rows = 0;
                for (var row : sheet) {
                    if (row.getRowNum() == 0) continue;
                    boolean nonempty = false;
                    for (var cell : row) {
                        if (!formatter.formatCellValue(cell).isBlank()) { nonempty = true; break; }
                    }
                    if (nonempty) rows++;
                }
                sheets.addObject().put("name", sheet.getSheetName()).put("rows", rows);
            }
        }
        return result;
    }

    public static void main(String[] args) throws Exception {
        Files.writeString(Path.of(args[1]), inspect(Path.of(args[0])).toString());
    }
}
