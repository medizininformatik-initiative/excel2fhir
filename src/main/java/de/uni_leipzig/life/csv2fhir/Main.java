package de.uni_leipzig.life.csv2fhir;

import picocli.CommandLine;

import java.io.File;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "csv2fhir", mixinStandardHelpOptions = true, version = "1.0",
        description = "Converts a directory containing multiple csv files into a json bundle.")
public class Main implements Callable<Integer> {
    @CommandLine.Option(names = {"-i", "--input-directory"}, required = true, paramLabel = "INPUT-DIRECTORY",
            description = "supply the input Directory here")
    File inputDirectory;

    @CommandLine.Option(names = {"-o", "--output-file"}, required = true, paramLabel = "OUTPUT-FILE",
            description = "supply the output File here")
    File outputFile;

    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new Main())
                .setExecutionExceptionHandler(new PrintExceptionMessageHandler());
        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        if (inputDirectory.isDirectory()) {
            Csv2Fhir converter = new Csv2Fhir(inputDirectory, outputFile);
            converter.convertFiles();
        } else {
            throw new Exception("provided input Directory is NOT a directory!");
        }
        return 0;
    }
}
