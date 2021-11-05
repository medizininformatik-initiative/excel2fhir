package de.uni_leipzig.imise.csv2fhir;

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
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.google.common.collect.Sets;

import de.uni_leipzig.imise.utils.Sys;
import de.uni_leipzig.life.csv2fhir.Csv2Fhir;

/**
 * @author fmeinecke (02.11.2020)
 */
public class SplitExcel {

    /**  */
    private static Set<String> sheetNames = Sets.newHashSet("Person", "Versorgungsfall", "Abteilungsfall", "Laborbefund",
            "Diagnose", "Prozedur", "Medikation", "Klinische Dokumentation"
    //            "Medikation"
    );

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
            Sys.out1(s);
        }
        void error(String s) {
            Sys.err1(s);
        }
    }

    /**  */
    Logger log = new Logger();
    //	Logger log = LogManager.getLogger(getClass());

    /**
     * @param excel
     * @throws IOException
     */
    public void splitExcel(File excel) throws IOException {
        String basename = FilenameUtils.removeExtension(excel.getPath());
        File csvDir = new File(basename);
        splitExcel(excel, csvDir);
    }

    /**
     * @param excel
     * @param csvDir
     * @throws IOException
     */
    public void splitExcel(File excel, File csvDir) throws IOException {
        if (!csvDir.exists()) {
            log.info("creating " + csvDir);
            csvDir.mkdirs();
        } else {
            log.info("delete all files in " + csvDir);
            FileUtils.cleanDirectory(csvDir);
        }
        String csvDirBasename = FilenameUtils.removeExtension(csvDir.getPath());
        try (Workbook workbook = new XSSFWorkbook(new FileInputStream(excel))) {
            for (Sheet dataSheet : workbook) {
                String sheetName = dataSheet.getSheetName();
                if (!sheetNames.contains(sheetName)) {
                    log.info("skip sheet \"" + sheetName + "\"");
                    continue;
                }
                // Das ist der Trick f�r das pot. Setzen des encondigs.(z.B. wegen "m�nnlich")
                // Wir setzten nun aber nur auf UTF
                String csvFile = FilenameUtils.concat(csvDirBasename, sheetName + ".csv");
                OutputStream os = new FileOutputStream(new File(csvFile));
                String charSet = "UTF-8";
                //                String charSet = "ISO-8859-1";
                try (PrintWriter csv = new PrintWriter(new OutputStreamWriter(os, charSet))) {
                    log.info("creating " + csvFile);
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
                            log.error("column \"" + cellColumnHeader + "\" is not trimmed");
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
                                log.error("unknown cell type " + cell.getCellType().name() + " " + cell.getAddress());
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
        log.info("finished");
    }

    /**
     * @param excelDir
     */
    public void convertAllExcelInDir(File excelDir) {
        FilenameFilter filter = (dir, name) -> !name.startsWith("~") && name.toLowerCase().endsWith(".xlsx");
        for (File excelTestdata : excelDir.listFiles(filter)) {
            PrintStream defaultSysOut = System.out;
            String fileName = FilenameUtils.removeExtension(excelTestdata.getName());
            File logFile = new File(excelDir, fileName + ".log");
            try (PrintStream logFileStream = new PrintStream(logFile)) {
                splitExcel(excelTestdata);
                File csvDir = new File(FilenameUtils.removeExtension(excelTestdata.getPath()));
                Sys.out1(logFile.getAbsolutePath());
                System.setOut(logFileStream);
                //                File resultJson = new File(excelDir, fileName + ".json");
                Csv2Fhir converter = new Csv2Fhir(csvDir, fileName);
                converter.convertFiles();
                System.setOut(defaultSysOut);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        SplitExcel se = new SplitExcel();
        se.convertAllExcelInDir(new File(args[0]));
    }
}
