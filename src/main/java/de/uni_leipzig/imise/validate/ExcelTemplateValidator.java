package de.uni_leipzig.imise.validate;

import static de.uni_leipzig.imise.validate.TemplateValidationIssue.Severity.ERROR;
import static de.uni_leipzig.imise.validate.TemplateValidationIssue.Severity.WARNING;
import static de.uni_leipzig.life.csv2fhir.ConverterOptions.BooleanOption.VALIDATE_STRICT;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Date;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hl7.fhir.r4.model.DateTimeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.uni_leipzig.imise.validate.TemplateValidationIssue.Severity;
import de.uni_leipzig.life.csv2fhir.ConverterOptions.BooleanOption;

/**
 * Validates the current Excel input template contract before converting it.
 */
public class ExcelTemplateValidator {

    private static final Logger LOG = LoggerFactory.getLogger(ExcelTemplateValidator.class);

    private static final String DATE_TIME_FORMAT = "dd.mm.yyyy hh:mm:ss";

    private static final Map<String, List<String>> EXPECTED_HEADERS = createExpectedHeaders();

    private final DataFormatter formatter = new DataFormatter(Locale.GERMANY);

    private FormulaEvaluator formulaEvaluator;

    public TemplateValidationResult validate(File excelFile) throws IOException {
        TemplateValidationResult result = new TemplateValidationResult();
        try (FileInputStream inputStream = new FileInputStream(excelFile);
                XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            formulaEvaluator = workbook.getCreationHelper().createFormulaEvaluator();
            boolean validateStrict = readBooleanOption(workbook, VALIDATE_STRICT);
            validateHeaders(workbook, result);
            if (!validateStrict) {
                LOG.info("Excel template strict validation is disabled by {}", VALIDATE_STRICT);
                log(result);
                return result;
            }
            Set<String> patientIds = validatePatients(workbook, result);
            Set<String> encounterIds = validateEncounters(workbook, result, patientIds);
            validateReferenceTables(workbook, result, patientIds, encounterIds);
        }
        log(result);
        return result;
    }

    private boolean readBooleanOption(XSSFWorkbook workbook, BooleanOption option) {
        String optionName = option.toString();
        XSSFSheet sheet = workbook.getSheet("Konvertierungsoptionen");
        if (sheet == null) {
            return option.getDefault();
        }
        for (Row row : sheet) {
            for (Cell cell : row) {
                String line = formatCell(cell).trim();
                if (line.startsWith("#") || !line.contains("=")) {
                    continue;
                }
                String[] keyValue = line.split("=", 2);
                if (optionName.equals(keyValue[0].trim())) {
                    return BooleanOption.isTrue(keyValue[1]);
                }
            }
        }
        return option.getDefault();
    }

    public void validateAndThrow(File excelFile) throws IOException {
        TemplateValidationResult result = validate(excelFile);
        if (result.hasErrors()) {
            throw new TemplateValidationException(result);
        }
    }

    private void validateHeaders(XSSFWorkbook workbook, TemplateValidationResult result) {
        for (Map.Entry<String, List<String>> entry : EXPECTED_HEADERS.entrySet()) {
            String sheetName = entry.getKey();
            XSSFSheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                add(result, ERROR, sheetName, 0, null, "Required sheet is missing");
                continue;
            }
            List<String> actualHeaders = readHeaders(sheet);
            if (!actualHeaders.equals(entry.getValue())) {
                add(result, ERROR, sheetName, 1, null,
                        "Header does not match current template schema. Expected " + entry.getValue() + " but found "
                                + actualHeaders);
            }
        }
    }

    private Set<String> validatePatients(XSSFWorkbook workbook, TemplateValidationResult result) {
        Set<String> patientIds = new HashSet<>();
        XSSFSheet sheet = workbook.getSheet("Person");
        if (sheet == null) {
            return patientIds;
        }
        Map<String, Integer> columns = columnIndexes(sheet);
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isEmptyDataRow(row, columns)) {
                continue;
            }
            String patientId = get(row, columns, "Patient-ID");
            if (isBlank(patientId)) {
                add(result, ERROR, "Person", rowIndex + 1, "Patient-ID", "Patient-ID is required");
                continue;
            }
            if (!patientIds.add(patientId)) {
                add(result, ERROR, "Person", rowIndex + 1, "Patient-ID", "Patient-ID must be unique");
            }
            validateDateValue(sheet, row, columns, "Geburtsdatum", result, false);
            validateDateValue(sheet, row, columns, "Datum Einwilligung", result, false);
        }
        return patientIds;
    }

    private Set<String> validateEncounters(XSSFWorkbook workbook, TemplateValidationResult result,
            Set<String> patientIds) {
        Set<String> encounterIds = new HashSet<>();
        XSSFSheet sheet = workbook.getSheet("Fall");
        if (sheet == null) {
            return encounterIds;
        }
        Map<String, Integer> columns = columnIndexes(sheet);
        String previousPatientId = null;
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isEmptyDataRow(row, columns)) {
                continue;
            }
            String patientId = get(row, columns, "Patient-ID");
            if (isBlank(patientId)) {
                patientId = previousPatientId;
            } else {
                previousPatientId = patientId;
            }
            if (isBlank(patientId)) {
                add(result, ERROR, "Fall", rowIndex + 1, "Patient-ID", "Patient-ID or previous Patient-ID is required");
            } else if (!patientIds.contains(patientId)) {
                add(result, ERROR, "Fall", rowIndex + 1, "Patient-ID", "Patient-ID does not exist in Person sheet");
            }

            DateTimeType start = validateDateTime(sheet, row, columns, "Start", result, true);
            DateTimeType end = validateDateTime(sheet, row, columns, "Ende", result, false);
            validateDateRange(result, "Fall", rowIndex + 1, "Start/Ende", start, end, ERROR);

            String encounterNumber = get(row, columns, "Fall-Nr");
            if (!isBlank(patientId) && !isBlank(encounterNumber)) {
                validateRequired(sheet, row, columns, "Einrichtungskontaktklasse", result);
                encounterIds.add(patientId + "|" + encounterNumber);
            }
        }
        return encounterIds;
    }

    private void validateReferenceTables(XSSFWorkbook workbook, TemplateValidationResult result, Set<String> patientIds,
            Set<String> encounterIds) {
        validateReferenceTable(workbook, result, patientIds, encounterIds, "Diagnose",
                List.of("Dokumentationszeitpunkt"), List.of());
        validateReferenceTable(workbook, result, patientIds, encounterIds, "Prozedur",
                List.of("Dokumentationszeitpunkt"), List.of());
        validateReferenceTable(workbook, result, patientIds, encounterIds, "Laborbefund",
                List.of("Zeitstempel (Abnahme)"), List.of());
        validateReferenceTable(workbook, result, patientIds, encounterIds, "Klinische Dokumentation",
                List.of("Zeitstempel"), List.of());
        validateReferenceTable(workbook, result, patientIds, encounterIds, "DocumentReference", List.of(), List.of());
        validateReferenceTable(workbook, result, patientIds, encounterIds, "Medikation",
                List.of("Zeitstempel", "Therapiestart", "Therapieende"),
                List.of(new DateRangeColumns("Therapiestart", "Therapieende")));
    }

    private void validateReferenceTable(XSSFWorkbook workbook, TemplateValidationResult result, Set<String> patientIds,
            Set<String> encounterIds, String sheetName, List<String> dateTimeColumns,
            List<DateRangeColumns> dateRangeColumns) {
        XSSFSheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            return;
        }
        Map<String, Integer> columns = columnIndexes(sheet);
        String previousPatientId = null;
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isEmptyDataRow(row, columns)) {
                continue;
            }
            String patientId = get(row, columns, "Patient-ID");
            if (isBlank(patientId)) {
                patientId = previousPatientId;
            } else {
                previousPatientId = patientId;
            }
            if (isBlank(patientId)) {
                add(result, ERROR, sheetName, rowIndex + 1, "Patient-ID",
                        "Patient-ID or previous Patient-ID is required");
            } else if (!patientIds.contains(patientId)) {
                add(result, ERROR, sheetName, rowIndex + 1, "Patient-ID",
                        "Patient-ID does not exist in Person sheet");
            }
            for (String encounterNumber : getEncounterNumbers(row, columns)) {
                if (!isBlank(patientId) && !encounterIds.contains(patientId + "|" + encounterNumber)) {
                    add(result, WARNING, sheetName, rowIndex + 1, "Fall-Nr",
                            "Fall-Nr does not exist for this Patient-ID in Fall sheet");
                }
            }
            Map<String, DateTimeType> parsedDateTimes = new HashMap<>();
            for (String columnName : dateTimeColumns) {
                parsedDateTimes.put(columnName, validateDateTime(sheet, row, columns, columnName, result, false));
            }
            for (DateRangeColumns rangeColumns : dateRangeColumns) {
                validateDateRange(result, sheetName, rowIndex + 1,
                        rangeColumns.startColumn + "/" + rangeColumns.endColumn,
                        parsedDateTimes.get(rangeColumns.startColumn), parsedDateTimes.get(rangeColumns.endColumn),
                        null);
            }
        }
    }

    private DateTimeType validateDateTime(XSSFSheet sheet, Row row, Map<String, Integer> columns, String columnName,
            TemplateValidationResult result, boolean required) {
        Cell cell = getCell(row, columns, columnName);
        String value = get(row, columns, columnName);
        if (isBlank(value)) {
            if (required) {
                add(result, ERROR, sheet.getSheetName(), row.getRowNum() + 1, columnName, "Value is required");
            }
            return null;
        }
        if (cell != null && cell.getCellStyle() != null && !isExpectedDateTimeFormat(cell)) {
            add(result, WARNING, sheet.getSheetName(), row.getRowNum() + 1, columnName,
                    "Cell format should be " + DATE_TIME_FORMAT + " but is "
                            + cell.getCellStyle().getDataFormatString());
        }
        if (isExcelDateCell(cell)) {
            return new DateTimeType(cell.getDateCellValue());
        }
        if (isExcelDateFormulaCell(cell)) {
            return new DateTimeType(evaluateDateFormula(cell));
        }
        try {
            return de.uni_leipzig.life.csv2fhir.utils.DateUtil.parseDateTimeType(value);
        } catch (Exception e) {
            add(result, ERROR, sheet.getSheetName(), row.getRowNum() + 1, columnName,
                    "Value is not a supported date/time: " + value);
            return null;
        }
    }

    private DateTimeType validateDateValue(XSSFSheet sheet, Row row, Map<String, Integer> columns, String columnName,
            TemplateValidationResult result, boolean required) {
        Cell cell = getCell(row, columns, columnName);
        String value = get(row, columns, columnName);
        if (isBlank(value)) {
            if (required) {
                add(result, ERROR, sheet.getSheetName(), row.getRowNum() + 1, columnName, "Value is required");
            }
            return null;
        }
        if (isExcelDateCell(cell)) {
            return new DateTimeType(cell.getDateCellValue());
        }
        if (isExcelDateFormulaCell(cell)) {
            return new DateTimeType(evaluateDateFormula(cell));
        }
        try {
            return de.uni_leipzig.life.csv2fhir.utils.DateUtil.parseDateTimeType(value);
        } catch (Exception e) {
            add(result, ERROR, sheet.getSheetName(), row.getRowNum() + 1, columnName,
                    "Value is not a supported date/time: " + value);
            return null;
        }
    }

    private void validateRequired(XSSFSheet sheet, Row row, Map<String, Integer> columns, String columnName,
            TemplateValidationResult result) {
        if (isBlank(get(row, columns, columnName))) {
            add(result, ERROR, sheet.getSheetName(), row.getRowNum() + 1, columnName,
                    columnName + " is required in strict validation mode");
        }
    }

    private void validateDateRange(TemplateValidationResult result, String sheetName, int rowNumber, String columnName,
            DateTimeType start, DateTimeType end, Severity equalSeverity) {
        if (start == null || end == null) {
            return;
        }
        if (end.getValue().before(start.getValue())) {
            add(result, ERROR, sheetName, rowNumber, columnName, "End must be after start");
        } else if (equalSeverity != null && end.getValue().equals(start.getValue())) {
            add(result, equalSeverity, sheetName, rowNumber, columnName, "End should be after start");
        }
    }

    private List<String> readHeaders(XSSFSheet sheet) {
        Row headerRow = sheet.getRow(0);
        List<String> headers = new ArrayList<>();
        if (headerRow == null) {
            return headers;
        }
        for (Cell cell : headerRow) {
            headers.add(formatter.formatCellValue(cell).trim());
        }
        for (int i = headers.size() - 1; i >= 0 && headers.get(i).isBlank(); i--) {
            headers.remove(i);
        }
        return headers;
    }

    private Map<String, Integer> columnIndexes(XSSFSheet sheet) {
        Map<String, Integer> columns = new HashMap<>();
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            return columns;
        }
        for (Cell cell : headerRow) {
            String header = formatter.formatCellValue(cell).trim();
            if (!header.isBlank()) {
                columns.put(header, cell.getColumnIndex());
            }
        }
        return columns;
    }

    private boolean isEmptyDataRow(Row row, Map<String, Integer> columns) {
        if (row == null) {
            return true;
        }
        for (Map.Entry<String, Integer> column : columns.entrySet()) {
            if ("Erklärung/Ausfüllhilfe".equals(column.getKey())) {
                continue;
            }
            Cell cell = row.getCell(column.getValue());
            if (!isBlank(formatCell(cell))) {
                return false;
            }
        }
        return true;
    }

    private String get(Row row, Map<String, Integer> columns, String columnName) {
        Cell cell = getCell(row, columns, columnName);
        return cell == null ? "" : formatCell(cell).trim();
    }

    private Cell getCell(Row row, Map<String, Integer> columns, String columnName) {
        Integer columnIndex = columns.get(columnName);
        if (row == null || columnIndex == null) {
            return null;
        }
        return row.getCell(columnIndex);
    }

    private boolean isExcelDateCell(Cell cell) {
        return cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell);
    }

    private boolean isExcelDateFormulaCell(Cell cell) {
        return cell != null && cell.getCellType() == CellType.FORMULA && DateUtil.isCellDateFormatted(cell);
    }

    private Date evaluateDateFormula(Cell cell) {
        CellValue cellValue = formulaEvaluator.evaluate(cell);
        return DateUtil.getJavaDate(cellValue.getNumberValue());
    }

    private boolean isExpectedDateTimeFormat(Cell cell) {
        String format = cell.getCellStyle().getDataFormatString();
        if (format == null) {
            return false;
        }
        String normalizedFormat = format.toLowerCase(Locale.ROOT)
                .replace("\\", "")
                .replace('/', '.')
                .replace("m/d/yy", "dd.mm.yyyy");
        return DATE_TIME_FORMAT.equals(normalizedFormat);
    }

    private List<String> getEncounterNumbers(Row row, Map<String, Integer> columns) {
        String value = get(row, columns, "Fall-Nr");
        if (isBlank(value)) {
            return List.of();
        }
        List<String> encounterNumbers = new ArrayList<>();
        for (String encounterNumber : value.split(",")) {
            String trimmedEncounterNumber = encounterNumber.trim();
            if (!trimmedEncounterNumber.isEmpty()) {
                encounterNumbers.add(trimmedEncounterNumber);
            }
        }
        return encounterNumbers;
    }

    private String formatCell(Cell cell) {
        return formatter.formatCellValue(cell, formulaEvaluator);
    }

    private void add(TemplateValidationResult result, Severity severity, String sheetName, int rowNumber,
            String columnName, String message) {
        result.add(new TemplateValidationIssue(severity, sheetName, rowNumber, columnName, message));
    }

    private void log(TemplateValidationResult result) {
        for (TemplateValidationIssue issue : result.getIssues()) {
            if (issue.isError()) {
                LOG.error(issue.toString());
            } else {
                LOG.warn(issue.toString());
            }
        }
        LOG.info("Excel template validation found {} error(s) and {} warning(s)", result.getErrorCount(),
                result.getWarningCount());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static Map<String, List<String>> createExpectedHeaders() {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Person", Arrays.asList("Patient-ID", "Vorname", "Nachname", "Anschrift", "Geburtsdatum",
                "Geschlecht", "Krankenkasse", "Datum Einwilligung", "PDAT Einwilligung",
                "KKDAT retro Einwilligung", "KKDAT Einwilligung", "BIOMAT Einwilligung",
                "BIOMAT Zusatz Einwilligung", "Erklärung/Ausfüllhilfe"));
        headers.put("Fall", Arrays.asList("Patient-ID", "Fall-Nr", "Start", "Ende", "Einrichtungskontaktklasse",
                "Fachabteilung", "Station", "Zimmer", "Bett", "Erklärung/Ausfüllhilfe"));
        headers.put("Laborbefund", Arrays.asList("Patient-ID", "Fall-Nr", "LOINC", "Parameter", "Messwert",
                "Einheit", "Zeitstempel (Abnahme)", "Erklärung/Ausfüllhilfe"));
        headers.put("Diagnose", Arrays.asList("Patient-ID", "Fall-Nr", "Bezeichner", "ICD",
                "Dokumentationszeitpunkt", "Typ", "Erklärung/Ausfüllhilfe"));
        headers.put("Prozedur", Arrays.asList("Patient-ID", "Fall-Nr", "Prozedurentext", "Prozedurencode",
                "Dokumentationszeitpunkt", "Erklärung/Ausfüllhilfe"));
        headers.put("Medikation", Arrays.asList("Patient-ID", "Fall-Nr", "Zeitstempel", "Medikationstyp",
                "Medikationsplanart", "Wirksubstanz aus Präparat/Handelsname", "ATC Code", "PZN Code", "ASK",
                "FHIR_UserSelected", "Darreichungsform", "Therapiestart", "Therapieende", "Einzeldosis", "Einheit",
                "Anzahl Dosen pro Tag", "Erklärung/Ausfüllhilfe"));
        headers.put("Klinische Dokumentation", Arrays.asList("Patient-ID", "Fall-Nr", "Bezeichner", "LOINC", "Wert",
                "Einheit", "Zeitstempel", "Erklärung/Ausfüllhilfe"));
        headers.put("DocumentReference", Arrays.asList("Patient-ID", "Fall-Nr", "Dateipfad", "Embed",
                "Erklärung/Ausfüllhilfe"));
        return headers;
    }

    private static class DateRangeColumns {

        private final String startColumn;
        private final String endColumn;

        private DateRangeColumns(String startColumn, String endColumn) {
            this.startColumn = Objects.requireNonNull(startColumn);
            this.endColumn = Objects.requireNonNull(endColumn);
        }
    }
}
