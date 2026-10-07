package de.uni_leipzig.life.csv2fhir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.io.StringWriter;


/** An independently selectable converter dialect, using the shared defaults. */
public final class ConverterOptionSet {
    private final String name;
    private final String text;

    public ConverterOptionSet(String name, String text) {
        this.name = name;
        this.text = text;
    }

    public String name() { return name; }

    public static boolean isOptionsSheet(String name) {
        return name.contains("Konvertierungsoptionen");
    }

    public ConverterOptions options() {
        return text.isBlank() ? ConverterOptions.fromText(ContractConfiguration.defaultProperties()).withCommandLineExecution()
                : ConverterOptions.fromText(text);
    }

    public static List<ConverterOptionSet> external(List<File> files) throws IOException {
        List<ConverterOptionSet> result = new ArrayList<>();
        for (File file : files) {
            result.add(new ConverterOptionSet(file.getName().replaceFirst("\\.[^.]+$", ""),
                    Files.readString(file.toPath())));
        }
        return checked(result);
    }

    public static List<ConverterOptionSet> defaults() {
        return List.of(new ConverterOptionSet("default", ""));
    }

    private static List<ConverterOptionSet> checked(List<ConverterOptionSet> sets) {
        if (sets.size() > 1) throw new IllegalArgumentException("Choose one converter configuration per run");
        return sets.isEmpty() ? defaults() : List.copyOf(sets);
    }

    public String directoryName() {
        String result = name.replaceAll("[^\\p{L}\\p{N}._-]", "_");
        if (result.isBlank() || result.equals(".") || result.equals(".."))
            throw new IllegalArgumentException("Invalid option set name: " + name);
        return result;
    }

    public void snapshot(Path directory) throws IOException {
        snapshot(directory, 1, false, new OutputFileType[] {OutputFileType.JSON, OutputFileType.NDJSON});
    }

    public void snapshot(Path directory, int patients, boolean validation, OutputFileType[] formats) throws IOException {
        if (text.isBlank()) {
            Properties values = new Properties();
            values.load(new java.io.StringReader(ContractConfiguration.defaultProperties()));
            values.setProperty("OUTPUT_PATIENTS_PER_FILE", Integer.toString(patients));
            values.setProperty("CHECKS_FHIR_VALIDATION", Boolean.toString(validation));
            values.setProperty("OUTPUT_FORMATS", java.util.Arrays.stream(formats).map(Enum::name).collect(java.util.stream.Collectors.joining(",")));
            Files.createDirectories(directory);
            var writer = new StringWriter();
            values.store(writer, "Effective converter configuration");
            Files.writeString(directory.resolve("converter-options.config"), writer.toString());
            return;
        }
        Files.createDirectories(directory);
        ConverterOptions options = options();
        if (ContractConfiguration.isContractText(text)) {
            // Preserve version, all selections and inactive comments, not just legacy bindings.
            Files.writeString(directory.resolve("converter-options.config"), text);
            return;
        }
        Properties values = new Properties();
        for (var key : ConverterOptions.BooleanOption.values()) values.setProperty(key.name(), Boolean.toString(options.is(key)));
        for (var key : ConverterOptions.IntOption.values()) values.setProperty(key.name(), Integer.toString(options.getValue(key)));
        for (var key : ConverterOptions.StringOption.values()) values.setProperty(key.name(), options.getValue(key));
        StringWriter writer = new StringWriter();
        values.store(writer, "Converter options: " + name);
        Files.writeString(directory.resolve("converter-options.config"), writer.toString());
    }
}
