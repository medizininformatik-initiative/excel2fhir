package de.uni_leipzig.imise.utils;

import java.io.File;
import java.io.IOException;

import com.google.common.base.Strings;

/**
 * @author AXS (9 Apr 2019)
 */
public final class ApplicationManager {

    /**
     * Returns the top directory where application data is located, i.e. the
     * installation directory.<br>
     *
     * @param ignoreSubDir if the application is started from a subdirectory,
     *            then this subdirectory is truncated here and only the file is
     *            returned to the top directory. This happens if you start the
     *            application directly from the jar file e.g. from the lib
     *            directory. Then you have to upload one.
     * @return path to the application
     */
    public static File getApplicationDir(final String ignoreSubDir) {
        File f = null;
        try {
            f = new File(".").getCanonicalFile();
            if (!Strings.isNullOrEmpty(ignoreSubDir)) {
                String path = f.getAbsolutePath();
                if (path.endsWith(ignoreSubDir)) {
                    path = path.substring(0, path.length() - ignoreSubDir.length());
                    f = new File(path);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return f;
    }

    /**
     * Returns the top directory where application data is located, i.e. the
     * installation directory.
     *
     * @return path to the application
     */
    public static File getApplicationDir() {
        return getApplicationDir("");
    }

}
