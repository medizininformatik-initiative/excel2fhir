package de.uni_leipzig.life.csv2fhir;

import static de.uni_leipzig.life.csv2fhir.OutputFileType.NDJSON;
import static de.uni_leipzig.life.csv2fhir.OutputFileType.ZIPJSON;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;

import de.uni_leipzig.imise.validate.FHIRValidator;

import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;

/**
 * Handler to write ndjson and zip files with multiple bundles in it.
 *
 * @author AXS (02.02.2022)
 */
public class MultiSinglePatientBundlesFileWriter {

    /** Buffered writer with a file writer in it. */
    private BufferedWriter ndjsonWriter;

    /**  */
    private ZipOutputStream zipJsonOutputStream;

    /** The ndjson file */
    private final File ndjsonFile;

    /** The zip file */
    private final File zipJsonFile;

    /**
     * The validator. If not null every bundle will be validated by this
     * validator and only written if the validtaion result is not error.
     */
    private final FHIRValidator validator;

    /** Base name of all output files */
    private final String outputFileNameBase;

    /**
     * @param outputDirectory
     * @param outputFileNameBase
     * @param validator
     * @param writeNDJsonFile
     * @param writeZipFile
     * @throws Exception
     */
    private MultiSinglePatientBundlesFileWriter(File outputDirectory, String outputFileNameBase, FHIRValidator validator, boolean writeNDJsonFile, boolean writeZipFile) throws Exception {
        ndjsonFile = new File(outputDirectory, outputFileNameBase + NDJSON.getFileExtension());
        zipJsonFile = new File(outputDirectory, outputFileNameBase + ZIPJSON.getFileExtension());
        this.validator = validator;
        this.outputFileNameBase = outputFileNameBase;
        resetInternal(writeNDJsonFile, writeZipFile);
    }

    /**
     * @param outputDirectory
     * @param outputFileNameBase
     * @param validator
     * @param outputFileTypes
     * @return a writer for json.zip and ndjson files, if the given
     *         outputFileTypes contains the keys of this files types. If no key
     *         found in the outputFileTypes then <code>null</code> is returned.
     * @throws Exception
     */
    public static MultiSinglePatientBundlesFileWriter create(File outputDirectory, String outputFileNameBase, FHIRValidator validator, OutputFileType... outputFileTypes) throws Exception {
        boolean writeNDJsonFile = false;
        boolean writeZipFile = false;
        for (OutputFileType outputFileType : outputFileTypes) {
            if (outputFileType == NDJSON) {
                writeNDJsonFile = true;
            } else if (outputFileType == ZIPJSON) {
                writeZipFile = true;
            }
        }
        if (!writeNDJsonFile && !writeZipFile) {
            return null;
        }
        return new MultiSinglePatientBundlesFileWriter(outputDirectory, outputFileNameBase, validator, writeNDJsonFile, writeZipFile);
    }

    /**
     * @throws Exception
     */
    public void reset() throws Exception {
        resetInternal(ndjsonWriter != null, zipJsonOutputStream != null);
    }

    /**
     * @param writeNDJsonFile
     * @param writeZipFile
     * @throws Exception
     */
    @SuppressWarnings("resource")
    private void resetInternal(boolean writeNDJsonFile, boolean writeZipFile) throws Exception {
        if (ndjsonWriter != null) {
            ndjsonWriter.close();
        }
        if (zipJsonOutputStream != null) {
            zipJsonOutputStream.close();
        }
        ndjsonWriter = writeNDJsonFile ? new BufferedWriter(new FileWriter(ndjsonFile, true)) : null;
        zipJsonOutputStream = writeZipFile ? new ZipOutputStream(new FileOutputStream(zipJsonFile)) : null;
        if (zipJsonOutputStream != null) {
            zipJsonOutputStream.setLevel(Deflater.BEST_COMPRESSION);
        }
    }

    /**
     * Append one line with the given bundle. If validator is not
     * <code>null</code> then the bundle will be added only if it is valid-
     *
     * @param bundle
     * @throws Exception
     */
    public void appendBundle(Bundle bundle) throws Exception {
        if (ndjsonWriter != null || zipJsonOutputStream != null) {
            if (bundle != null && !bundle.getEntry().isEmpty()) {
                if (validator == null || !validator.validateBundle(bundle).isError()) {
                    if (ndjsonWriter != null) {
                        String encodedBundle = NDJSON.getParser()
                                .setPrettyPrint(false)
                                .encodeResourceToString(bundle);
                        ndjsonWriter.write(encodedBundle);
                        ndjsonWriter.newLine();
                    }
                    if (zipJsonOutputStream != null) {
                        String encodedBundle = ZIPJSON.getParser()
                                .setPrettyPrint(true)
                                .encodeResourceToString(bundle);
                        try (InputStream bundleInputStream = new ByteArrayInputStream(encodedBundle.getBytes(UTF_8))) {
                            String pid = extractPatientID(bundle);
                            ZipEntry zipEntry = new ZipEntry(outputFileNameBase + "-" + pid + ZIPJSON.getBaseFileType().getFileExtension());
                            zipJsonOutputStream.putNextEntry(zipEntry);
                            byte[] bytes = new byte[1024];
                            int length;
                            while ((length = bundleInputStream.read(bytes)) >= 0) {
                                zipJsonOutputStream.write(bytes, 0, length);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Searches in all bundle entries for a resource of class {@link Patient}
     * and returns its ID. If there are more than one patients in the bundle
     * than the very first ID will be returned.
     *
     * @param bundle
     * @return
     */
    private static String extractPatientID(Bundle bundle) {
        List<BundleEntryComponent> entries = bundle.getEntry();
        for (BundleEntryComponent bundleEntry : entries) {
            Resource resource = bundleEntry.getResource();
            Class<? extends Resource> resourceClass = resource.getClass();
            if (resourceClass == Patient.class) {
                return resource.getId();
            }
        }
        return null;
    }

    /**
     * Finishes writing the file and renames it with the given extension to the
     * file name or deletes the file if it is empty.
     *
     * @param nameExtension this string will be inserted after the current file
     *            name and before the file extension.
     * @throws Exception
     */
    public void closeWriterAndRenameOrDeleteIfEmpty(String nameExtension) throws Exception {
        if (ndjsonWriter != null) {
            ndjsonWriter.close();
        }
        if (!deleteIfEmpty(ndjsonFile)) {
            String newFileName = outputFileNameBase + nameExtension + NDJSON.getFileExtension();
            File newFile = new File(ndjsonFile.getParentFile(), newFileName);
            ndjsonFile.renameTo(newFile);
        }
        if (zipJsonOutputStream != null) {
            zipJsonOutputStream.close();
        }
        if (!deleteIfEmpty(zipJsonFile)) {
            String newFileName = outputFileNameBase + nameExtension + ZIPJSON.getFileExtension();
            File newFile = new File(zipJsonFile.getParentFile(), newFileName);
            zipJsonFile.renameTo(newFile);
        }
    }

    /**
     * @param file
     * @return <code>true</code> only if the file is an empty file that could be
     *         deleted without an exception
     * @throws Exception
     */
    private static boolean deleteIfEmpty(File file) throws Exception {
        if (file.isFile() && file.length() == 0L) {
            file.delete();
            return true;
        }
        return false;
    }

}
