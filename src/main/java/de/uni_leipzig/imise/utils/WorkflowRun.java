package de.uni_leipzig.imise.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.google.gson.JsonParser;

/**
 * Owns a fresh CLI run; converter intermediates never share a previous run's
 * directory.
 */
public final class WorkflowRun {
    public final Path directory;
    public final Path staging;
    public final Path csv;

    public WorkflowRun(File outputRoot, File csvRoot, String operation) throws IOException {
        Path root = (outputRoot == null ? Path.of("outputGlobal") : outputRoot.toPath()).toAbsolutePath();
        Files.createDirectories(root);
        // Synthea supplies the system offset separately from its clinical time zone.
        String offset = System.getProperty("excel2fhir.runOffset");
        ZoneId zone = offset == null ? ZoneId.systemDefault() : ZoneOffset.of(offset);
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HH-mm-ss").format(ZonedDateTime.now(zone));
        String name = "run-" + stamp + "-" + operation;
        Path candidate = root.resolve(name);
        for (int suffix = 2;; suffix++) {
            try {
                Files.createDirectory(candidate);
                break;
            } catch (java.nio.file.FileAlreadyExistsException e) {
                candidate = root.resolve(name + "-" + suffix);
            }
        }
        directory = candidate;
        staging = Files.createDirectories(directory.resolve("details/pending"));
        Files.createDirectories(directory.resolve("details/reports"));
        Files.createDirectories(directory.resolve("details/logs"));
        csv = csvRoot == null ? directory.resolve("details/csv")
                : csvRoot.toPath().toAbsolutePath().resolve(directory.getFileName());
        // Do not reuse or clear a user-supplied CSV directory.
        if (csvRoot != null)
            Files.createDirectories(csv.getParent());
        Files.createDirectory(csv);
        status("RUNNING", "Conversion in progress.");
        System.out.println("Run: " + directory);
    }

    public void status(String status, String detail) throws IOException {
        Files.writeString(directory.resolve("status.txt"), status + "\n" + detail + "\n"
                + "FHIR: fhir/\nReports: details/reports/\nCSV: " + csv + "\n");
    }

    public int finish(boolean importProblems, boolean validationProblems, boolean validationRequested)
            throws IOException {
        List<Path> files;
        try (var stream = Files.walk(staging)) {
            files = stream.filter(Files::isRegularFile).sorted().toList();
        }
        boolean unchecked = false;
        boolean invalid = false;
        for (Path file : files) {
            String name = file.getFileName().toString();
            if (name.endsWith(".validation.json")) {
                var report = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                String state = report.get("status").getAsString();
                unchecked |= state.equals("NOT_CHECKED");
                invalid |= !List.of("VALID", "WARNING", "IGNORED", "NOT_CHECKED").contains(state);
            }
            if (name.endsWith(".import.json") || name.endsWith(".validation.json")) {
                Path target = directory.resolve("details/reports").resolve(staging.relativize(file));
                Files.createDirectories(target.getParent());
                Files.move(file, target);
            }
        }
        importProblems |= files.stream().noneMatch(Files::exists);
        if (!importProblems) {
            Path fhir = Files.createDirectory(directory.resolve("fhir"));
            List<Path> outputs = files.stream().filter(Files::exists).toList();
            List<Path> ndjson = outputs.stream().filter(p -> p.toString().endsWith(".ndjson")).toList();
            var groups = ndjson.stream().collect(java.util.stream.Collectors.groupingBy(Path::getParent));
            for (var group : groups.entrySet()) {
                Path target = fhir.resolve(staging.relativize(group.getKey())).resolve("patients.ndjson");
                Files.createDirectories(target.getParent());
                try (var destination = Files.newOutputStream(target)) {
                    for (Path file : group.getValue()) Files.copy(file, destination);
                }
                for (Path file : group.getValue()) Files.delete(file);
            }
            for (Path file : outputs) {
                if (!Files.exists(file))
                    continue;
                Path target = fhir.resolve(staging.relativize(file));
                Files.createDirectories(target.getParent());
                Files.move(file, target);
            }
        }
        String state = importProblems || invalid ? "FAILED"
                : unchecked ? "NOT_CHECKED"
                        : validationProblems ? "FAILED" : validationRequested ? "COMPLETE" : "NOT_VALIDATED";
        status(state, importProblems ? "Incomplete import. Diagnostic files remain in details/pending/."
                : unchecked ? "Import complete; terminology checks could not all be performed."
                        : "Import complete. Validation: " + state);
        System.out.println(state + ": " + directory.resolve("status.txt"));
        return state.equals("COMPLETE") || state.equals("NOT_VALIDATED") ? 0 : 1;
    }

    public void fail(Exception error) throws IOException {
        Path fhir = directory.resolve("fhir");
        if (Files.exists(fhir))
            Files.move(fhir, staging.resolve("unpublished"));
        status("FAILED", error.toString());
    }
}
