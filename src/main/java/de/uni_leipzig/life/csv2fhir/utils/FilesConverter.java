package de.uni_leipzig.life.csv2fhir.utils;

import java.io.File;
import java.io.FileFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author AXS (03.11.2021)
 */
public abstract class FilesConverter<T> {

    /**  */
    private static Logger LOG = LoggerFactory.getLogger(FilesConverter.class);

    /** The directory with the files or a specific file to convert */
    protected File source;

    /**
     * The directory where the converted files are stored or a specific target
     * file.
     */
    protected File target;

    /** The filter for the files in the source directory */
    protected FileFilter fileFilter;

    /** If <code>true</code> the passed files will be printed to System.out */
    protected boolean printDebug;

    /** number of files which should be skipped in the directory */
    protected int startFileIndex;

    /**
     * number of files that should be processed after the*startFileIndex,if
     * exists.-1 or> files count means all.
     */
    protected int filesCount;

    /**
     * @param source The directory with the files or a specific file to convert
     * @param target The directory where the converted files are stored or a
     *            specific target file
     * @param fileFilter The filter for the files in the source directory
     */
    public FilesConverter(final File source, final File target, final FileFilter fileFilter) {
        this(source, target, fileFilter, false);
    }

    /**
     * @param source The directory with the files or a specific file to convert
     * @param target The directory where the converted files are stored or a
     *            specific target file
     * @param fileExtension The file extension for the filter of the files in
     *            the source directory
     */
    public FilesConverter(final File source, final File target, final String fileExtension) {
        this(source, target, createFileFilter(fileExtension), false);
    }

    /**
     * @param source The directory with the files or a specific file to convert
     * @param target The directory where the converted files are stored or a
     *            specific target file
     * @param printDebug If <code>true</code> the passed files will be printed
     *            to System.out
     */
    public FilesConverter(final File source, final File target, final boolean printDebug) {
        this(source, target, (FileFilter) null, printDebug);
    }

    /**
     * @param source The directory with the files or a specific file to convert
     * @param target The directory where the converted files are stored or a
     *            specific target file
     * @param fileExtension The file extension for the filter of the files in
     *            the source directory
     * @param printDebug If <code>true</code> the passed files will be printed
     *            to System.out
     */
    public FilesConverter(final File source, final File target, final String fileExtension, final boolean printDebug) {
        this(source, target, fileExtension, 0, -1, printDebug);
    }

    /**
     * @param source The directory with the files or a specific file to convert
     * @param target The directory where the converted files are stored or a
     *            specific target file
     * @param fileExtension The file extension for the filter of the files in
     *            the source directory
     * @param startFileIndex number of files which should be skipped in the
     *            directory
     * @param filesCount number of files that should be processed after the
     *            startFileIndex, if exists. -1 or > files count means all.
     * @param printDebug If <code>true</code> the passed files will be printed
     *            to System.out
     */
    public FilesConverter(final File source, final File target, final String fileExtension, final int startFileIndex, final int filesCount, final boolean printDebug) {
        this(source, target, createFileFilter(fileExtension), startFileIndex, filesCount, printDebug);
    }

    /**
     * @param source The directory with the files or a specific file to convert
     * @param target The directory where the converted files are stored or a
     *            specific target file
     * @param fileFilter The filter for the files in the source directory
     * @param printDebug If <code>true</code> the passed files will be printed
     *            to System.out
     */
    public FilesConverter(final File source, final File target, final FileFilter fileFilter, final boolean printDebug) {
        this(source, target, fileFilter, 0, -1, printDebug);
    }

    /**
     * @param source The directory with the files or a specific file to convert
     * @param target The directory where the converted files are stored or a
     *            specific target file
     * @param fileFilter The filter for the files in the source directory
     * @param startFileIndex number of files which should be skipped in the
     *            directory
     * @param filesCount number of files that should be processed after the
     *            startFileIndex, if exists. -1 or > files count means all.
     * @param printDebug If <code>true</code> the passed files will be printed
     *            to System.out
     */
    public FilesConverter(final File source, final File target, final FileFilter fileFilter, final int startFileIndex, final int filesCount, final boolean printDebug) {
        this.source = source;
        this.target = target;
        this.fileFilter = fileFilter;
        this.startFileIndex = startFileIndex;
        this.filesCount = filesCount;
        this.printDebug = printDebug;
    }

    /**
     * @return all files from the source directory passed through the fileFilter
     *         or only the source file if source is a file
     */
    public File[] listFiles() {
        if (source.isFile()) {
            return new File[] {source};
        }
        return source.listFiles(fileFilter);
    }

    /**
     * Iterates all files in the source directory and calls
     * {@link #convert(Object)} for each of them.
     */
    public void convert() {
        int currentFileIndex = 0;
        for (File file : listFiles()) {
            if (filesCount >= 0 && currentFileIndex > startFileIndex + filesCount) {
                break;
            }
            if (currentFileIndex++ < startFileIndex) {
                continue;
            }
            if (printDebug) {
                LOG.debug(String.valueOf(file));
            }
            T object = readFile(file);
            convert(object);
            File targetFile = getTargetFile(file);
            writeObject(object, targetFile);
        }
    }

    /**
     * Pocesses the converting
     *
     * @param objectToConvert
     */
    public abstract void convert(T objectToConvert);

    /**
     * Reads an object to convert from file
     *
     * @param sourceFile file to read
     * @return the object to convert from the file
     */
    public abstract T readFile(final File sourceFile);

    /**
     * Writes the given object to the file.
     *
     * @param convertedObject
     * @param targetFile
     */
    public abstract void writeObject(final T convertedObject, final File targetFile);

    /**
     * @param sourceFile
     * @return the full target file name generated by the source file name and
     *         the target directory or always the target if it is a file itself
     *         (and not a directory).
     */
    protected File getTargetFile(final File sourceFile) {
        if (!target.exists() || !target.isDirectory()) {
            return target;
        }
        String name = sourceFile.getName();
        File targetFile = new File(target, name);
        return targetFile;
    }

    /**
     * @param fileExtension
     * @return a file filter that accepts the given file extension
     */
    public static final FileFilter createFileFilter(final String fileExtension) {
        return pathname -> pathname.canRead() && pathname.getName().endsWith(fileExtension);
    }

}
