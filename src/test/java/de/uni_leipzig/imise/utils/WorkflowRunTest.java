package de.uni_leipzig.imise.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class WorkflowRunTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void folderUsesSystemTime() throws Exception {
        checkClock("Asia/Kathmandu", null, "+0545");
    }

    @Test
    public void syntheaRunClockIsIndependentOfClinicalZone() throws Exception {
        checkClock("Europe/Berlin", "-0330", "-0330");
    }

    private void checkClock(String systemZone, String runOffset, String expectedOffset) throws Exception {
        TimeZone previous = TimeZone.getDefault();
        String previousOffset = System.getProperty("excel2fhir.runOffset");
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(systemZone));
            if (runOffset == null)
                System.clearProperty("excel2fhir.runOffset");
            else
                System.setProperty("excel2fhir.runOffset", runOffset);
            long before = System.currentTimeMillis() / 1000;
            WorkflowRun run = new WorkflowRun(temporary.newFolder(), null, "excel-to-fhir");
            long after = System.currentTimeMillis() / 1000;
            String name = run.directory.getFileName().toString();
            String stamp = name.substring(4, 21);
            LocalDateTime timestamp = LocalDateTime.parse(stamp,
                    DateTimeFormatter.ofPattern("yyyyMMdd_HH-mm-ss"));
            assertEquals("run-" + stamp + "-excel-to-fhir", name);
            long seconds = timestamp.toEpochSecond(ZoneOffset.of(expectedOffset));
            assertTrue(seconds >= before && seconds <= after);
        } finally {
            TimeZone.setDefault(previous);
            if (previousOffset == null)
                System.clearProperty("excel2fhir.runOffset");
            else
                System.setProperty("excel2fhir.runOffset", previousOffset);
        }
    }
}
