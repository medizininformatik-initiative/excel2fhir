package de.uni_leipzig.imise.validate;

import static de.uni_leipzig.imise.validate.TemplateValidationIssue.Severity.ERROR;
import static de.uni_leipzig.imise.validate.TemplateValidationIssue.Severity.WARNING;
import static de.uni_leipzig.life.csv2fhir.ConverterOptions.BooleanOption.CHECK_INPUT_CONSISTENCY;

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
import org.hl7.fhir.r4.model.Coding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.uni_leipzig.imise.validate.TemplateValidationIssue.Severity;
import de.uni_leipzig.life.csv2fhir.ConverterOptions;
import de.uni_leipzig.life.csv2fhir.converter.DiagnosisValues;
import de.uni_leipzig.life.csv2fhir.converter.AdmissionReasonValues;

/**
 * Validates the current Excel input template contract before converting it.
 */
public class ExcelTemplateValidator {

    private static final Logger LOG = LoggerFactory.getLogger(ExcelTemplateValidator.class);

    private static final Map<String, List<String>> EXPECTED_HEADERS = createExpectedHeaders();

    private final DataFormatter formatter = new DataFormatter(Locale.GERMANY);

    private FormulaEvaluator formulaEvaluator;

    public TemplateValidationResult validate(File excelFile) throws IOException {
        TemplateValidationResult result = new TemplateValidationResult();
        for (var set : de.uni_leipzig.life.csv2fhir.ConverterOptionSet.workbook(excelFile)) {
            var checked = validate(excelFile, set.options(), set.name());
            if (checked.hasErrors()) return checked;
            result = checked;
        }
        return result;
    }

    public TemplateValidationResult validate(File excelFile, ConverterOptions options) throws IOException {
        return validate(excelFile, options, "Konvertierungsoptionen");
    }

    public TemplateValidationResult validate(File excelFile, ConverterOptions options, String optionSetName) throws IOException {
        TemplateValidationResult result = new TemplateValidationResult();
        try (FileInputStream inputStream = new FileInputStream(excelFile);
                XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            formulaEvaluator = workbook.getCreationHelper().createFormulaEvaluator();
            for (String error : options.getErrors()) add(result, ERROR, optionSetName, 0, "A", error);
            boolean checkInputConsistency = options.is(CHECK_INPUT_CONSISTENCY);
            validateHeaders(workbook, result);
            if (!checkInputConsistency) {
                LOG.info("Excel input consistency checks are disabled by {}", CHECK_INPUT_CONSISTENCY);
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
        var contacts = new de.uni_leipzig.life.csv2fhir.converter.ContactInputValidator();
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

            Map<String, String> contactValues = new HashMap<>();
            for (String column : columns.keySet()) contactValues.put(column, get(row, columns, column));
            for (String column : List.of("Start", "Ende")) {
                Cell cell = getCell(row, columns, column);
                if (isExcelDateCell(cell)) contactValues.put(column, new DateTimeType(cell.getDateCellValue()).getValueAsString());
                else if (isExcelDateFormulaCell(cell)) contactValues.put(column, new DateTimeType(evaluateDateFormula(cell)).getValueAsString());
            }
            for (var issue : contacts.accept(new de.uni_leipzig.life.csv2fhir.converter.ContactInputValidator.Input(
                    rowIndex + 1, patientId, contactValues))) {
                add(result, ERROR, "Fall", (int) issue.row(), issue.field(), issue.message());
            }
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
        for (String sheet : List.of("Impfung", "Befundbericht", "Behandlungsplan")) {
            validateReferenceTable(workbook, result, patientIds, encounterIds, sheet, List.of("Zeitpunkt", "Ende", "Ausgabezeitpunkt"), List.of());
        }
        validateReferenceTable(workbook, result, patientIds, encounterIds, "Diagnose",
                List.of("Dokumentationszeitpunkt", "Beginn", "Ende"), List.of(new DateRangeColumns("Beginn", "Ende")));
        validateReferenceTable(workbook, result, patientIds, encounterIds, "Prozedur",
                List.of("Durchführungsbeginn"), List.of());
        validateReferenceTable(workbook, result, patientIds, encounterIds, "Laborbefund",
                List.of("Zeitstempel (Abnahme)"), List.of());
        validateReferenceTable(workbook, result, patientIds, encounterIds, "Klinische Dokumentation",
                List.of("Zeitstempel"), List.of());
        validateReferenceTable(workbook, result, patientIds, encounterIds, "DocumentReference", List.of(), List.of());
        validateReferenceTable(workbook, result, patientIds, encounterIds, "Medikation",
                List.of("Dokumentationszeitpunkt", "Beginn", "Ende"),
                List.of(new DateRangeColumns("Beginn", "Ende")));
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
        Set<String> entryIds = new HashSet<>();
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
            if ("Diagnose".equals(sheetName)) {
                validateDiagnosisSelections(row, columns, result);
            }
            if ("Medikation".equals(sheetName)) {
                for (String error : de.uni_leipzig.life.csv2fhir.converter.MedicationValues.errors(key -> {
                    Cell cell = getCell(row, columns, key);
                    if (isExcelDateCell(cell)) return new DateTimeType(cell.getDateCellValue()).getValueAsString();
                    if (isExcelDateFormulaCell(cell)) return new DateTimeType(evaluateDateFormula(cell)).getValueAsString();
                    return get(row, columns, key);
                })) {
                    add(result, ERROR, sheetName, rowIndex + 1, "Medikation", error);
                }
            }
            if (("Laborbefund".equals(sheetName) || "laboratory".equals(get(row, columns, "Kategorie")))
                    && "Ja/Nein".equals(get(row, columns, "Werttyp"))) {
                add(result, ERROR, sheetName, rowIndex + 1, "Werttyp", "The KDS laboratory profile requires a coded answer for boolean results");
            }
            String idColumn = columns.containsKey("Eintrag ID") ? "Eintrag ID" : "Untersuchung ID";
            if (columns.containsKey(idColumn)) {
                String entryId = get(row, columns, idColumn);
                String parentId = get(row, columns, "Komponente von");
                if ("Eintrag ID".equals(idColumn) && isBlank(entryId)) {
                    add(result, ERROR, sheetName, rowIndex + 1, idColumn, "Entry ID is required");
                }
                if (!isBlank(parentId) && !entryIds.contains(patientId + "|" + parentId)) {
                    add(result, ERROR, sheetName, rowIndex + 1, "Komponente von", "Component parent must precede the component for the same patient");
                }
                if (!isBlank(entryId) && !entryIds.add(patientId + "|" + entryId)) {
                    add(result, ERROR, sheetName, rowIndex + 1, idColumn, "Duplicate entry ID for this patient");
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

    void validateDiagnosisSelections(Row row, Map<String, Integer> columns, TemplateValidationResult result) {
        Map<String, Coding> systems =
                DiagnosisValues.systems();
        String firstSystem = null;
        for (String[] pair : List.of(new String[] {"Code", "Codesystem"},
                new String[] {"Zusatzcode", "Zusatzcodesystem"})) {
            String code = get(row, columns, pair[0]);
            if (isBlank(code)) {
                continue;
            }
            String selection = get(row, columns, pair[1]);
            var coding = systems.get(selection);
            if (coding == null) {
                add(result, ERROR, "Diagnose", row.getRowNum() + 1, pair[1], "Explicit supported codesystem required");
            } else if (coding.getSystem().equals(firstSystem)) {
                add(result, ERROR, "Diagnose", row.getRowNum() + 1, pair[1], "Duplicate coding system exceeds profile slice");
            } else {
                firstSystem = coding.getSystem();
            }
            try {
                DiagnosisValues.absentReason(code);
            } catch (Exception e) {
                add(result, ERROR, "Diagnose", row.getRowNum() + 1, pair[0], "Unknown explicit data absent reason");
            }
        }
        Map<String, Map<String, String>> statuses = Map.of(
                "Klinischer Status", DiagnosisValues.CLINICAL,
                "Verifikationsstatus", DiagnosisValues.VERIFICATION);
        statuses.forEach((column, values) -> {
            String value = get(row, columns, column);
            if (!isBlank(value) && !values.containsKey(value)) {
                try {
                    if (DiagnosisValues.absentReason(value) == null) {
                        throw new IllegalArgumentException();
                    }
                } catch (Exception e) {
                    add(result, ERROR, "Diagnose", row.getRowNum() + 1, column, "Unsupported status or data absent reason");
                }
            }
        });
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
        if (isExcelDateCell(cell)) {
            return new DateTimeType(cell.getDateCellValue());
        }
        if (isExcelDateFormulaCell(cell)) {
            return new DateTimeType(evaluateDateFormula(cell));
        }
        try {
            if (DiagnosisValues.absentReason(value) != null) {
                return null;
            }
            if (value.matches("\\d{4}(-\\d{2}(-\\d{2})?)?(T.*)?")) {
                return new DateTimeType(value);
            }
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
                    columnName + " is required when input consistency checks are enabled");
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
        headers.put("Person", Arrays.asList("Patient-ID", "Vorname", "Nachname", "Geburtsdatum",
                "Geschlecht", "Datum Einwilligung", "PDAT Einwilligung",
                "KKDAT retro Einwilligung", "KKDAT Einwilligung", "BIOMAT Einwilligung",
                "BIOMAT Zusatz Einwilligung", "Straße", "Postleitzahl", "Ort", "Bundesland", "Land", "Sterbezeitpunkt", "Erklärung/Ausfüllhilfe"));
        headers.put("Fall", Arrays.asList("Patient-ID", "Fall-Nr", "Start", "Ende", "Einrichtungskontaktklasse",
                "Fachabteilung", "Station", "Zimmer", "Bett", AdmissionReasonValues.COLUMN,
                "Kontaktart", "Erklärung/Ausfüllhilfe"));
        headers.put("Laborbefund", Arrays.asList("Patient-ID", "Fall-Nr", "LOINC", "Codesystem", "Zusatzcode", "Zusatzcodesystem", "Parameter", "Messwert",
                "Einheit", "Zeitstempel (Abnahme)", "Werttyp", "Wertcode", "Wertcodesystem", "Kategorie", "Status", "Untersuchung ID", "Komponente von", "Ausgabezeitpunkt", "Einheitencode", "Erklärung/Ausfüllhilfe"));
        headers.put("Diagnose", Arrays.asList("Patient-ID", "Fall-Nr", "Bezeichner", "Code", "Codesystem",
                "Zusatzcode", "Zusatzcodesystem", "Dokumentationszeitpunkt", "Beginn", "Ende",
                "Klinischer Status", "Verifikationsstatus", "Typ", "Erklärung/Ausfüllhilfe"));
        headers.put("Prozedur", Arrays.asList("Patient-ID", "Fall-Nr", "Prozedurentext", "Prozedurencode",
                "Durchführungsbeginn", "Codesystem", "Zusatzcode", "Zusatzcodesystem", "Ende", "Status", "Kategorie", "Erklärung/Ausfüllhilfe"));
        headers.put("Medikation", Arrays.asList("Patient-ID", "Fall-Nr", "Medikationstyp", "Präparatbezeichnung", "Präparatcode", "Präparatcodesystem", "ATC-Code", "ATC-Version", "Darreichungsform", "Wirkstoffcode", "Wirkstoffcodesystem", "Status", "Absicht", "Dokumentationszeitpunkt", "Beginn", "Ende", "Einzeldosis", "Dosiereinheit", "Dosen pro Tag", "Dosierungstext", "Erklärung/Ausfüllhilfe"));
        headers.put("Klinische Dokumentation", Arrays.asList("Patient-ID", "Fall-Nr", "Bezeichner", "Untersuchungscode", "Codesystem", "Zusatzcode", "Zusatzcodesystem", "Wert",
                "Einheit", "Zeitstempel", "Werttyp", "Wertcode", "Wertcodesystem", "Kategorie", "Status", "Untersuchung ID", "Komponente von", "Ausgabezeitpunkt", "Einheitencode", "Erklärung/Ausfüllhilfe"));
        headers.put("DocumentReference", Arrays.asList("Patient-ID", "Fall-Nr", "Dateipfad", "Embed",
                "Dokumenttext", "Status", "Ausgabezeitpunkt", "Dokumentcode", "Dokumentcodesystem", "Dokumentbezeichner", "Erklärung/Ausfüllhilfe"));
        headers.put("Impfung", Arrays.asList("Patient-ID", "Fall-Nr", "Eintrag ID", "Bezeichner", "Code", "Codesystem", "Zeitpunkt", "Status", "Primärquelle", "Erklärung/Ausfüllhilfe"));
        headers.put("Befundbericht", Arrays.asList("Patient-ID", "Fall-Nr", "Eintrag ID", "Bezeichner", "Code", "Codesystem", "Zeitpunkt", "Status", "Ausgabezeitpunkt", "Ergebnisse", "Beschreibung", "Erklärung/Ausfüllhilfe"));
        headers.put("Behandlungsplan", Arrays.asList("Patient-ID", "Fall-Nr", "Eintrag ID", "Bezeichner", "Code", "Codesystem", "Zeitpunkt", "Ende", "Status", "Absicht", "Beschreibung", "Aktivitätscodes", "Erklärung/Ausfüllhilfe"));
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
