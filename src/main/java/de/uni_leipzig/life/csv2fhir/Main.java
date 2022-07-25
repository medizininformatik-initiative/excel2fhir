package de.uni_leipzig.life.csv2fhir;

import static de.uni_leipzig.life.csv2fhir.OutputFileType.JSON;

import java.io.File;
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
    }, required = true, paramLabel = "INPUT-DIRECTORY", description = "supply the input Directory here")
    File inputDirectory;

    /**
     *
     */
    @CommandLine.Option(names = {
            "-o", "--output-file"
    }, required = true, paramLabel = "OUTPUT-FILE", description = "supply the output File here")
    String outputFile;

    /**
     *
     */
    @Option(names = {"-v",
            "--validate-bundles"}, negatable = true, paramLabel = "VALIDATE-BUNDLES", description = "Adds only valid resources to the bundle.")
    static boolean validateBundles = false;

    @Option(names = {"-vll",
            "--validation-log-level"}, paramLabel = "VALIDATION-LOG-LEVEL", description = "Sets the log level for validation. Default ist ERROR. Other values are IGNORED, WARNING or VALID")
    static ValidationResultType minLogLevel = null;

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
        if (!inputDirectory.isDirectory()) {
            throw new Exception("provided input Directory is NOT a directory!");
        }
        FHIRValidator validator = validateBundles ? new FHIRValidator(minLogLevel) : null;
        Csv2Fhir converter = new Csv2Fhir(inputDirectory, outputFile, validator, null);
        converter.convertFiles(Integer.MAX_VALUE, JSON);
        return 0;
    }
}
