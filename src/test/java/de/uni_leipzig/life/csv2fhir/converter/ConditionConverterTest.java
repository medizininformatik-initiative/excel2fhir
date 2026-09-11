package de.uni_leipzig.life.csv2fhir.converter;

import static org.junit.Assert.*;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.hl7.fhir.r4.model.Condition;
import org.junit.Test;

import de.uni_leipzig.life.csv2fhir.ConverterOptions;
import de.uni_leipzig.life.csv2fhir.ConverterResult;

public class ConditionConverterTest {
    @Test
    public void keepsInvalidCodeLiterallyAndUsesExplicitYear() throws Exception {
        Condition c = convert(Map.of("Code", "F02.3*+G20.10", "Codesystem", "ICD-10-GM 2026",
                "Dokumentationszeitpunkt", "1980-01-01"));
        assertEquals("F02.3*+G20.10", c.getCode().getCodingFirstRep().getCode());
        assertEquals("2026", c.getCode().getCodingFirstRep().getVersion());
    }

    @Test
    public void combinesOriginalAndTargetInOneDiagnosis() throws Exception {
        Condition c = convert(Map.of("Code", "74400008", "Codesystem", DiagnosisValues.SNOMED,
                "Zusatzcode", "K35.8", "Zusatzcodesystem", "ICD-10-GM 2026"));
        assertEquals(2, c.getCode().getCoding().size());
        assertEquals("http://snomed.info/sct", c.getCode().getCodingFirstRep().getSystem());
        assertFalse(c.getCode().getCodingFirstRep().hasVersion());
    }

    @Test
    public void preservesIndependentDatesAndStatuses() throws Exception {
        Condition c = convert(Map.of("Dokumentationszeitpunkt", "2026-09-11T08:00:00+02:00",
                "Beginn", "2020-04", "Ende", "2026-08-31", "Klinischer Status", "Abgeklungen",
                "Verifikationsstatus", "Bestätigt"));
        assertEquals("2020-04", c.getOnsetDateTimeType().getValueAsString());
        assertEquals("2026-08-31", c.getAbatementDateTimeType().getValueAsString());
        assertEquals("2026-09-11T08:00:00+02:00", c.getRecordedDateElement().getValueAsString());
        assertEquals("resolved", c.getClinicalStatus().getCodingFirstRep().getCode());
        assertEquals("confirmed", c.getVerificationStatus().getCodingFirstRep().getCode());
    }

    @Test
    public void blankIsOmittedAndAbsentReasonIsExplicit() throws Exception {
        Condition blank = convert(Map.of());
        assertFalse(blank.hasCode());
        assertFalse(blank.hasRecordedDateElement());
        assertFalse(blank.hasClinicalStatus());
        Condition absent = convert(Map.of("Code", "!dar:masked", "Codesystem", DiagnosisValues.SNOMED,
                "Beginn", "!dar:unknown", "Verifikationsstatus", "!dar:not-asked"));
        assertFalse(absent.getCode().getCodingFirstRep().getCodeElement().hasValue());
        assertEquals("masked", absent.getCode().getCodingFirstRep().getCodeElement()
                .getExtensionFirstRep().getValue().primitiveValue());
        assertTrue(absent.getOnsetDateTimeType().hasExtension());
        assertTrue(absent.getVerificationStatus().hasExtension());
    }

    @Test
    public void requiresSystemAndRejectsUnknownSelections() {
        assertThrows(Exception.class, () -> convert(Map.of("Code", "00123")));
        assertThrows(Exception.class, () -> convert(Map.of("Klinischer Status", "made-up")));
        assertThrows(Exception.class, () -> convert(Map.of("Beginn", "!dar:made-up")));
        assertThrows(Exception.class, () -> convert(Map.of("Code", "A01", "Codesystem", "ICD-10-GM 2026",
                "Zusatzcode", "A02", "Zusatzcodesystem", "ICD-10-GM 2025")));
    }

    private Condition convert(Map<String, String> input) throws Exception {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("Patient-ID", "PID1");
        row.put("Fall-Nr", "1");
        for (ConditionConverter.Diagnosis_Columns column : ConditionConverter.Diagnosis_Columns.values()) {
            row.put(column.toString(), input.getOrDefault(column.toString(), ""));
        }
        StringWriter csv = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(csv, CSVFormat.DEFAULT)) {
            printer.printRecord(row.keySet());
            printer.printRecord(row.values());
        }
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                .setNullString("").build().parse(new StringReader(csv.toString()))) {
            ConverterOptions options = new ConverterOptions("");
            ConditionConverter converter = new ConditionConverter(parser.getRecords().get(0), null,
                    new ConverterResult(options), null, options);
            var resources = converter.convertInternal();
            assertEquals(1, resources.size());
            return (Condition) resources.get(0);
        }
    }
}
