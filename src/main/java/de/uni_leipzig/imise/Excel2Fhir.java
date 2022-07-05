package de.uni_leipzig.imise;

import static de.uni_leipzig.imise.utils.FileTools.ensureEmptyDirectory;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.uni_leipzig.UcumMapper;
import de.uni_leipzig.imise.FHIRValidator.ValidationResultType;
import de.uni_leipzig.imise.utils.Excel2Csv;
import de.uni_leipzig.life.csv2fhir.ConverterResult.ConverterResultStatistics;
import de.uni_leipzig.life.csv2fhir.Csv2Fhir;
import de.uni_leipzig.life.csv2fhir.OutputFileType;

/**
 * @author fmeineke (02.11.2020), AXS (21.11.2021)
 */
public class Excel2Fhir {

    /**  */
    private static Logger LOG = LoggerFactory.getLogger(Excel2Fhir.class);

    /**  */
    private final FHIRValidator validator;

    /** Counters for all created resources */
    private final ConverterResultStatistics allFilesStatistics = new ConverterResultStatistics();

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
     * @param validate
     * @param minLogLevel
     */
    public Excel2Fhir(boolean validate, ValidationResultType minLogLevel) {
        validator = validate ? new FHIRValidator(minLogLevel) : null;
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
     * @throws IOException
     */
    public void convertAllExcelInDir(File sourceExcelDir, Collection<String> sheetNames) throws IOException {
        convertAllExcelInDir(sourceExcelDir, sheetNames, null, null, Integer.MAX_VALUE);
    }

    /**
     * @param sourceExcelDir
     * @param sheetNames if not <code>null</code> then only the sheets with a
     *            name in this collection will be convertert to csv. If
     *            <code>null</code> then all sheet will be convertet.
     * @param tempDir
     * @param resultDir
     * @param patientsPerBundle
     * @param outputFileTypes
     * @throws IOException
     */
    public void convertAllExcelInDir(File sourceExcelDir, Collection<String> sheetNames, File tempDir, File resultDir, int patientsPerBundle, OutputFileType... outputFileTypes)
            throws IOException {
        FilenameFilter filter = (dir, name) -> !name.startsWith("~") && name.toLowerCase().endsWith(".xlsx");
        createAndCleanOutputDirectories(sourceExcelDir, tempDir, resultDir);
        for (File sourceExcelFile : sourceExcelDir.listFiles(filter)) {
            convertExcelFile(sourceExcelFile, sheetNames, tempDir, resultDir, patientsPerBundle, false, outputFileTypes);
        }
    }

    /**
     * @param sourceExcelFile
     * @param sheetNames if not <code>null</code> then only the sheets with a
     *            name in this collection will be convertert to csv. If
     *            <code>null</code> then all sheet will be convertet.
     * @param tempDir
     * @param resultDir
     * @param patientsPerBundle
     * @param outputFileTypes
     * @throws IOException
     */
    public void convertExcelFile(File sourceExcelFile, Collection<String> sheetNames, File tempDir, File resultDir, int patientsPerBundle, OutputFileType... outputFileTypes)
            throws IOException {
        convertExcelFile(sourceExcelFile, sheetNames, tempDir, resultDir, patientsPerBundle, true, outputFileTypes);
    }

    /**
     * @param sourceExcelFile
     * @param sheetNames if not <code>null</code> then only the sheets with a
     *            name in this collection will be convertert to csv. If
     *            <code>null</code> then all sheet will be convertet.
     * @param tempDir
     * @param resultDir
     * @param outputFileTypes
     * @param patientsPerBundle
     * @param createAndCleanOutputDirectories
     * @throws IOException
     */
    private void convertExcelFile(File sourceExcelFile, Collection<String> sheetNames, File tempDir, File resultDir,
            int patientsPerBundle, boolean createAndCleanOutputDirectories, OutputFileType... outputFileTypes) throws IOException {
        if (createAndCleanOutputDirectories) {
            createAndCleanOutputDirectories(sourceExcelFile, tempDir, resultDir);
        }
        String fileName = FilenameUtils.removeExtension(sourceExcelFile.getName());
        Excel2Csv.splitExcel(sourceExcelFile, sheetNames, tempDir);
        Csv2Fhir converter = new Csv2Fhir(tempDir, resultDir, fileName, validator);
        try {
            ConverterResultStatistics converterStatistics = converter.convertFiles(patientsPerBundle, outputFileTypes);
            allFilesStatistics.add(converterStatistics);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
        if (!UcumMapper.invalidUcumCodes.isEmpty()) {
            LOG.error("Invalid UCUM codes in all files at this point " + UcumMapper.invalidUcumCodes);
        }
        LOG.info("All bundles of all files content:\n" + allFilesStatistics);
    }

}
