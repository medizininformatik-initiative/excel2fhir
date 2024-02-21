package de.uni_leipzig.imise.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;

/**
 * @author fmeineke (02.11.2020)
 */
public class Excel2Csv {

    /**  */
    private static final String DELIM = ",";

    /**  */
    private static final String QUOTE = "\"";

    /** Replacement for Quotes in values of Excel cells */
    public static final String QUOTE_ESCAPE = "~Q~";

    /**  */
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy HH:mm");

    /**  */
    private static final Logger LOG = LoggerFactory.getLogger(Excel2Csv.class);

    /**
     * @param excelFile
     * @param sheetNamePatterns
     * @throws IOException
     */
    public static void splitExcel(File excelFile, Collection<String> sheetNamePatterns) throws IOException {
        String basename = FilenameUtils.removeExtension(excelFile.getPath());
        File csvDir = new File(basename);
        splitExcel(excelFile, sheetNamePatterns, csvDir);
    }

    /**
     * @param s
     * @param patterns
     * @return
     */
    private static boolean matches(String s, Collection<String> patterns) {
        for (String pattern : patterns) {
            if (s.matches(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param sourceExcelFile
     * @param sheetNamePatterns if not <code>null</code> then only the sheets
     *            with a name in this collection will be convertert to csv. If
     *            <code>null</code> then all sheet will be convertet.
     * @param targetCsvDir
     * @throws IOException
     */
    @SuppressWarnings("null")
    public static void splitExcel(File sourceExcelFile, Collection<String> sheetNamePatterns, File targetCsvDir) throws IOException {
        LOG.info("Start splitting Excel to CSV...");
        Stopwatch stopwatch = Stopwatch.createStarted();
        String sourceFileName = FilenameUtils.removeExtension(sourceExcelFile.getName());
        String csvDirBasename = FilenameUtils.removeExtension(targetCsvDir.getPath());
        try (Workbook workbook = new XSSFWorkbook(new FileInputStream(sourceExcelFile))) {
            for (Sheet dataSheet : workbook) {
                String sheetName = dataSheet.getSheetName();
                if (sheetNamePatterns != null) {
                    if (!matches(sheetName, sheetNamePatterns)) {
                        LOG.info("Skip sheet \"" + sheetName + "\"");
                        continue;
                    }
                }
                // Das ist der Trick für das pot. Setzen des encondigs.(z.B. wegen "männlich")
                // Wir setzten nun aber nur auf UTF
                String csvFile = FilenameUtils.concat(csvDirBasename, sourceFileName + "_" + sheetName + ".csv");
                OutputStream os = new FileOutputStream(new File(csvFile));
                String charSet = "UTF-8";
                //                String charSet = "ISO-8859-1";
                try (PrintWriter csv = new PrintWriter(new OutputStreamWriter(os, charSet))) {
                    LOG.info("Creating " + csvFile);
                    // Annahme: Header ist in der ersten Zeile
                    // Annahme: Es gibt nur soviele Spalten wie Header
                    int maxCol = 0;
                    Row firstRow = dataSheet.getRow(0);
                    // Z�hle relevante Spalten
                    for (int col = 0; col < firstRow.getLastCellNum(); col++) {
                        // This looks fine but skips null cells
                        //for (Cell cell : firstRow) {
                        //  String s = cell.getStringCellValue();
                        Cell cell = firstRow.getCell(col);
                        if (cell == null) {
                            break;
                        }
                        String cellColumnHeader = cell.getStringCellValue();
                        if (cellColumnHeader.isEmpty()) {
                            break;
                        }
                        if (!cellColumnHeader.trim().equals(cellColumnHeader)) {
                            LOG.error("Column \"" + cellColumnHeader + "\" is not trimmed");
                        }
                        maxCol++;
                    }
                    for (Row row : dataSheet) {
                        boolean skipEmptyRow = true;
                        List<String> rowValues = new ArrayList<>();
                        for (int col = 0; col < maxCol; col++) {
                            Cell cell = row.getCell(col);
                            String cellValue = null;
                            CellType cellType = cell != null ? cell.getCellType() : CellType.BLANK;
                            CellType formulaResultType = cellType == CellType.FORMULA ? cell.getCachedFormulaResultType() : null;
                            if (cellType == CellType.BLANK) {
                                cellValue = "";
                            } else if (cellType == CellType.NUMERIC || formulaResultType == CellType.NUMERIC) {
                                if (DateUtil.isCellDateFormatted(cell)) {
                                    // Achtung: Das klappt nicht immer; ab und zu ist Datum in Excel trotzdem ein String
                                    cellValue = DATE_FORMAT.format(cell.getDateCellValue());
                                } else {
                                    // 11715311 wird ansonsten zu 1.1715311E7
                                    // Mist Excel
                                    double d = cell.getNumericCellValue();
                                    long l = (long) d;
                                    if (d - l == 0) {
                                        cellValue = "" + l;
                                    } else {
                                        cellValue = "" + d;
                                    }
                                    cellValue = cellValue.replace(",", ".");
                                    cellValue = cellValue.replaceAll("\\.0$", "");
                                }
                                skipEmptyRow = false;
                            } else if (cellType == CellType.STRING || formulaResultType == CellType.STRING) {
                                cellValue = cell.getStringCellValue();
                                skipEmptyRow = false;
                            } else {
                                LOG.error("Unknown cell type " + cell.getCellType().name() + " " + cell.getAddress());
                                cellValue = "";
                            }
                            // clean value inclusive bon-breaking whitespace occured in ICD
                            cellValue = cellValue.replaceAll("[\u00A0\u2007\u202F\\s]+", " ").trim();
                            // "No Value" used in UKE
                            if ("#NV".equals(cellValue)) {
                                cellValue = "";
                            }
                            // We must escape all quotes in the values to prevent errors
                            // on reading the CSV-file with Java. There is no standard
                            // for escaping quotes in CSV so we use our own escape sequence.
                            cellValue = cellValue.replace("\"", QUOTE_ESCAPE);
                            if (cellValue.contains(DELIM)) {
                                cellValue = QUOTE + cellValue + QUOTE;
                            }
                            rowValues.add(cellValue);
                        }
                        if (!skipEmptyRow) {
                            for (int i = 0; i < rowValues.size() - 1; i++) {
                                csv.print(rowValues.get(i));
                                csv.print(DELIM);
                            }
                            csv.print(rowValues.get(rowValues.size() - 1));
                            csv.println();
                        }
                    }
                }
            }
        }
        LOG.info("Finished splitting Excel to CSV in " + stopwatch.stop());
    }

}
