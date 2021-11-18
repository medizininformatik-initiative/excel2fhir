package de.uni_leipzig.imise.csv2fhir;

import static de.uni_leipzig.life.csv2fhir.Csv2Fhir.OutputFileType.JSON;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Collection;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import de.uni_leipzig.imise.utils.FileTools;
import de.uni_leipzig.imise.utils.Sys;
import de.uni_leipzig.life.csv2fhir.Csv2Fhir;
import de.uni_leipzig.life.csv2fhir.Csv2Fhir.OutputFileType;

/**
 * @author fmeinecke (02.11.2020)
 */
public class SplitExcel {

    /**  */
    private static final String DELIM = ",";

    /**  */
    private static final String QUOTE = "\"";

    /**  */
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy HH:mm");

    /**
     *
     */
    class Logger {
        void info(String s) {
            Sys.outm(1, 1, s);
        }
        void error(String s) {
            Sys.errm(1, 1, s);
        }
    }

    /**  */
    Logger log = new Logger();
    //	Logger log = LogManager.getLogger(getClass());

    /**
     * @param excelFile
     * @return
     */
    private static File getTargetCSVDir(File excelFile) {
        String path = excelFile.getPath();
        path = FilenameUtils.removeExtension(path);
        File targetCSVDir = new File(path, "output");
        return targetCSVDir;
    }

    /**
     * @param excelFile
     * @param sheetNames
     * @throws IOException
     */
    public void splitExcel(File excelFile, Collection<String> sheetNames) throws IOException {
        String basename = FilenameUtils.removeExtension(excelFile.getPath());
        File csvDir = new File(basename);
        splitExcel(excelFile, sheetNames, csvDir);
    }

    /**
     * @param sourceExcelFile
     * @param sheetNames if not <code>null</code> then only the sheets with a
     *            name in this collection will be convertert to csv. If
     *            <code>null</code> then all sheet will be convertet.
     * @param targetCsvDir
     * @throws IOException
     */
    public void splitExcel(File sourceExcelFile, Collection<String> sheetNames, File targetCsvDir) throws IOException {
        ensureEmptyDirectory(targetCsvDir, sourceExcelFile.getParentFile());
        String csvDirBasename = FilenameUtils.removeExtension(targetCsvDir.getPath());
        try (Workbook workbook = new XSSFWorkbook(new FileInputStream(sourceExcelFile))) {
            for (Sheet dataSheet : workbook) {
                String sheetName = dataSheet.getSheetName();
                if (sheetNames != null && !sheetNames.contains(sheetName)) {
                    log.info("Skip sheet \"" + sheetName + "\"");
                    continue;
                }
                // Das ist der Trick für das pot. Setzen des encondigs.(z.B. wegen "männlich")
                // Wir setzten nun aber nur auf UTF
                String csvFile = FilenameUtils.concat(csvDirBasename, sheetName + ".csv");
                OutputStream os = new FileOutputStream(new File(csvFile));
                String charSet = "UTF-8";
                //                String charSet = "ISO-8859-1";
                try (PrintWriter csv = new PrintWriter(new OutputStreamWriter(os, charSet))) {
                    log.info("Creating " + csvFile);
                    // Annahme: Header ist in der ersten Zeile
                    // Annahme: Es gibt nur soviele Spalten wie Header
                    int maxCol = 0;
                    Row firstRow = dataSheet.getRow(0);
                    // Z�hle relevante Spalten
                    for (int col = 0; col < firstRow.getLastCellNum(); col++) {
                        // This looks fine but skips null cells
                        //for (Cell cell : firstRow) {
                        //	String s = cell.getStringCellValue();
                        Cell cell = firstRow.getCell(col);
                        if (cell == null) {
                            break;
                        }
                        String cellColumnHeader = cell.getStringCellValue();
                        if (cellColumnHeader.isEmpty()) {
                            break;
                        }
                        if (!cellColumnHeader.trim().equals(cellColumnHeader)) {
                            log.error("Column \"" + cellColumnHeader + "\" is not trimmed");
                        }
                        maxCol++;
                    }
                    for (Row row : dataSheet) {
                        boolean skipEmptyRow = false;
                        for (int col = 0; col < maxCol; col++) {
                            Cell cell = row.getCell(col);
                            String cellValue;
                            if (cell == null || cell.getCellType() == CellType.BLANK) {
                                if (col == 0) {
                                    skipEmptyRow = true;
                                    break;
                                }
                                cellValue = "";
                            } else if (cell.getCellType() == CellType.NUMERIC) {
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
                            } else if (cell.getCellType() == CellType.STRING) {
                                cellValue = cell.getStringCellValue();
                            } else {
                                log.error("Unknown cell type " + cell.getCellType().name() + " " + cell.getAddress());
                                cellValue = "";
                            }
                            if (col > 0) {
                                csv.print(DELIM);
                            }
                            // clean value inclusive bon-breaking whitespace occured in ICD
                            cellValue = cellValue.replaceAll("[\u00A0\u2007\u202F\\s]+", " ").trim();
                            // "No Value" used in UKE
                            if (cellValue.equals("#NV")) {
                                cellValue = "";
                            }
                            if (cellValue.contains(DELIM)) {
                                cellValue = QUOTE + cellValue + QUOTE;
                            }
                            csv.print(cellValue);
                        }
                        if (!skipEmptyRow) {
                            csv.println();
                        }
                    }
                }
            }
        }
        log.info("Finished");
    }

    /**
     * @param dir2CreateOrClean
     * @param inputDir
     * @throws IOException
     */
    private final void ensureEmptyDirectory(File dir2CreateOrClean, File inputDir) throws IOException {
        if (!dir2CreateOrClean.exists()) {
            log.info("creating " + dir2CreateOrClean);
            dir2CreateOrClean.mkdirs();
        } else if (!inputDir.equals(dir2CreateOrClean)) {
            if (!FileTools.isEmpty(dir2CreateOrClean)) {
                log.info("Delete all files in \"" + dir2CreateOrClean + "\"");
                try {
                    FileUtils.cleanDirectory(dir2CreateOrClean);
                } catch (Exception e) {
                    // ignore (maybe the .log file cannot be deleted)
                }
            }
        }
    }

    /**
     * @param sourceExcelFileOrDirectory
     * @param targetCSVDir
     * @param targetJSONDir
     * @throws IOException
     */
    private void createAndCleanOutputDirectories(File sourceExcelFileOrDirectory, File targetCSVDir, File targetJSONDir)
            throws IOException {
        File sourceExcelDir = sourceExcelFileOrDirectory.isDirectory() ? sourceExcelFileOrDirectory
                : sourceExcelFileOrDirectory.getParentFile();
        //create and reset directories
        if (targetCSVDir == null) {
            targetCSVDir = getTargetCSVDir(sourceExcelDir);
        }
        ensureEmptyDirectory(targetCSVDir, sourceExcelDir);
        if (targetJSONDir == null) {
            targetJSONDir = targetCSVDir;
        }
        ensureEmptyDirectory(targetJSONDir, sourceExcelDir);
    }

    /**
     * @param excelDir
     * @param sheetNames if not <code>null</code> then only the sheets with a
     *            name in this collection will be convertert to csv. If
     *            <code>null</code> then all sheet will be convertet.
     * @throws IOException
     */
    public void convertAllExcelInDir(File sourceExcelDir, Collection<String> sheetNames) throws IOException {
        convertAllExcelInDir(sourceExcelDir, sheetNames, null, null, JSON, false);
    }

    /**
     * @param sourceExcelDir
     * @param sheetNames if not <code>null</code> then only the sheets with a
     *            name in this collection will be convertert to csv. If
     *            <code>null</code> then all sheet will be convertet.
     * @param tempDir
     * @param resultDir
     * @param outputFileType
     * @param convertFilesPerPatient
     * @throws IOException
     */
    public void convertAllExcelInDir(File sourceExcelDir, Collection<String> sheetNames, File tempDir, File resultDir, OutputFileType outputFileType,
            boolean convertFilesPerPatient)
            throws IOException {
        FilenameFilter filter = (dir, name) -> !name.startsWith("~") && name.toLowerCase().endsWith(".xlsx");
        createAndCleanOutputDirectories(sourceExcelDir, tempDir, resultDir);
        for (File sourceExcelFile : sourceExcelDir.listFiles(filter)) {
            convertExcelFile(sourceExcelFile, sheetNames, tempDir, resultDir, outputFileType, convertFilesPerPatient, false);
        }
    }

    /**
     * @param sourceExcelFile
     * @param sheetNames if not <code>null</code> then only the sheets with a
     *            name in this collection will be convertert to csv. If
     *            <code>null</code> then all sheet will be convertet.
     * @param tempDir
     * @param resultDir
     * @param outputFileType
     * @param convertFilesPerPatient
     * @throws IOException
     */
    public void convertExcelFile(File sourceExcelFile, Collection<String> sheetNames, File tempDir, File resultDir, OutputFileType outputFileType,
            boolean convertFilesPerPatient)
            throws IOException {
        convertExcelFile(sourceExcelFile, sheetNames, tempDir, resultDir, outputFileType, convertFilesPerPatient, true);
    }

    /**
     * @param sourceExcelFile
     * @param sheetNames if not <code>null</code> then only the sheets with a
     *            name in this collection will be convertert to csv. If
     *            <code>null</code> then all sheet will be convertet.
     * @param tempDir
     * @param resultDir
     * @param outputFileType
     * @param convertFilesPerPatient
     * @param createAndCleanOutputDirectories
     * @throws IOException
     */
    private void convertExcelFile(File sourceExcelFile, Collection<String> sheetNames, File tempDir, File resultDir, OutputFileType outputFileType,
            boolean convertFilesPerPatient, boolean createAndCleanOutputDirectories) throws IOException {
        if (createAndCleanOutputDirectories) {
            createAndCleanOutputDirectories(sourceExcelFile, tempDir, resultDir);
        }
        PrintStream defaultSysOut = System.out;
        String fileName = FilenameUtils.removeExtension(sourceExcelFile.getName());
        File logFile = new File(tempDir, fileName + ".log");
        try (PrintStream logFileStream = new PrintStream(logFile)) {
            splitExcel(sourceExcelFile, sheetNames, tempDir);
            Sys.out1(logFile.getAbsolutePath());
            System.setOut(logFileStream);
            Csv2Fhir converter = new Csv2Fhir(tempDir, resultDir, fileName);
            converter.convertFiles(outputFileType, convertFilesPerPatient);
            System.setOut(defaultSysOut);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
