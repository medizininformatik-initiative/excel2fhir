package de.uni_leipzig.life.csv2fhir;

import static org.junit.Assert.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.ZipFile;
import org.hl7.fhir.r4.model.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MultiSinglePatientBundlesFileWriterTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private Bundle bundle(String id) {
        Patient patient = new Patient(); patient.setId(id);
        patient.addName().setFamily("Groß").addGiven("Jörg");
        patient.addExtension().setUrl("urn:test:text")
                .setValue(new StringType("ÄÖÜ ß 😀 \\\"\n\r\t".repeat(10000)));
        Observation observation = new Observation(); observation.setId("observation-" + id);
        observation.setValue(new Quantity().setValue(new java.math.BigDecimal("0.123456789012345678900")));
        Bundle result = new Bundle().setType(Bundle.BundleType.TRANSACTION);
        result.addEntry().setResource(patient);
        result.addEntry().setResource(observation);
        return result;
    }

    @Test public void serializationPreservesBytesAcrossEntriesAndReset() throws Exception {
        var directory = temporary.newFolder().toPath();
        var writer = MultiSinglePatientBundlesFileWriter.create(directory.toFile(), "case_", null,
                OutputFileType.NDJSON, OutputFileType.ZIPJSON);
        for (int group = 0; group < 2; group++) {
            var bundles = List.of(bundle("p" + group + "a"), bundle("p" + group + "b"));
            StringBuilder expectedNdjson = new StringBuilder();
            for (Bundle bundle : bundles) {
                expectedNdjson.append(OutputFileType.NDJSON.getParser().setPrettyPrint(false)
                        .encodeResourceToString(bundle)).append(System.lineSeparator());
                writer.appendBundle(bundle);
            }
            String suffix = "group" + group;
            writer.closeWriterAndRenameOrDeleteIfEmpty(suffix);
            assertArrayEquals(expectedNdjson.toString().getBytes(UTF_8),
                    Files.readAllBytes(directory.resolve("case_" + suffix + ".ndjson")));
            try (ZipFile archive = new ZipFile(directory.resolve("case_" + suffix + ".json.zip").toFile())) {
                assertEquals(2, archive.size());
                for (Bundle bundle : bundles) {
                    String id = bundle.getEntryFirstRep().getResource().getId();
                    byte[] expected = OutputFileType.ZIPJSON.getParser().setPrettyPrint(true)
                            .encodeResourceToString(bundle).getBytes(UTF_8);
                    try (var input = archive.getInputStream(archive.getEntry("case_" + id + ".json"))) {
                        assertArrayEquals(expected, input.readAllBytes());
                    }
                }
            }
            if (group == 0) writer.reset();
        }
    }
}
