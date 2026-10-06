package de.uni_leipzig.life.csv2fhir;

import static org.junit.Assert.*;
import java.nio.file.*;
import java.util.zip.*;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;

public class DatasetUploadExportTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final String PATIENT = "{\"resourceType\":\"Patient\",\"id\":\"one\"}";

    @Test public void selectsOneRepresentationPerFolderAndPreservesDecimals() throws Exception {
        Path root = temporary.newFolder().toPath();
        Files.writeString(root.resolve("one.json"), PATIENT);
        Files.writeString(root.resolve("all.ndjson"), PATIENT + "\n" + PATIENT);
        Path nested = Files.createDirectory(root.resolve("nested"));
        Files.writeString(nested.resolve("observation.ndjson"), "{\"resourceType\":\"Observation\",\"id\":\"two\",\"valueQuantity\":{\"value\":0.1234567890123456789}}\n");
        Path output = temporary.newFile().toPath();
        DatasetUploadExport.export(root, output);
        var lines = Files.readAllLines(output);
        assertEquals(2, lines.size());
        assertTrue(lines.stream().anyMatch(line -> line.contains("0.1234567890123456789")));
    }

    @Test public void readsXmlAndCompressedFormats() throws Exception {
        for (String suffix : new String[]{".json.gz", ".json.bz2", ".json.zip", ".xml"}) {
            Path root = temporary.newFolder().toPath();
            Path file = root.resolve("patient" + suffix);
            if (suffix.equals(".xml")) Files.writeString(file, "<Patient xmlns=\"http://hl7.org/fhir\"><id value=\"one\"/></Patient>");
            else if (suffix.equals(".json.gz")) {
                try (var out = new GZIPOutputStream(Files.newOutputStream(file))) { out.write(PATIENT.getBytes()); }
            } else if (suffix.equals(".json.bz2")) {
                try (var out = new BZip2CompressorOutputStream(Files.newOutputStream(file))) { out.write(PATIENT.getBytes()); }
            } else {
                try (var out = new ZipOutputStream(Files.newOutputStream(file))) {
                    out.putNextEntry(new ZipEntry("one.json")); out.write(PATIENT.getBytes()); out.closeEntry();
                }
            }
            Path output = temporary.newFile().toPath();
            DatasetUploadExport.export(root, output);
            assertEquals(PATIENT, Files.readString(output).strip());
        }
    }
}
