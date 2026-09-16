package de.uni_leipzig.life.csv2fhir;

import static org.junit.Assert.*;
import static de.uni_leipzig.life.csv2fhir.ConverterOptions.BooleanOption.*;
import org.junit.Test;
import java.nio.file.Files;
import java.util.List;

public class ConverterOptionsTest {
    @Test public void fileAndWorkbookTextUseTheSameParserAndCollectErrors() throws Exception {
        String text = "# Comment\nVALIDATE_STRICT=treu\nSTART_ID_CONDITION=abc\n"
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
            assertTrue(a.is(VALIDATE_STRICT));
        } finally { Files.delete(file); }
    }

    @Test public void acceptsExplicitBooleansAndPropertiesSyntax() {
        for (String value : List.of("true", "TRUE", "ja", "1")) {
            var o = ConverterOptions.fromText("VALIDATE_STRICT : " + value);
            assertTrue(o.getErrors().isEmpty()); assertTrue(o.is(VALIDATE_STRICT));
        }
        for (String value : List.of("false", "FALSE", "nein", "0")) {
            var o = ConverterOptions.fromText("VALIDATE_STRICT=" + value);
            assertTrue(o.getErrors().isEmpty()); assertFalse(o.is(VALIDATE_STRICT));
        }
        var o = ConverterOptions.fromText("PID_PREFIX=demo\\u002d\nPID_SUFFIX=-x\n"
                + "PID_LAST_NUMBER_INCREASE_INITIAL_OFFSET=10\nPID_LAST_NUMBER_INCREASE_LOOP_OFFSET=100");
        assertEquals("demo-p011-x", o.getFullPID("p001"));
        assertEquals("demo-p111-x", o.getFullPID("p001", 1));
    }

    @Test public void offsetsCannotWrapOrSilentlyIgnoreNegativeSettings() {
        assertFalse(ConverterOptions.fromText("PID_LAST_NUMBER_INCREASE_INITIAL_OFFSET=-1").getErrors().isEmpty());
        assertFalse(ConverterOptions.fromText("PID_LAST_NUMBER_INCREASE_LOOP_OFFSET=-1").getErrors().isEmpty());
        var o = ConverterOptions.fromText("PID_LAST_NUMBER_INCREASE_INITIAL_OFFSET=1");
        assertThrows(ArithmeticException.class, () -> o.getFullPID("p2147483647"));
        assertThrows(IllegalArgumentException.class, () -> o.getFullPID("noDigits"));
        var loop = ConverterOptions.fromText("PID_LAST_NUMBER_INCREASE_LOOP_OFFSET=2147483647");
        assertThrows(ArithmeticException.class, () -> loop.getFullPID("p1", 2));
    }
}
