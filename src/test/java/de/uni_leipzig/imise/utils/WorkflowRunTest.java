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

    @Test
    public void validationFindingsDoNotFailCompletedGeneration() throws Exception {
        for (String validation : java.util.List.of("ERROR", "WARNING", "NOT_CHECKED", "VALID")) {
            WorkflowRun run = new WorkflowRun(temporary.newFolder(), null, "test");
            java.nio.file.Files.writeString(run.staging.resolve("case.json"), "{}");
            java.nio.file.Files.writeString(run.staging.resolve("case.validation.json"),
                    "{\"status\":\"" + validation + "\"}");
            assertEquals(0, run.finish(false, validation.equals("ERROR") || validation.equals("NOT_CHECKED"), true));
            var outcome = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(run.directory.resolve("outcome.json"))).getAsJsonObject();
            assertEquals("succeeded", outcome.get("generation").getAsString());
            assertEquals(validation.equals("VALID") ? "passed" : validation.equals("NOT_CHECKED") ? "incomplete" : "findings", outcome.get("validation").getAsString());
            assertTrue(java.nio.file.Files.isRegularFile(run.directory.resolve("fhir/case.json")));
        }
        WorkflowRun unassessed = new WorkflowRun(temporary.newFolder(), null, "test");
        java.nio.file.Files.writeString(unassessed.staging.resolve("case.json"), "{}");
        assertEquals(0, unassessed.finish(false, false, true));
        assertTrue(java.nio.file.Files.readString(unassessed.directory.resolve("outcome.json")).contains("unavailable"));
        WorkflowRun failed = new WorkflowRun(temporary.newFolder(), null, "test");
        assertEquals(1, failed.finish(true, false, false));
    }

    private void checkClock(String systemZone, String runOffset, String expectedOffset) throws Exception {
        TimeZone previous = TimeZone.getDefault();
        String previousOffset = System.getProperty("synthifhir.runOffset");
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(systemZone));
            if (runOffset == null)
                System.clearProperty("synthifhir.runOffset");
            else
                System.setProperty("synthifhir.runOffset", runOffset);
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
                System.clearProperty("synthifhir.runOffset");
            else
                System.setProperty("synthifhir.runOffset", previousOffset);
        }
    }
}
