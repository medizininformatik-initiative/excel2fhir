package de.uni_leipzig.life.csv2fhir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Streams one representation per output folder and counts unique resource IDs. */
public final class DatasetInspection {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> SUFFIXES = List.of(".json", ".ndjson", ".json.gz", ".json.bz2", ".json.zip", ".xml");
    private final Map<String, Set<String>> ids = new TreeMap<>();
    private long instances, missingIds, files;
    private final Set<String> formats = new TreeSet<>();

    private void resource(String type, String id) {
        if (type == null || type.equals("Bundle")) return;
        instances++;
        if (id == null || id.isBlank()) missingIds++;
        else ids.computeIfAbsent(type, unused -> new HashSet<>()).add(id);
    }

    private void object(JsonParser parser, boolean entries) throws Exception {
        String type = null, id = null;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() == null) throw new IllegalArgumentException("Truncated JSON object");
            String field = parser.currentName();
            JsonToken token = parser.nextToken();
            if (field.equals("resourceType")) type = parser.getValueAsString();
            else if (field.equals("id")) id = parser.getValueAsString();
            else if (entries && field.equals("entry") && token == JsonToken.START_ARRAY) {
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    if (parser.currentToken() != JsonToken.START_OBJECT) throw new IllegalArgumentException("Invalid bundle entry");
                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        String name = parser.currentName();
                        token = parser.nextToken();
                        if (name.equals("resource") && token == JsonToken.START_OBJECT) object(parser, false);
                        else parser.skipChildren();
                    }
                }
            } else parser.skipChildren();
        }
        resource(type, id);
    }

    private void json(InputStream input) throws Exception {
        try (var parser = JSON.getFactory().createParser(input).disable(JsonParser.Feature.AUTO_CLOSE_SOURCE)) {
            while (parser.nextToken() != null) {
                if (parser.currentToken() != JsonToken.START_OBJECT) throw new IllegalArgumentException("Expected FHIR JSON object");
                object(parser, true);
            }
        }
    }

    private void xml(InputStream input) throws Exception {
        var factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        var parser = factory.createXMLStreamReader(input);
        int depth = 0, resourceDepth = -1;
        boolean bundle = false;
        var elements = new ArrayList<String>();
        String type = null, id = null;
        try {
            while (parser.hasNext()) {
                int event = parser.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    depth++;
                    elements.add(parser.getLocalName());
                    if (depth == 1) bundle = parser.getLocalName().equals("Bundle");
                    if ((!bundle && depth == 1) || (bundle && depth == 4 && elements.get(1).equals("entry") && elements.get(2).equals("resource"))) {
                        resourceDepth = depth; type = parser.getLocalName(); id = null;
                    } else if (depth == resourceDepth + 1 && parser.getLocalName().equals("id")) {
                        id = parser.getAttributeValue(null, "value");
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if (depth == resourceDepth) { resource(type, id); resourceDepth = -1; }
                    elements.remove(elements.size() - 1);
                    depth--;
                }
            }
        } finally { parser.close(); }
    }

    private void file(Path path, String suffix) throws Exception {
        files++;
        formats.add(suffix.substring(1));
        try (var input = Files.newInputStream(path)) {
            if (suffix.equals(".xml")) xml(input);
            else if (suffix.equals(".json.gz")) {
                try (var expanded = new GZIPInputStream(input)) { json(expanded); }
            } else if (suffix.equals(".json.bz2")) {
                try (var expanded = new BZip2CompressorInputStream(input)) { json(expanded); }
            } else if (suffix.equals(".json.zip")) {
                try (var archive = new ZipInputStream(input)) {
                    for (var entry = archive.getNextEntry(); entry != null; entry = archive.getNextEntry())
                        if (!entry.isDirectory() && entry.getName().endsWith(".json")) json(archive);
                }
            } else json(input);
        }
    }

    public static ObjectNode inspect(Path root) throws Exception {
        var scan = new DatasetInspection();
        Map<Path, List<Path>> folders = new TreeMap<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                if (Files.isSymbolicLink(path)) throw new IllegalArgumentException("Symbolic links are not dataset files");
                folders.computeIfAbsent(path.getParent(), unused -> new ArrayList<>()).add(path);
            }
        }
        for (var folder : folders.values()) {
            for (String suffix : SUFFIXES) {
                var selected = folder.stream().filter(path -> path.toString().endsWith(suffix)).toList();
                if (selected.isEmpty()) continue;
                for (Path path : selected) scan.file(path, suffix);
                break;
            }
        }
        var result = JSON.createObjectNode();
        var counts = result.putObject("resourceCounts");
        scan.ids.forEach((type, ids) -> counts.put(type, ids.size()));
        long unique = scan.ids.values().stream().mapToLong(Set::size).sum();
        result.put("patients", scan.ids.getOrDefault("Patient", Set.of()).size());
        result.put("uniqueResources", unique).put("resourceInstances", scan.instances).put("missingIds", scan.missingIds);
        result.put("repeatedIds", scan.instances - scan.missingIds - unique).put("inspectedFiles", scan.files);
        result.set("inspectedFormats", JSON.valueToTree(scan.formats));
        return result;
    }

    public static void main(String[] args) throws Exception {
        Files.writeString(Path.of(args[1]), inspect(Path.of(args[0])).toString());
    }
}
