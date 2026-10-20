package de.uni_leipzig.life.synthifhir;

import static org.junit.Assert.*;
import java.nio.file.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

/** Saved settings must resolve to the execution choices used for their run. */
public class ExecutionSettingsTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private ConverterOptions snapshot(ConverterOptionSet set) throws Exception {
        Path directory = temp.newFolder().toPath();
        set.snapshot(directory, 7, true, new OutputFileType[] {OutputFileType.XML});
        var saved = ConverterOptions.fromText(Files.readString(directory.resolve("converter-options.config")));
        assertTrue(saved.getErrors().toString(), saved.getErrors().isEmpty());
        return saved;
    }

    @Test public void defaultSnapshotRetainsCliExecutionEvenWithDifferentReplayFlags() throws Exception {
        var saved = snapshot(ConverterOptionSet.defaults().get(0));
        assertEquals(7, saved.patientsPerFile(1));
        assertTrue(saved.validationEnabled(false));
        assertArrayEquals(new OutputFileType[] {OutputFileType.XML}, saved.outputFormats(new OutputFileType[] {OutputFileType.JSON}));
    }

    @Test public void versionedSnapshotRetainsSelectionsAndTheirDefaultsOverCliFlags() throws Exception {
        for (String text : new String[] {"CONFIGURATION_VERSION=1\n", "CONFIGURATION_VERSION=1\nOUTPUT_FORMATS=NDJSON\nOUTPUT_PATIENTS_PER_FILE=2\nCHECKS_FHIR_VALIDATION=false\n"}) {
            var original = ConverterOptions.fromText(text);
            var saved = snapshot(new ConverterOptionSet("configured", text));
            assertEquals(original.patientsPerFile(7), saved.patientsPerFile(9));
            assertEquals(original.validationEnabled(true), saved.validationEnabled(true));
            assertArrayEquals(original.outputFormats(new OutputFileType[] {OutputFileType.XML}),
                    saved.outputFormats(new OutputFileType[] {OutputFileType.XML}));
        }
    }

    @Test public void suppliedConfigurationsRequireCurrentVersionBeforeSnapshot() throws Exception {
        for (String text : new String[] {"", "# comment\n", "PID_PREFIX=site-\n", "OUTPUT_FORMATS=JSON\n",
                "CONFIGURATION_VERSION=2\n", "CONFIGURATION_VERSION=1\nOUTPUT_FORMATS=invalid\n"}) {
            Path file = temp.newFile().toPath();
            Files.writeString(file, text);
            assertFalse(new ConverterOptions(file.toString()).getErrors().isEmpty());
            assertThrows(IllegalArgumentException.class, () -> ConverterOptionSet.external(java.util.List.of(file.toFile())));
            var set = new ConverterOptionSet("supplied", text);
            assertFalse(set.options().getErrors().isEmpty());
            Path destination = temp.getRoot().toPath().resolve("rejected-snapshot");
            assertThrows(IllegalArgumentException.class, () -> set.snapshot(destination));
            assertFalse(Files.exists(destination));
        }
    }

}
