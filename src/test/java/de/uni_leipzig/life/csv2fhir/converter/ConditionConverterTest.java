package de.uni_leipzig.life.csv2fhir.converter;

import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Fall;
import static org.junit.Assert.assertEquals;

import java.io.StringReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Resource;
import org.junit.Assert;
import org.junit.Test;

import de.uni_leipzig.life.csv2fhir.ConverterOptions;
import de.uni_leipzig.life.csv2fhir.ConverterResult;

public class ConditionConverterTest {

    @Test
    public void convertTest() throws Exception {
        ConverterResult result = new ConverterResult(new ConverterOptions(""));

        Assert.assertThrows(Exception.class, () -> {
            newConditionConverter(null, result).convertInternal();
        });

        Assert.assertThrows(Exception.class, () -> {
            newConditionConverter("", result).convertInternal();
        });

        Assert.assertThrows(Exception.class, () -> {
            newConditionConverter(" \t ", result).convertInternal();
        });

        testConvert("A12.34", "A12.34");
        testConvert("A12.3", "A12.3");
        testConvert("A12.", "A12");
        testConvert("A12", "A12");
        testConvert("A12.34A12.34", "A12.34");
        testConvert("A12.34A", "A12.34");
        testConvert("A1");

        testConvert("A12.34A12.3", "A12.34", "A12.3");
        testConvert("A12.34A12.", "A12.34", "A12");
        testConvert("A12.34A12.", "A12.34", "A12");

        testConvert("F02.3*+G20.10", "F02.3", "G20.10");

    }

    /**
     * @param codeInput
     * @param resultCodes
     */
    private static void testConvert(String codeInput, String... expectedResultCodes) throws Exception {
        ConverterResult result = new ConverterResult(new ConverterOptions(""));
        Encounter encounter = new Encounter();
        encounter.setId("PID1-E-1");
        result.add(Fall, encounter);
        ConditionConverter diagnosisConverter = newConditionConverter(codeInput, result);
        List<Resource> convertedResources = diagnosisConverter.convertInternal();
        int convertedResourcesCount = convertedResources == null ? 0 : convertedResources.size();
        assertEquals(convertedResourcesCount, expectedResultCodes.length);
        Set<String> conditionIDs = new HashSet<>();
        for (int i = 0; i < convertedResourcesCount; i++) {
            Condition condition = (Condition) convertedResources.get(i); // the potencial Nullpointer warning is wrong!
            CodeableConcept code = condition.getCode();
            List<Coding> codings = code.getCoding();
            assertEquals(codings.size(), 1);
            Coding coding = codings.get(0);
            String codingCode = coding.getCode();
            assertEquals(codingCode, expectedResultCodes[i]);
            conditionIDs.add(condition.getId());
        }
        // check whether always different IDs are generated
        assertEquals(conditionIDs.size(), convertedResourcesCount);
    }

    /**
     * @param icdCode
     * @param result
     * @return
     * @throws Exception
     */
    private static ConditionConverter newConditionConverter(String icdCode, ConverterResult result) throws Exception {
        CSVRecord record = createRecord(icdCode);
        return new ConditionConverter(record, null, result, null, new ConverterOptions(""));
    }

    /**
     * @param icdCode
     * @return
     * @throws Exception
     */
    private static CSVRecord createRecord(String icdCode) throws Exception {
        String value = icdCode == null ? "" : icdCode;
        String csv = "Patient-ID,Fall-Nr,Bezeichner,ICD,Dokumentationszeitpunkt,Typ\n"
                + "PID1,1,," + value + ",02.10.2020 00:00,\n";
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setNullString("")
                .setHeader()
                .setSkipHeaderRecord(true)
                .build();
        CSVParser parser = csvFormat.parse(new StringReader(csv));
        return parser.getRecords().get(0);
    }
}
