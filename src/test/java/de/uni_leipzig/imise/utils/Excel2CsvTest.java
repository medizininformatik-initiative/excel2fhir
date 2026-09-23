package de.uni_leipzig.imise.utils;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class Excel2CsvTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void dottedDirectoryNamesAreUsedLiterally() throws Exception {
        File target = temp.newFolder("Fall.example.xlsx");
        Excel2Csv.splitExcel(new File("FHIR_Testdatengenerator_Vorlage.xlsx"), List.of("Person"), target);
        assertTrue(Files.isRegularFile(target.toPath().resolve("FHIR_Testdatengenerator_Vorlage_Person.csv")));
    }
}
