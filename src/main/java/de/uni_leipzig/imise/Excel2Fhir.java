package de.uni_leipzig.imise;

import static de.uni_leipzig.imise.utils.ApplicationManager.getApplicationDir;

import java.io.File;
import java.util.concurrent.Callable;

import de.uni_leipzig.imise.csv2fhir.SplitExcel;
import de.uni_leipzig.life.csv2fhir.PrintExceptionMessageHandler;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "excel2fhir", mixinStandardHelpOptions = true, version = "1.0", description = "Converts a directory containing multiple excel files into (a) json bundle(s).")
public class Excel2Fhir implements Callable<Integer> {

    /** the project directory */
    public static final File APPLICATION_DIR = getApplicationDir();

    /**
    *
    */
    @Option(names = {"-f",
            "--input-file"}, paramLabel = "INPUT-File", description = "Input excel file. If specified the input directory is ignored.")
    File inputFile;

    /**
     *
     */
    @Option(names = {"-i",
            "--input-directory"}, paramLabel = "INPUT-DIRECTORY", description = "Input directory for the excel file")
    File inputDirectory = new File(APPLICATION_DIR, "input");

    /**
     *
     */
    @Option(names = {"-o",
            "--output-directory"}, paramLabel = "OUTPUT-DIRECTORY", description = "Output directory for the result json file(s).")
    File outputDirectory = new File(APPLICATION_DIR, "outputGlobal");

    /**
     *
     */
    @Option(names = {"-t",
            "--temp-directory"}, paramLabel = "TEMP-DIRECTORY", description = "Temp directory for csv files converted from input files and needed to create output files. If parameter is missing then the temp directory is the input directory.")
    File tempDirectory = new File(APPLICATION_DIR, "outputLocal");

    /**
     *
     */
    @Option(names = {"-s",
            "--split-result"}, negatable = true, paramLabel = "SPLIT-RESULT", description = "Splits the result .json file in one file per patient")
    boolean convertFilesPerPatient = false;

    /**
     * @param args
     */
    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new Excel2Fhir()).setExecutionExceptionHandler(new PrintExceptionMessageHandler());
        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        SplitExcel se = new SplitExcel();
        try {
            if (inputFile != null) {
                se.convertExcelFile(inputFile, tempDirectory, outputDirectory, convertFilesPerPatient);
            } else {
                if (!inputDirectory.isDirectory()) {
                    throw new Exception("Provided input Directory is NOT a directory!");
                }
                se.convertAllExcelInDir(inputDirectory, tempDirectory, outputDirectory, convertFilesPerPatient);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

}
