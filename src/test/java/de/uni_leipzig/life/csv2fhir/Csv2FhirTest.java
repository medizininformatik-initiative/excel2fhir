package de.uni_leipzig.life.csv2fhir;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class Csv2FhirTest {

    @Test
    public void getOutputFileNameRemovesTrailingSeparatorWithoutExtension() {
        String fileName = Csv2Fhir.getOutputFileName("FHIR_Testdatengenerator_Vorlage_", "", OutputFileType.JSON);

        assertEquals("FHIR_Testdatengenerator_Vorlage.json", fileName);
    }

    @Test
    public void getOutputFileNameKeepsSeparatorWithExtension() {
        String fileName = Csv2Fhir.getOutputFileName("FHIR_Testdatengenerator_Vorlage_", "PID1", OutputFileType.JSON);

        assertEquals("FHIR_Testdatengenerator_Vorlage_PID1.json", fileName);
    }
}
