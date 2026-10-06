package de.uni_leipzig.life.csv2fhir;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

/** Export one existing representation per folder for upload preparation only. */
public final class DatasetUploadExport {
    private static final ObjectMapper JSON = new ObjectMapper().enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    private static void json(InputStream input, BufferedWriter output) throws Exception {
        try (var parser = JSON.getFactory().createParser(input).disable(JsonParser.Feature.AUTO_CLOSE_SOURCE)) {
            while (parser.nextToken() != null) {
                var node = JSON.readTree(parser);
                if (!node.isObject()) throw new IllegalArgumentException("Expected a FHIR object");
                output.write(node.toString());
                output.newLine();
            }
        }
    }

    public static void export(Path root, Path destination) throws Exception {
        try (var output = Files.newBufferedWriter(destination)) {
            for (Path path : DatasetInspection.selectedFiles(root)) {
                String name = path.toString();
                try (var input = Files.newInputStream(path)) {
                    if (name.endsWith(".xml")) {
                        var context = FhirContext.forR4Cached();
                        var resource = context.newXmlParser().parseResource(input);
                        output.write(context.newJsonParser().encodeResourceToString(resource));
                        output.newLine();
                    } else if (name.endsWith(".json.gz")) {
                        try (var expanded = new GZIPInputStream(input)) { json(expanded, output); }
                    } else if (name.endsWith(".json.bz2")) {
                        try (var expanded = new BZip2CompressorInputStream(input)) { json(expanded, output); }
                    } else if (name.endsWith(".json.zip")) {
                        try (var archive = new ZipInputStream(input)) {
                            for (var entry = archive.getNextEntry(); entry != null; entry = archive.getNextEntry())
                                if (!entry.isDirectory() && entry.getName().endsWith(".json")) json(archive, output);
                        }
                    } else json(input, output);
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        export(Path.of(args[0]), Path.of(args[1]));
    }
}
