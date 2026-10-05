package de.uni_leipzig.life.csv2fhir;

import java.nio.file.Files;
import java.nio.file.Path;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Read-only inspection using the CSV converter's grouping and parsing rules. */
public final class CsvInputPreflight {
    private CsvInputPreflight() {}

    public static ObjectNode inspect(Path directory) throws Exception {
        var result = new ObjectMapper().createObjectNode();
        var issues = result.putArray("issues");
        var tables = result.putArray("sheets");
        var groups = result.putArray("groups");
        int errors = 0;
        try {
            for (String prefix : Main.inputPrefixes(directory.toFile())) {
                groups.add(prefix);
                var converter = new Csv2Fhir(directory.toFile(), directory.toFile(), prefix, null,
                        ConverterOptions.fromText("CHECK_INPUT_CONSISTENCY=false\n"));
                var report = converter.inspectInputs();
                errors += report.issues.size();
                for (var issue : report.issues) {
                    if (issues.size() < 100) issues.add(prefix + ": " + issue.category + ": " + issue.reason);
                }
                var files = new java.util.HashSet<String>();
                report.tables.values().forEach(table -> {
                    if (files.add(table.file)) tables.addObject().put("name", table.file).put("rows", table.rowsRead);
                });
            }
        } catch (IllegalArgumentException error) {
            errors++;
            issues.add(error.getMessage());
        }
        result.put("valid", errors == 0).put("errors", errors).put("warnings", 0);
        return result;
    }

    public static void main(String[] args) throws Exception {
        Files.writeString(Path.of(args[1]), inspect(Path.of(args[0])).toString());
    }
}
