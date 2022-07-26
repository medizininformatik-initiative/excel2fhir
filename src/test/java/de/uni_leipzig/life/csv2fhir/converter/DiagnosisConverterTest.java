package de.uni_leipzig.life.csv2fhir.converter;

import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Versorgungsfall;
import static de.uni_leipzig.life.csv2fhir.converter.DiagnosisConverter.Diagnosis_Columns.Dokumentationsdatum;
import static de.uni_leipzig.life.csv2fhir.converter.DiagnosisConverter.Diagnosis_Columns.ICD;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import de.uni_leipzig.life.csv2fhir.ConverterResult;

@RunWith(MockitoJUnitRunner.class)
public class DiagnosisConverterTest {

    @Test
    public void convertTest() throws Exception {
        CSVRecord recordMock = mock(CSVRecord.class);
        doReturn("PID1").when(recordMock).get("Patient-ID");
        ConverterResult resultMock = mock(ConverterResult.class);
        //FHIRValidator validator = mock(FHIRValidator.class);
        DiagnosisConverter diagnosisConverterUnderTest = new DiagnosisConverter(recordMock, resultMock, null);

        //doReturn(null).when(recordMock).get("ICD");
        when(recordMock.get("ICD")).thenReturn(null);
        List<Resource> convertedResources = diagnosisConverterUnderTest.convertInternal();
        assertNull(convertedResources);

        when(recordMock.get("ICD")).thenReturn("");
        Assertions.assertThrows(Exception.class, () -> {
            diagnosisConverterUnderTest.convertInternal();
        });

        when(recordMock.get("ICD")).thenReturn(" \t ");
        Assertions.assertThrows(Exception.class, () -> {
            diagnosisConverterUnderTest.convertInternal();
        });

        testConvert(diagnosisConverterUnderTest, recordMock, resultMock, "A12.34", "A12.34");
        testConvert(diagnosisConverterUnderTest, recordMock, resultMock, "A12.3", "A12.3");
        testConvert(diagnosisConverterUnderTest, recordMock, resultMock, "A12.", "A12");
        testConvert(diagnosisConverterUnderTest, recordMock, resultMock, "A12", "A12");
        testConvert(diagnosisConverterUnderTest, recordMock, resultMock, "A12.34A12.34", "A12.34");
        testConvert(diagnosisConverterUnderTest, recordMock, resultMock, "A12.34A", "A12.34");
        testConvert(diagnosisConverterUnderTest, recordMock, resultMock, "A1");

        testConvert(diagnosisConverterUnderTest, recordMock, resultMock, "A12.34A12.3", "A12.34", "A12.3");
        testConvert(diagnosisConverterUnderTest, recordMock, resultMock, "A12.34A12.", "A12.34", "A12");
        testConvert(diagnosisConverterUnderTest, recordMock, resultMock, "A12.34A12.", "A12.34", "A12");

        testConvert(diagnosisConverterUnderTest, recordMock, resultMock, "F02.3*+G20.10", "F02.3", "G20.10");

    }

    /**
     * @param diagnosisConverter
     * @param recordMock
     * @param resultMock
     * @param codeInput
     * @param resultCodes
     */
    private static void testConvert(DiagnosisConverter diagnosisConverter, CSVRecord recordMock, ConverterResult resultMock, String codeInput, String... expectedResultCodes) throws Exception {
        String recordedDate = "02.10.2020 00:00";
        doReturn(recordedDate).when(recordMock).get(Dokumentationsdatum.toString());

        when(recordMock.get(ICD.toString())).thenReturn(codeInput);
        when(resultMock.get(Versorgungsfall, Encounter.class, diagnosisConverter.getEncounterId())).thenReturn(new Encounter());
        List<Resource> convertedResources = diagnosisConverter.convertInternal();
        int convertedResourcesCount = convertedResources == null ? 0 : convertedResources.size();
        assertEquals(convertedResourcesCount, expectedResultCodes.length);
        Set<String> conditionIDs = new HashSet<>();
        for (int i = 0; i < convertedResourcesCount; i++) {
            Condition condition = (Condition) convertedResources.get(i); //the potencial Nullpointer warning is wrong!
            CodeableConcept code = condition.getCode();
            List<Coding> codings = code.getCoding();
            assertEquals(codings.size(), 1);
            Coding coding = codings.get(0);
            String codingCode = coding.getCode();
            assertEquals(codingCode, expectedResultCodes[i]);
            conditionIDs.add(condition.getId());
        }
        //check whether laways different IDs are generated
        assertEquals(conditionIDs.size(), convertedResourcesCount);
    }
}
