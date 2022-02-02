package de.uni_leipzig.imise;

import static de.uni_leipzig.imise.utils.ApplicationManager.getApplicationDir;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;

import de.uni_leipzig.imise.utils.FileLogger;
import de.uni_leipzig.imise.utils.FileLogger.LogContentLayout;
import de.uni_leipzig.life.csv2fhir.OutputFileType;
import de.uni_leipzig.life.csv2fhir.PrintExceptionMessageHandler;
import de.uni_leipzig.life.csv2fhir.TableIdentifier;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * @author AXS (14.11.2021)
 */
@Command(name = "excel2fhir", mixinStandardHelpOptions = true, version = "1.0", description = "Converts a directory containing multiple excel files into (a) json bundle(s).")
public class Excel2FhirMain implements Callable<Integer> {

    /**  */
    private static Logger LOG = LoggerFactory.getLogger(Excel2FhirMain.class);

    /** the project directory */
    public static final File APPLICATION_DIR = getApplicationDir();

    @Option(names = {"-f",
            "--input-file"}, paramLabel = "INPUT-File", description = "Input excel file. If specified the input directory is ignored.")
    static File inputFile;

    @Option(names = {"-i",
            "--input-directory"}, paramLabel = "INPUT-DIRECTORY", description = "Input directory for the excel file")
    static File inputDirectory = new File(APPLICATION_DIR, "input");

    @Option(names = {"-o",
            "--output-directory"}, paramLabel = "OUTPUT-DIRECTORY", description = "Output directory for the result json file(s).")
    static File outputDirectory;

    @Option(names = {"-t",
            "--temp-directory"}, paramLabel = "TEMP-DIRECTORY", description = "Temp directory for csv files converted from input files and needed to create output files. If parameter is missing then the temp directory is the input directory.")
    static File tempDirectory;

    @Option(names = {"-r",
            "--result-file-format"}, split = ",", paramLabel = "RESULT-FILE-FORMAT", description = "Result file format (comma separated) \"JSON\" (default), \"XML\", \"NDJSON\", \"JSONGZIP\" or \"JSONBZ2\".")
    static OutputFileType[] outputFileTypes = {OutputFileType.JSON};

    @Option(names = {"-p",
            "--patients-count"}, paramLabel = "PATIENTS-COUNT", description = "Maximum number of patients in one file.")
    static int patientsPerBundle = Integer.MAX_VALUE;

    @Option(names = {"-l",
            "--log-layout"}, paramLabel = "LOG-FILE-LAYOUT", description = "The layout of the log content in the logfile.")
    static LogContentLayout logFileContentLayout = LogContentLayout.DATE_LEVEL_SOURCE_LINENUMBER; //the console log layout is set in the projects log4j2.xml file!

    @Option(names = {"-v",
            "--validate-bundles"}, negatable = true, paramLabel = "VALIDATE-BUNDLES", description = "Adds only valid resources to the bundle.")
    static boolean validateBundles = false;

    /**
     * @param args
     */
    public static void main(String[] args) {
        initDirectoriesAndLogger();

        LOG.info("Started...");
        Stopwatch stopwatch = Stopwatch.createStarted();

        Excel2FhirMain excel2Fhir = new Excel2FhirMain();
        CommandLine cmd = new CommandLine(excel2Fhir).setExecutionExceptionHandler(new PrintExceptionMessageHandler());

        int exitCode = cmd.execute(args);

        LOG.info("Finished in " + stopwatch.stop());

        System.exit(exitCode);
    }

    /**
     *
     */
    public static void initDirectoriesAndLogger() {
        File defaultOutputDirectory = inputFile != null ? inputFile.getParentFile() : inputDirectory.getParentFile();
        if (tempDirectory == null) {
            tempDirectory = new File(defaultOutputDirectory, "outputLocal");
        }
        if (outputDirectory == null) {
            outputDirectory = new File(defaultOutputDirectory, "outputGlobal");
        }
        File absoluteLogFile = new File(tempDirectory, Excel2FhirMain.class.getSimpleName() + ".log");
        FileLogger.addRootFileLogger(absoluteLogFile, logFileContentLayout);
    }

    @Override
    public Integer call() throws Exception {
        if (outputDirectory == null) {
            initDirectoriesAndLogger();
        }
        try {
            List<String> excelSheetNames = TableIdentifier.getExcelSheetNames();
            Excel2Fhir excel2Fhir = new Excel2Fhir(validateBundles);
            if (inputFile != null) {
                excel2Fhir.convertExcelFile(inputFile, excelSheetNames, tempDirectory, outputDirectory, patientsPerBundle, outputFileTypes);
            } else {
                if (!inputDirectory.isDirectory()) {
                    throw new Exception("Provided input Directory is NOT a directory!");
                }
                excel2Fhir.convertAllExcelInDir(inputDirectory, excelSheetNames, tempDirectory, outputDirectory, patientsPerBundle, outputFileTypes);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

}
