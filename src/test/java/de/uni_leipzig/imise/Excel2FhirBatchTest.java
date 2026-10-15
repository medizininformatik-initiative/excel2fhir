package de.uni_leipzig.imise;

import static org.junit.Assert.*;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import de.uni_leipzig.UcumMapper;

public class Excel2FhirBatchTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private Path execute(String name, Path input, Path config) throws Exception {
        Path root = temporary.newFolder(name).toPath();
        var job = new JsonObject();
        job.add("arguments", new Gson().toJsonTree(new String[]{"-f", input.toString(), "-o", root.toString(),
                "-p", "1000", "--converter-options", config.toString()}));
        job.addProperty("log", root.resolve("conversion.log").toString());
        job.addProperty("result", root.resolve("result.json").toString());
        UcumMapper.invalidUcumCodes.add("previous-case-warning");
        Excel2FhirBatch.execute(job);
        assertTrue(UcumMapper.invalidUcumCodes.isEmpty());
        return root;
    }

    private Map<String, String> outputs(Path root) throws Exception {
        Map<String, String> values = new TreeMap<>();
        try (var paths = Files.walk(root)) {
            for (Path p : paths.filter(Files::isRegularFile).filter(p -> p.toString().contains("/fhir/")).toList())
                values.put(p.getFileName().toString(), Files.readString(p));
        }
        assertFalse(values.isEmpty());
        return values;
    }

    @Test public void isolatesOptionsFailuresLogsAndResourceIdsBetweenCases() throws Exception {
        Path input = Path.of("FHIR_Testdatengenerator_Vorlage.xlsx");
        Path a = temporary.newFile("a.config").toPath(), b = temporary.newFile("b.config").toPath();
        Files.writeString(a, "CONFIGURATION_VERSION=1\nPID_PREFIX=first-\n");
        Files.writeString(b, "CONFIGURATION_VERSION=1\nPID_PREFIX=second-\n");
        Path first = execute("first", input, a);
        String originalLog = Files.readString(first.resolve("conversion.log"));
        var expected = outputs(first);
        Path failed = execute("failed", Path.of("missing-workbook.xlsx"), b);
        assertNotEquals(0, JsonParser.parseString(Files.readString(failed.resolve("result.json")))
                .getAsJsonObject().get("exitCode").getAsInt());
        Path second = execute("second", input, b);
        assertNotEquals(expected, outputs(second));
        Path repeated = execute("repeated", input, a);
        assertEquals(expected, outputs(repeated));
        assertEquals(originalLog, Files.readString(first.resolve("conversion.log")));
        assertFalse(originalLog.contains("previous-case-warning"));
        for (Path root : List.of(first, second, repeated))
            assertEquals(0, JsonParser.parseString(Files.readString(root.resolve("result.json")))
                    .getAsJsonObject().get("exitCode").getAsInt());
    }
}
