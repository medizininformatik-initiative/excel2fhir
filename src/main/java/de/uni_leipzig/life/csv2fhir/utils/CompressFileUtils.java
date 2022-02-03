package de.uni_leipzig.life.csv2fhir.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;

/**
 * @author AXS (02.02.2022)
 */
public class CompressFileUtils {

    /**
     * @param source
     * @throws IOException
     */
    public static void compressGzip(File source) throws IOException {
        File target = new File(source.getAbsolutePath() + ".gz");
        try (GZIPOutputStream out = new GZIPOutputStream(new FileOutputStream(target));
                FileInputStream fis = new FileInputStream(source)) {
            // copy file
            byte[] buffer = new byte[1024];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        }
    }

    /**
     * @param source
     * @throws IOException
     */
    public static void compressBZ2(File source) throws IOException {
        File target = new File(source.getAbsolutePath() + ".bz2");
        try (BZip2CompressorOutputStream gos = new BZip2CompressorOutputStream(new FileOutputStream(target));
                FileInputStream fis = new FileInputStream(source)) {
            // copy file
            byte[] buffer = new byte[1024];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                gos.write(buffer, 0, len);
            }
        }
    }

}
