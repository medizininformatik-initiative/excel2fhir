import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import com.sun.star.beans.*;
import com.sun.star.bridge.XUnoUrlResolver;
import com.sun.star.comp.helper.Bootstrap;
import com.sun.star.frame.*;
import com.sun.star.lang.XComponent;
import com.sun.star.sheet.*;
import com.sun.star.table.*;
import com.sun.star.text.XText;
import com.sun.star.uno.*;

/** Apply explicit cell/range edits to an existing workbook through LibreOffice. */
public class WorkbookUno {
    static <T> T q(Class<T> type, Object object) { return UnoRuntime.queryInterface(type, object); }
    static PropertyValue prop(String name, Object value) {
        PropertyValue p = new PropertyValue(); p.Name = name; p.Value = value; return p;
    }
    public static void main(String[] args) {
        try {
            run(args);
        } catch (Throwable error) {
            error.printStackTrace();
            System.exit(1);
        }
    }
    private static void run(String[] args) throws java.lang.Exception {
        XComponentContext local = Bootstrap.createInitialComponentContext(null);
        XUnoUrlResolver resolver = q(XUnoUrlResolver.class,
                local.getServiceManager().createInstanceWithContext("com.sun.star.bridge.UnoUrlResolver", local));
        XComponentContext context = q(XComponentContext.class, resolver.resolve(
                "uno:socket,host=localhost,port=" + System.getenv().getOrDefault("CSV2FHIR_UNO_PORT", "20152")
                        + ";urp;StarOffice.ComponentContext"));
        XComponentLoader loader = q(XComponentLoader.class,
                context.getServiceManager().createInstanceWithContext("com.sun.star.frame.Desktop", context));
        XComponent doc = loader.loadComponentFromURL(Path.of(args[0]).toUri().toString(), "_blank", 0,
                new PropertyValue[] {prop("Hidden", true), prop("UpdateDocMode", (short) 0)});
        try {
            XSpreadsheetDocument book = q(XSpreadsheetDocument.class, doc);
            for (String line : Files.readAllLines(Path.of(args[1]), StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                String[] a = line.split("\t", -1);
                if (a[0].equals("copySheet")) {
                    book.getSheets().copyByName(a[2], a[1], (short) book.getSheets().getElementNames().length);
                    continue;
                }
                XSpreadsheet sheet = q(XSpreadsheet.class, book.getSheets().getByName(a[1]));
                if (a[0].equals("row")) {
                    int row = Integer.parseInt(a[2].substring(1)) - 1;
                    Object[][] values = new Object[1][a.length - 3];
                    for (int i = 3; i < a.length; i++) values[0][i - 3] =
                            new String(Base64.getDecoder().decode(a[i]), StandardCharsets.UTF_8);
                    q(XCellRangeData.class, sheet.getCellRangeByPosition(0, row, a.length - 4, row)).setDataArray(values);
                } else if (a[0].equals("insert")) {
                    q(XColumnRowRange.class, sheet).getColumns().insertByIndex(Integer.parseInt(a[2]), Integer.parseInt(a[3]));
                } else if (a[0].equals("copy")) {
                    CellRangeAddress source = q(XCellRangeAddressable.class, sheet.getCellRangeByName(a[2])).getRangeAddress();
                    CellAddress target = q(XCellAddressable.class, sheet.getCellRangeByName(a[3]).getCellByPosition(0, 0)).getCellAddress();
                    q(XCellRangeMovement.class, sheet).copyRange(target, source);
                } else if (a[0].equals("clear")) {
                    q(XSheetOperation.class, sheet.getCellRangeByName(a[2])).clearContents(1 | 2 | 4 | 16);
                } else if (a[0].equals("set")) {
                    q(XText.class, sheet.getCellRangeByName(a[2]).getCellByPosition(0, 0)).setString(
                            new String(Base64.getDecoder().decode(a[3]), StandardCharsets.UTF_8));
                } else if (a[0].equals("validation")) {
                    XPropertySet cells = q(XPropertySet.class, sheet.getCellRangeByName(a[2]));
                    Object validation = cells.getPropertyValue("Validation");
                    XPropertySet props = q(XPropertySet.class, validation);
                    props.setPropertyValue("Type", ValidationType.LIST);
                    props.setPropertyValue("IgnoreBlankCells", true);
                    props.setPropertyValue("ShowErrorMessage", Boolean.parseBoolean(a[4]));
                    q(XSheetCondition.class, validation).setFormula1(a[3]);
                    cells.setPropertyValue("Validation", validation);
                } else if (a[0].equals("text")) {
                    XPropertySet cells = q(XPropertySet.class, sheet.getCellRangeByName(a[2]));
                    com.sun.star.util.XNumberFormats formats = q(com.sun.star.util.XNumberFormatsSupplier.class, doc).getNumberFormats();
                    int textFormat = formats.queryKey("@", new com.sun.star.lang.Locale(), true);
                    if (textFormat < 0) textFormat = formats.addNew("@", new com.sun.star.lang.Locale());
                    cells.setPropertyValue("NumberFormat", textFormat);
                    cells.setPropertyValue("CharFontName", "Calibri");
                } else if (a[0].equals("width")) {
                    q(XPropertySet.class, q(XColumnRowRange.class, sheet).getColumns().getByIndex(Integer.parseInt(a[2])))
                            .setPropertyValue("Width", Integer.parseInt(a[3]));
                }
            }
            if (args.length >= 3) {
                XSpreadsheet diagnosis = q(XSpreadsheet.class, book.getSheets().getByName(args.length >= 4 ? args[3] : "Diagnose"));
                q(XStorable.class, doc).storeToURL(Path.of(args[2]).toUri().toString(),
                        new PropertyValue[] {prop("FilterName", "calc_pdf_Export"), prop("Overwrite", true),
                                prop("FilterData", new PropertyValue[] {prop("Selection", diagnosis.getCellRangeByName(args.length >= 5 ? args[4] : "A1:N6")),
                                        prop("SinglePageSheets", true)})});
            } else {
                q(XStorable.class, doc).store();
            }
        } finally {
            doc.dispose();
        }
        // UNO connection threads otherwise keep the standalone helper alive.
        System.exit(0);
    }
}
