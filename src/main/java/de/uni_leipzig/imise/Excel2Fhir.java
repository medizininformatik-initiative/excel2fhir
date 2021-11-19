package de.uni_leipzig.imise;

import static de.uni_leipzig.imise.utils.ApplicationManager.getApplicationDir;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;

import de.uni_leipzig.imise.csv2fhir.SplitExcel;
import de.uni_leipzig.imise.utils.FileLogger;
import de.uni_leipzig.imise.utils.FileLogger.LogContentLayout;
import de.uni_leipzig.life.csv2fhir.Csv2Fhir.OutputFileType;
import de.uni_leipzig.life.csv2fhir.InputDataTableName;
import de.uni_leipzig.life.csv2fhir.PrintExceptionMessageHandler;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "excel2fhir", mixinStandardHelpOptions = true, version = "1.0", description = "Converts a directory containing multiple excel files into (a) json bundle(s).")
public class Excel2Fhir implements Callable<Integer> {

    /**  */
    private static Logger LOG = LoggerFactory.getLogger(Excel2Fhir.class);

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
            "--result-file-format"}, paramLabel = "RESULT-FILE-FORMAT", description = "Result file format \"JSON\" (default) or \"XML\".")
    static OutputFileType outputFileType = OutputFileType.JSON;

    @Option(names = {"-s",
            "--split-result"}, negatable = true, paramLabel = "SPLIT-RESULT", description = "Splits the result file in one file per patient.")
    static boolean convertFilesPerPatient = false;

    @Option(names = {"-l",
            "--log-layout"}, paramLabel = "LOG-FILE-LAYOUT", description = "The layout of the log content in the logfile.")
    static LogContentLayout logFileContentLayout = LogContentLayout.DATE_LEVEL_SOURCE_LINENUMBER; //the console log layout is set in the projects log4j2.xml file!

    /**
     * @param args
     */
    public static void main(String[] args) {
        initDirectoriesAndLogger();

        LOG.info("Started...");
        Stopwatch stopwatch = Stopwatch.createStarted();

        Excel2Fhir excel2Fhir = new Excel2Fhir();
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
        File absoluteLogFile = new File(tempDirectory, Excel2Fhir.class.getSimpleName() + ".log");
        FileLogger.addRootFileLogger(absoluteLogFile, logFileContentLayout);
    }

    @Override
    public Integer call() throws Exception {
        if (outputDirectory == null) {
            initDirectoriesAndLogger();
        }
        SplitExcel se = new SplitExcel();
        try {

            List<String> excelSheetNames = InputDataTableName.getExcelSheetNames();

            if (inputFile != null) {
                se.convertExcelFile(inputFile, excelSheetNames, tempDirectory, outputDirectory, outputFileType, convertFilesPerPatient);
            } else {
                if (!inputDirectory.isDirectory()) {
                    throw new Exception("Provided input Directory is NOT a directory!");
                }
                se.convertAllExcelInDir(inputDirectory, excelSheetNames, tempDirectory, outputDirectory, outputFileType, convertFilesPerPatient);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

}
