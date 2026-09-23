package de.uni_leipzig.life.csv2fhir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.io.StringWriter;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

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
        return ConverterOptions.fromText(text);
    }

    public static List<ConverterOptionSet> external(List<File> files) throws IOException {
        List<ConverterOptionSet> result = new ArrayList<>();
        for (File file : files) {
            result.add(new ConverterOptionSet(file.getName().replaceFirst("\\.[^.]+$", ""),
                    Files.readString(file.toPath())));
        }
        return checked(result);
    }

    public static List<ConverterOptionSet> workbook(File file) throws IOException {
        List<ConverterOptionSet> result = new ArrayList<>();
        try (var input = Files.newInputStream(file.toPath()); var book = new XSSFWorkbook(input)) {
            var formatter = new DataFormatter(Locale.GERMANY);
            var evaluator = book.getCreationHelper().createFormulaEvaluator();
            for (var sheet : book) {
                if (!isOptionsSheet(sheet.getSheetName())) continue;
                StringBuilder text = new StringBuilder();
                for (Row row : sheet) {
                    var cell = row.getCell(0);
                    text.append(cell == null ? "" : formatter.formatCellValue(cell, evaluator)).append('\n');
                }
                result.add(new ConverterOptionSet(sheet.getSheetName(), text.toString()));
            }
        }
        return checked(result);
    }

    public static List<ConverterOptionSet> csv(File directory, String prefix) throws IOException {
        List<ConverterOptionSet> result = new ArrayList<>();
        File[] files = directory.listFiles(File::isFile);
        if (files != null) {
            Arrays.sort(files);
            String base = prefix.replaceFirst("[-_]$", "");
            for (File file : files) {
                String name = file.getName();
                if (!name.endsWith(".csv")) continue;
                String stem = name.substring(0, name.length() - 4);
                String owner = Arrays.stream(files).map(File::getName)
                        .filter(n -> n.endsWith("Person.csv"))
                        .map(n -> n.substring(0, n.length() - "Person.csv".length()).replaceFirst("[-_]$", ""))
                        .filter(n -> matchesDataSet(stem, n))
                        .max(java.util.Comparator.comparingInt(String::length)).orElse(base);
                if (!owner.equals(base) || !matchesDataSet(stem, base)) continue;
                String sheet = stem.substring(base.length()).replaceFirst("^[-_]", "");
                if (isOptionsSheet(sheet)) result.add(new ConverterOptionSet(sheet, Files.readString(file.toPath())));
            }
        }
        return checked(result);
    }

    private static boolean matchesDataSet(String stem, String base) {
        return base.isEmpty() || stem.startsWith(base + "_") || stem.startsWith(base + "-")
                || stem.startsWith(base + "Konvertierungsoptionen");
    }

    private static List<ConverterOptionSet> checked(List<ConverterOptionSet> sets) {
        if (sets.isEmpty()) return List.of(new ConverterOptionSet("default", ""));
        var names = new java.util.HashSet<String>();
        for (var set : sets) {
            String name = set.directoryName();
            if (!names.add(name.toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("Option sets have the same output name: " + name);
        }
        return List.copyOf(sets);
    }

    public String directoryName() {
        String result = name.replaceAll("[^\\p{L}\\p{N}._-]", "_");
        if (result.isBlank() || result.equals(".") || result.equals(".."))
            throw new IllegalArgumentException("Invalid option set name: " + name);
        return result;
    }

    public void snapshot(Path directory) throws IOException {
        Files.createDirectories(directory);
        ConverterOptions options = options();
        Properties values = new Properties();
        for (var key : ConverterOptions.BooleanOption.values()) values.setProperty(key.name(), Boolean.toString(options.is(key)));
        for (var key : ConverterOptions.IntOption.values()) values.setProperty(key.name(), Integer.toString(options.getValue(key)));
        for (var key : ConverterOptions.StringOption.values()) values.setProperty(key.name(), options.getValue(key));
        StringWriter writer = new StringWriter();
        values.store(writer, "Converter options: " + name);
        Files.writeString(directory.resolve("converter-options.config"), writer.toString());
    }
}
