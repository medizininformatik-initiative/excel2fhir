package de.uni_leipzig.imise.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author AXS (05.11.2021)
 */
public class FileTools {

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

}
