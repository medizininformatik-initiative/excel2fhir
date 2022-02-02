package de.uni_leipzig.life.csv2fhir;

import static de.uni_leipzig.life.csv2fhir.OutputFileType.NDJSON;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

import org.hl7.fhir.r4.model.Bundle;

import de.uni_leipzig.imise.FHIRValidator;

/**
 * Wraps a file as a ndjson file.
 *
 * @author AXS (02.02.2022)
 */
public class NDJsonFile {

    /** Buffered writer with a file writer in it. */
    private final BufferedWriter writer;

    /** The ndjson file */
    private final File file;

    /**
     * The validator. If not null every bundle will be validated by this
     * validator and only written if the validtaion result is not error.
     */
    private final FHIRValidator validator;

    /**
     * @param file
     * @param validator
     * @throws Exception
     */
    public NDJsonFile(File file, FHIRValidator validator) throws Exception {
        writer = new BufferedWriter(new FileWriter(file, true));
        this.file = file;
        this.validator = validator;
    }

    /**
     * Append one line with the given bundle. If validator is not
     * <code>null</code> then the bundle will be added only if it is valid-
     *
     * @param bundle
     * @throws Exception
     */
    public void appendBundle(Bundle bundle) throws Exception {
        if (bundle != null && !bundle.getEntry().isEmpty()) {
            if (validator == null || !validator.validateBundle(bundle).isError()) {
                String encodedBundle = NDJSON.getParser()
                        .setPrettyPrint(false)
                        .encodeResourceToString(bundle);
                writer.write(encodedBundle);
                writer.newLine();
            }
        }
    }

    /**
     * Finishes writing the file and renames it to the passed name or deletes
     * the file if it is empty.
     *
     * @param newFileName
     * @throws Exception
     */
    public void closeWriterAndRenameOrDeleteIfEmpty(String newFileName) throws Exception {
        writer.close();
        if (!deleteIfEmpty()) {
            File newFile = new File(file.getParentFile(), newFileName);
            file.renameTo(newFile);
        }
    }

    /**
     * @return <code>true</code> only if the file is an empty file
     * @throws Exception
     */
    private boolean deleteIfEmpty() throws Exception {
        if (file.isFile() && file.length() == 0L) {
            writer.close();
            file.delete();
            return true;
        }
        return false;
    }

}
