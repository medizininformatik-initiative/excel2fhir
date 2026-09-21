package de.uni_leipzig.life.csv2fhir;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import de.uni_leipzig.imise.utils.WorkflowRun;
import de.uni_leipzig.imise.utils.FileLogger;
import java.util.concurrent.Callable;

import de.uni_leipzig.imise.validate.FHIRValidator;
import de.uni_leipzig.imise.validate.FHIRValidator.ValidationResultType;
import picocli.CommandLine;
import picocli.CommandLine.Option;

/**
 * @author fheuschkel (02.11.2020)
 */
@CommandLine.Command(name = "csv2fhir", mixinStandardHelpOptions = true, version = "1.0", description = "Converts a directory containing multiple csv files into a json bundle.")
public class Main implements Callable<Integer> {

    /**
     *
     */
    @CommandLine.Option(names = {
            "-i", "--input-directory"
    }, paramLabel = "INPUT-DIRECTORY", description = "CSV input directory. Default: input in the working directory.")
    File inputDirectory = new File("input");

    @Option(names = { "-o", "--output-directory" }, description = "Output root for fresh runs. Default: outputGlobal.")
    File outputDirectory;

    @Option(names = "--converter-options", paramLabel = "FILE", description = "External converter options; repeat for multiple variants.")
    List<File> converterOptions = new java.util.ArrayList<>();

    @Option(names = { "-r",
            "--result-file-format" }, split = ",", description = "Output formats. Default: JSON,NDJSON.")
    OutputFileType[] outputFileTypes = { OutputFileType.JSON, OutputFileType.NDJSON };

    @Option(names = { "-p", "--patients-count" }, description = "Maximum number of patients per JSON bundle.")
    int patientsPerBundle = Integer.MAX_VALUE;

    /**
     *
     */
    @Option(names = { "-v",
            "--validate-bundles" }, negatable = true, defaultValue = "false", fallbackValue = "true", paramLabel = "VALIDATE-BUNDLES", description = "Enables FHIR bundle validation (default: disabled), writes reports and exits nonzero on errors or incomplete checks.")
    boolean validateBundles = false;

    @Option(names = { "-vll",
            "--validation-log-level" }, paramLabel = "VALIDATION-LOG-LEVEL", description = "Sets the log level for validation. Default ist ERROR. Other values are IGNORED, WARNING or VALID")
    ValidationResultType minLogLevel = ValidationResultType.ERROR;

    /**
     * @param args
     */
    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new Main()).setExecutionExceptionHandler(new PrintExceptionMessageHandler());
        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        if (patientsPerBundle < 1)
            throw new IllegalArgumentException("-p must be positive.");
        File[] persons = inputDirectory.listFiles(f -> f.isFile() && f.getName().endsWith("Person.csv"));
        if (persons == null || persons.length == 0) {
            throw new IllegalArgumentException("No CSV data sets (*Person.csv) in " + inputDirectory);
        }
        Arrays.sort(persons);
        List<String> prefixes = Arrays.stream(persons).map(f -> f.getName().substring(0,
                f.getName().length() - "Person.csv".length())).toList();
        // The converter accepts separator variants. Reject ambiguous sets instead of
        // converting them twice.
        if (prefixes.stream().map(p -> p.replaceFirst("[-_]$", "")).distinct().count() != prefixes.size()) {
            throw new IllegalArgumentException("Ambiguous CSV prefixes: " + prefixes);
        }
        WorkflowRun run = new WorkflowRun(outputDirectory, null, "csv-to-fhir");
        FileLogger.addRootFileLogger(run.directory.resolve("details/logs/conversion.log").toFile(),
                FileLogger.LogContentLayout.DATE_LEVEL_SOURCE_LINENUMBER);
        try {
            FHIRValidator validator = validateBundles ? createValidator() : null;
            boolean importProblems = false;
            for (String prefix : prefixes) {
                var sets = converterOptions.isEmpty() ? ConverterOptionSet.csv(inputDirectory, prefix)
                        : ConverterOptionSet.external(converterOptions);
                for (var set : sets) {
                    var destination = run.staging.resolve(set.directoryName());
                    if (prefixes.size() > 1) destination = destination.resolve(prefix + "Person");
                    Files.createDirectories(destination);
                    set.snapshot(run.directory.resolve("details/options").resolve(run.staging.relativize(destination)));
                    Csv2Fhir converter = new Csv2Fhir(inputDirectory, destination.toFile(), prefix, validator, set.options());
                    converter.convertFiles(patientsPerBundle, outputFileTypes);
                    importProblems |= converter.hasImportProblems();
                }
            }
            boolean validationProblems = validator != null && validator.hasValidationProblems();
            return run.finish(importProblems, validationProblems, validateBundles);
        } catch (Exception e) {
            run.fail(e);
            throw e;
        }
    }

    FHIRValidator createValidator() {
        return new FHIRValidator(minLogLevel);
    }
}
