package de.uni_leipzig.imise.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author AXS (05.11.2021)
 */
public class FileTools {

    /**  */
    private static final Logger LOG = LoggerFactory.getLogger(FileTools.class);

    /**
     * @param path
     * @return
     * @throws IOException
     */
    public static boolean isEmpty(File file) throws IOException {
        return isEmpty(file.toPath().toAbsolutePath());
    }

    /**
     * @param path
     * @return
     * @throws IOException
     */
    public static boolean isEmpty(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> directory = Files.newDirectoryStream(path)) {
                return !directory.iterator().hasNext();
            }
        }
        return false;
    }

    /**
     * @param dir2CreateOrClean
     * @param ignoreDir if this dir equals the dir2CreateOrClean then nothing
     *            will be deleted.
     * @throws IOException
     */
    public static final void ensureEmptyDirectory(File dir2CreateOrClean, File ignoreDir) throws IOException {
        if (!dir2CreateOrClean.exists()) {
            LOG.info("creating " + dir2CreateOrClean);
            dir2CreateOrClean.mkdirs();
        } else if (!ignoreDir.equals(dir2CreateOrClean)) {
            if (!FileTools.isEmpty(dir2CreateOrClean)) {
                LOG.info("Delete all files in \"" + dir2CreateOrClean + "\"");
                try {
                    FileUtils.cleanDirectory(dir2CreateOrClean);
                } catch (Exception e) {
                    // Ignore because the Log-file cannot be
                    // deleted but already exists in this directory
                }
            }
        }
    }

}
