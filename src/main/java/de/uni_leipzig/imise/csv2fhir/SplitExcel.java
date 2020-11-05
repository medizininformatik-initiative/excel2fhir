package de.uni_leipzig.imise.csv2fhir;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashSet;
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

import de.uni_leipzig.life.csv2fhir.Csv2Fhir;

public class SplitExcel {

    private static Set<String> sheetNames = new HashSet<String>((Arrays.asList(
            "Person","Versorgungsfall","Abteilungsfall","Laborbefund","Diagnose","Prozedur","Medikation","Klinische Dokumentation"
            //			"Person","Klinische Dokumentation"
            )));
    private static String delim=",";
    private static String quote="\"";
    private static SimpleDateFormat dateFormat =new SimpleDateFormat("dd.MM.yyyy, HH:mm");
    //	private static SimpleDateFormat dateFormat =new SimpleDateFormat("MM/dd/yyyy");
    class Logger {
        void info(String s) { System.out.println(s); } 
        void error(String s) { System.err.println(s); } 
    };
    Logger log = new Logger();
    //	Logger log = LogManager.getLogger(getClass());

    public File splitExcel(File excel) throws IOException {
        FileInputStream excelFile = new FileInputStream(excel);
        String basename = FilenameUtils.removeExtension(excel.getPath());
        File csvDir = new File(basename);
        if (!csvDir.exists()) {
            log.info("creating " + csvDir);
            csvDir.mkdirs();
        } else {
            log.info("delete all files in " + csvDir);
            FileUtils.cleanDirectory(csvDir);
        }
        try (Workbook workbook = new XSSFWorkbook(excelFile)) {
            for (Sheet dataSheet : workbook) {
                String sheetName = dataSheet.getSheetName();
                if (!sheetNames.contains(sheetName)) {
                    log.info("skip sheet \"" + sheetName + "\"");
                    continue;
                }
                // Das ist der Trick f�r das pot. Setzen des encondigs.(z.B. wegen "m�nnlich")
                // Wir setzten nun aber nur auf UTF
                String csvFile = FilenameUtils.concat(basename, sheetName+".csv"); 
                OutputStream os = new FileOutputStream(new File(csvFile));
                String charSet = "UTF-8";
//                String charSet = "ISO-8859-1";
                try (PrintWriter csv = new PrintWriter(new OutputStreamWriter(os,charSet))) {
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
                        if (cell == null) break;
                        String cellColumnHeader = cell.getStringCellValue();
                        if (cellColumnHeader.isEmpty()) break;
                        if (!cellColumnHeader.trim().equals(cellColumnHeader)) 
                            log.error("column \"" + cellColumnHeader + "\" is not trimmed");
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
                                    cellValue = dateFormat.format(cell.getDateCellValue());
                                } else {
                                    cellValue = "" + cell.getNumericCellValue();
                                    cellValue = cellValue.replace(",", ".");
                                    cellValue = cellValue.replaceAll("\\.0$", "");
                                }
                            } else if (cell.getCellType() == CellType.STRING) { 
                                cellValue = cell.getStringCellValue();								
                            } else {
                                log.error("unknown cell type " + cell.getCellType().name() + " " + cell.getAddress());
                                cellValue = "";
                            }
                            if (col > 0) csv.print(delim);
                            //s = s.replace("\"", "\"\"");
                            if (cellValue.contains(delim)) cellValue = quote + cellValue + quote;
                            csv.print(cellValue);
                        }
                        if (!skipEmptyRow) csv.println();
                    }
                }
            }
        }
        log.info("finished");
        return csvDir;
    }
    public void convertAllExcelInDir(File excelDir) {
        FilenameFilter filter= new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return !name.startsWith("~") && name.toLowerCase().endsWith(".xlsx");
            }
        };
        for (File excelTestdata : excelDir.listFiles(filter)) {
            File csvDir;
            try {
                csvDir = splitExcel(excelTestdata);
                File logFile = new File(excelDir,	FilenameUtils.removeExtension(excelTestdata.getName())+".log");
                System.out.println(logFile.getAbsolutePath());
                System.setOut(new PrintStream(logFile));
                File resultJson = new File(excelDir,            			
                        FilenameUtils.removeExtension(excelTestdata.getName())+".json");
                Csv2Fhir converter = new Csv2Fhir(csvDir,resultJson);
                converter.convertFiles();	
                System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }		
    }

    public static void main(String[] args) {
        SplitExcel se = new SplitExcel();
        se.convertAllExcelInDir(new File(args[0]));
    }
}
