package de.uni_leipzig.life.csv2fhir;

import static org.junit.Assert.*;
import static de.uni_leipzig.life.csv2fhir.ConverterOptions.BooleanOption.*;
import org.junit.Test;
import java.nio.file.Files;
import java.util.List;

public class ConverterOptionsTest {
    @Test public void syntheaYearAndVersionPolicyAreValidatedIndependently() {
        for (int year : List.of(2025, 2026)) {
            for (String mode : List.of("Jahr", "", "Unbekannt (Data Absent Reason)", "!dar:masked", "!dar:invalid")) {
                var options = ConverterOptions.fromText("SYNTHEA_MAPPING_YEAR=" + year + "\nSYNTHEA_VERSION_OUTPUT=" + mode);
                assertTrue(options.getErrors().toString(), options.getErrors().isEmpty());
                assertEquals(year, options.getValue(ConverterOptions.IntOption.SYNTHEA_MAPPING_YEAR));
                assertEquals(mode, options.getValue(ConverterOptions.StringOption.SYNTHEA_VERSION_OUTPUT));
            }
        }
        for (String text : List.of("SYNTHEA_MAPPING_YEAR=2024", "SYNTHEA_MAPPING_YEAR=2027",
                "SYNTHEA_MAPPING_YEAR=2025,2026", "SYNTHEA_VERSION_OUTPUT=anything", "SYNTHEA_VERSION_OUTPUT=!dar:")) {
            assertFalse(text, ConverterOptions.fromText(text).getErrors().isEmpty());
        }
    }

    @Test public void fileAndWorkbookTextUseTheSameParserAndCollectErrors() throws Exception {
        String text = "# Comment\nCHECK_INPUT_CONSISTENCY=treu\nSTART_ID_CONDITION=abc\n"
                + "PID_LAST_NUMBER_INCREASE_LOOP_COUNT=-1\n"
                + "PID_PREFIX=first\nPID_PREFIX=second\n";
        var file = Files.createTempFile("converter-options-", ".config");
        try {
            Files.writeString(file, text);
            var a = ConverterOptions.fromText(text);
            var b = new ConverterOptions(file.toString());
            assertEquals(4, a.getErrors().size());
            assertEquals(a.getErrors(), b.getErrors());
            // Invalid input must not silently turn validation off.
            assertTrue(a.is(CHECK_INPUT_CONSISTENCY));
        } finally { Files.delete(file); }
    }

    @Test public void acceptsExplicitBooleansAndPropertiesSyntax() {
        for (String value : List.of("true", "TRUE", "ja", "1")) {
            var o = ConverterOptions.fromText("CHECK_INPUT_CONSISTENCY : " + value);
            assertTrue(o.getErrors().isEmpty()); assertTrue(o.is(CHECK_INPUT_CONSISTENCY));
        }
        for (String value : List.of("false", "FALSE", "nein", "0")) {
            var o = ConverterOptions.fromText("CHECK_INPUT_CONSISTENCY=" + value);
            assertTrue(o.getErrors().isEmpty()); assertFalse(o.is(CHECK_INPUT_CONSISTENCY));
        }
        var o = ConverterOptions.fromText("PID_PREFIX=demo-\nPID_SUFFIX=-x");
        assertEquals("demo-p001-x", o.getFullPID("p001"));
        assertEquals("demo-p001-x-rep-1", o.getFullPID("p001", 1));
    }

    @Test public void filenamesPreserveUnderscoresWhileResourceIdsUseHyphens() {
        var options = ConverterOptions.fromText("PID_PREFIX=demo_\nPID_SUFFIX=_x");
        assertEquals("demo_p_001_x", options.getFullPIDForFileName("p_001"));
        assertEquals("demo-p-001-x-rep-2", options.getFullPID("p_001", 2));
    }

    @Test public void repetitionsSupportUuidsLargeNumbersAndTextWithoutArithmetic() {
        var options = ConverterOptions.fromText("");
        for (String id : List.of("noDigits", "p2147483647", "550e8400-e29b-41d4-a716-446655440000")) {
            assertEquals(id, options.getFullPID(id, 0));
            assertEquals(id + "-rep-2", options.getFullPID(id, 2));
        }
        assertThrows(IllegalArgumentException.class, () -> options.getFullPID("p1", -1));
        assertFalse(ConverterOptions.fromText("PID_LAST_NUMBER_INCREASE_LOOP_OFFSET=1").getErrors().isEmpty());
        assertTrue(ConverterOptions.fromText("PID_LAST_NUMBER_INCREASE_INITIAL_OFFSET=0").getErrors().isEmpty());
    }
    @Test public void explicitOptionsOverrideWorkflowDefaultsWithoutHidingDuplicates() {
        var defaults = java.util.Map.of("SET_REFERENCE_FROM_CONDITION_TO_ENCOUNTER", "true", "PID_PREFIX", "workflow-");
        var options = ConverterOptions.fromText("SET_REFERENCE_FROM_CONDITION_TO_ENCOUNTER=false\n", defaults);
        assertTrue(options.getErrors().isEmpty());
        assertFalse(options.is(SET_REFERENCE_FROM_CONDITION_TO_ENCOUNTER));
        assertEquals("workflow-p1", options.getFullPID("p1"));
        assertFalse(ConverterOptions.fromText("PID_PREFIX=a\nPID_PREFIX=b", defaults).getErrors().isEmpty());
    }
}
