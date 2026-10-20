package de.uni_leipzig.imise.utils;

import java.io.File;
import java.io.IOException;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** File-backed workbooks for inspection and conversion without rewriting the input. */
public final class ExcelWorkbooks {
    private ExcelWorkbooks() {}

    public static XSSFWorkbook openReadOnly(File file) throws IOException {
        try {
            var pkg = OPCPackage.open(file, PackageAccess.READ);
            try {
                return new XSSFWorkbook(pkg);
            } catch (IOException | RuntimeException e) {
                pkg.revert();
                throw e;
            }
        } catch (InvalidFormatException e) {
            throw new IOException("Cannot read workbook " + file, e);
        }
    }
}
