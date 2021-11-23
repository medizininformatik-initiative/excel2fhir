package de.uni_leipzig.imise;

import static de.uni_leipzig.imise.utils.FileTools.ensureEmptyDirectory;
import static de.uni_leipzig.life.csv2fhir.Csv2Fhir.OutputFileType.JSON;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.uni_leipzig.imise.utils.Excel2Csv;
import de.uni_leipzig.life.csv2fhir.Csv2Fhir;
import de.uni_leipzig.life.csv2fhir.Csv2Fhir.OutputFileType;

/**
 * @author fmeineke (02.11.2020), AXS (21.11.2021)
 */
public class Excel2Fhir {

    /**  */
    private static Logger LOG = LoggerFactory.getLogger(Excel2Fhir.class);

    /**
     * @param excelFile
     * @return
     */
    private static File getTargetCSVDir(File excelFile) {
        String path = excelFile.getPath();
        path = FilenameUtils.removeExtension(path);
        File targetCSVDir = new File(path, "output");
        return targetCSVDir;
    }

    /**
     * @param sourceExcelFileOrDirectory
     * @param targetCSVDir
     * @param targetJSONDir
     * @throws IOException
     */
    private static void createAndCleanOutputDirectories(File sourceExcelFileOrDirectory, File targetCSVDir, File targetJSONDir)
            throws IOException {
        File sourceExcelDir = sourceExcelFileOrDirectory.isDirectory() ? sourceExcelFileOrDirectory
                : sourceExcelFileOrDirectory.getParentFile();
        //create and reset directories
        if (targetCSVDir == null) {
            targetCSVDir = getTargetCSVDir(sourceExcelDir);
        }
        ensureEmptyDirectory(targetCSVDir, sourceExcelDir);
        if (targetJSONDir == null) {
            targetJSONDir = targetCSVDir;
        }
        if (targetJSONDir != targetCSVDir) {
            ensureEmptyDirectory(targetJSONDir, sourceExcelDir);
        }
    }

    /**
     * @param excelDir
     * @param sheetNames if not <code>null</code> then only the sheets with a
     *            name in this collection will be convertert to csv. If
     *            <code>null</code> then all sheet will be convertet.
     * @param validateBundles
     * @throws IOException
     */
    public static void convertAllExcelInDir(File sourceExcelDir, Collection<String> sheetNames, boolean validateBundles) throws IOException {
        convertAllExcelInDir(sourceExcelDir, sheetNames, null, null, JSON, false, validateBundles);
    }

    /**
     * @param sourceExcelDir
     * @param sheetNames if not <code>null</code> then only the sheets with a
     *            name in this collection will be convertert to csv. If
     *            <code>null</code> then all sheet will be convertet.
     * @param tempDir
     * @param resultDir
     * @param outputFileType
     * @param convertFilesPerPatient
     * @param validateBundles
     * @throws IOException
     */
    public static void convertAllExcelInDir(File sourceExcelDir, Collection<String> sheetNames, File tempDir, File resultDir, OutputFileType outputFileType,
            boolean convertFilesPerPatient, boolean validateBundles)
            throws IOException {
        FilenameFilter filter = (dir, name) -> !name.startsWith("~") && name.toLowerCase().endsWith(".xlsx");
        createAndCleanOutputDirectories(sourceExcelDir, tempDir, resultDir);
        for (File sourceExcelFile : sourceExcelDir.listFiles(filter)) {
            convertExcelFile(sourceExcelFile, sheetNames, tempDir, resultDir, outputFileType, convertFilesPerPatient, false, false);
        }
        if (validateBundles) {
            String[] validatorInputDir = new String[] {resultDir.getPath()};
            new FHIRValidator().validate(validatorInputDir);
        }
    }

    /**
     * @param sourceExcelFile
     * @param sheetNames if not <code>null</code> then only the sheets with a
     *            name in this collection will be convertert to csv. If
     *            <code>null</code> then all sheet will be convertet.
     * @param tempDir
     * @param resultDir
     * @param outputFileType
     * @param convertFilesPerPatient
     * @param validateBundles
     * @throws IOException
     */
    public static void convertExcelFile(File sourceExcelFile, Collection<String> sheetNames, File tempDir, File resultDir, OutputFileType outputFileType,
            boolean convertFilesPerPatient, boolean validateBundles)
            throws IOException {
        convertExcelFile(sourceExcelFile, sheetNames, tempDir, resultDir, outputFileType, convertFilesPerPatient, true, validateBundles);
    }

    /**
     * @param sourceExcelFile
     * @param sheetNames if not <code>null</code> then only the sheets with a
     *            name in this collection will be convertert to csv. If
     *            <code>null</code> then all sheet will be convertet.
     * @param tempDir
     * @param resultDir
     * @param outputFileType
     * @param convertFilesPerPatient
     * @param createAndCleanOutputDirectories
     * @param validateBundles
     * @throws IOException
     */
    private static void convertExcelFile(File sourceExcelFile, Collection<String> sheetNames, File tempDir, File resultDir, OutputFileType outputFileType,
            boolean convertFilesPerPatient, boolean createAndCleanOutputDirectories, boolean validateBundles) throws IOException {
        if (createAndCleanOutputDirectories) {
            createAndCleanOutputDirectories(sourceExcelFile, tempDir, resultDir);
        }
        String fileName = FilenameUtils.removeExtension(sourceExcelFile.getName());
        Excel2Csv.splitExcel(sourceExcelFile, sheetNames, tempDir);
        Csv2Fhir converter = new Csv2Fhir(tempDir, resultDir, fileName);
        try {
            converter.convertFiles(outputFileType, convertFilesPerPatient);
            if (validateBundles) {
                String[] validatorInputDir = new String[] {resultDir.getPath()};
                new FHIRValidator().validate(validatorInputDir);
            }
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
    }
}
