package de.uni_leipzig.imise.validate;

/**
 * Validation issue found in an Excel input template.
 */
public class TemplateValidationIssue {

    public static enum Severity {
        ERROR,
        WARNING
    }

    private final Severity severity;
    private final String sheetName;
    private final int rowNumber;
    private final String columnName;
    private final String message;

    public TemplateValidationIssue(Severity severity, String sheetName, int rowNumber, String columnName,
            String message) {
        this.severity = severity;
        this.sheetName = sheetName;
        this.rowNumber = rowNumber;
        this.columnName = columnName;
        this.message = message;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getSheetName() {
        return sheetName;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public String getColumnName() {
        return columnName;
    }

    public String getMessage() {
        return message;
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    @Override
    public String toString() {
        String location = sheetName;
        if (rowNumber > 0) {
            location += "!" + rowNumber;
        }
        if (columnName != null) {
            location += "[" + columnName + "]";
        }
        return severity + " " + location + ": " + message;
    }
}
