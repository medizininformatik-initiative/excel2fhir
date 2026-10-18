package de.uni_leipzig.imise.validate;

import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import de.uni_leipzig.imise.utils.Excel2Csv;
import de.uni_leipzig.imise.utils.ExcelWorkbooks;
import de.uni_leipzig.life.csv2fhir.ConverterOptions;

public class CsvInputValidationTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private XSSFWorkbook fixture() throws Exception {
        var workbook = new XSSFWorkbook();
        // Synthetic test tables use the shipped schema, not copies of its example patients.
        try (var template = ExcelWorkbooks.openReadOnly(new java.io.File("FHIR_Testdatengenerator_Vorlage.xlsx"))) {
            for (var source : template) {
                if (source.getSheetName().equals("Codes") || source.getSheetName().contains("Konvertierungsoptionen")) continue;
                var row = workbook.createSheet(source.getSheetName()).createRow(0);
                for (var cell : source.getRow(0)) row.createCell(cell.getColumnIndex()).setCellValue(cell.getStringCellValue());
            }
        }
        set(workbook,"Person",1,"Patient-ID","p");
        set(workbook,"Person",1,"Geburtsdatum","01.01.1980");
        set(workbook,"Fall",1,"Patient-ID","p");
        set(workbook,"Fall",1,"Fall-Nr","1");
        set(workbook,"Fall",1,"Start","2026-01-01T10:00:00+01:00");
        set(workbook,"Fall",1,"Ende","2026-01-01T11:00:00+01:00");
        set(workbook,"Fall",1,"Einrichtungskontaktklasse","ambulant");
        return workbook;
    }
    private void set(XSSFWorkbook book, String sheet, int rowIndex, String header, String value) {
        XSSFSheet table = book.getSheet(sheet);
        var row = table.getRow(rowIndex);
        if (row == null) row = table.createRow(rowIndex);
        for (var cell : table.getRow(0)) if (cell.getStringCellValue().equals(header)) {
            var target = row.getCell(cell.getColumnIndex());
            if (target == null) target = row.createCell(cell.getColumnIndex());
            target.setCellValue(value); return;
        }
        throw new AssertionError(header);
    }
    private List<String> issues(TemplateValidationResult result) {
        return result.getIssues().stream().map(Object::toString).sorted().collect(Collectors.toList());
    }
    private void compare(Consumer<XSSFWorkbook> mutate, boolean errors, boolean checks) throws Exception {
        try (var book = fixture()) {
            mutate.accept(book);
            Path dir = temporary.newFolder().toPath();
            Excel2Csv.splitExcel(book,new java.io.File("case.xlsx"),null,dir.toFile());
            var options = ConverterOptions.fromConfigurationText("CONFIGURATION_VERSION=1\nCHECK_INPUT_CONSISTENCY="+checks+"\n");
            var validator = new ExcelTemplateValidator();
            var excel = validator.validate(book,options,"test");
            var csv = validator.validateCsv(dir,"case_",options,"test");
            assertEquals(issues(excel),issues(csv));
            assertEquals(issues(csv).toString(),errors,csv.hasErrors());
        }
    }
    @Test public void validTables() throws Exception { compare(b -> {},false,true); }
    @Test public void malformedBirthDate() throws Exception { compare(b -> set(b,"Person",1,"Geburtsdatum","not-a-date"),true,true); }
    @Test public void duplicatePatient() throws Exception { compare(b -> set(b,"Person",2,"Patient-ID","p"),true,true); }
    @Test public void unknownPatient() throws Exception { compare(b -> set(b,"Fall",1,"Patient-ID","missing"),true,true); }
    @Test public void reversedContactPeriodRetainsExistingAcceptance() throws Exception { compare(b -> set(b,"Fall",1,"Ende","2025-01-01T10:00:00+01:00"),false,true); }
    @Test public void invalidDiagnosisSelection() throws Exception { compare(b -> {set(b,"Diagnose",1,"Patient-ID","p");set(b,"Diagnose",1,"Codesystem","bad system");},true,true); }
    @Test public void duplicateClinicalId() throws Exception { compare(b -> {for(int i=1;i<=2;i++){set(b,"Impfung",i,"Patient-ID","p");set(b,"Impfung",i,"Eintrag ID","same");}},true,true); }
    @Test public void componentBeforeParent() throws Exception { compare(b -> {set(b,"Laborbefund",1,"Patient-ID","p");set(b,"Laborbefund",1,"Komponente von","absent");},true,true); }
    @Test public void missingContactIsWarning() throws Exception { compare(b -> {set(b,"Diagnose",1,"Patient-ID","p");set(b,"Diagnose",1,"Fall-Nr","missing");},false,true); }
    @Test public void checksDisabledMatch() throws Exception { compare(b -> set(b,"Person",1,"Geburtsdatum","not-a-date"),false,false); }
    @Test public void missingTableStillFailsWithChecksDisabled() throws Exception { compare(b -> b.removeSheetAt(b.getSheetIndex("Person")),true,false); }
    @Test public void malformedCsvFails() throws Exception {
        Path dir = temporary.newFolder().toPath();
        Files.writeString(dir.resolve("case_Person.csv"),"Patient-ID,Vorname\np,\"unterminated\n");
        assertThrows(Exception.class, () -> new ExcelTemplateValidator().validateCsv(dir,"case_",ConverterOptions.fromConfigurationText("CONFIGURATION_VERSION=1\n"),"test"));
    }
    @Test public void inconsistentCsvWidthFails() throws Exception {
        Path dir = temporary.newFolder().toPath();
        Files.writeString(dir.resolve("case_Person.csv"),"Patient-ID,Vorname\np,one,extra\n");
        assertThrows(java.io.IOException.class, () -> new ExcelTemplateValidator().validateCsv(dir,"case_",ConverterOptions.fromConfigurationText("CONFIGURATION_VERSION=1\n"),"test"));
    }
}
