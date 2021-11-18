package fmeineke.imise.de;

import static de.uni_leipzig.life.csv2fhir.Csv2Fhir.OutputFileType.JSON;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;

import de.uni_leipzig.imise.csv2fhir.SplitExcel;
import de.uni_leipzig.imise.utils.Sys;
import de.uni_leipzig.life.csv2fhir.Csv2Fhir;
import de.uni_leipzig.life.csv2fhir.InputDataTableName;
import de.uni_leipzig.life.csv2fhir.Ucum;
import junit.framework.TestCase;

public class SplitMainTest extends TestCase {

    public SplitMainTest(String testName) {
        super(testName);
    }

    static Collection<String> excelSheetNames = InputDataTableName.getExcelSheetNames();

    public static void testUcum() {
        String test[] = {"mg/dl", "U/l", "ng/ml"};

        for (String t : test) {
            boolean isUcum = Ucum.isUcum(t);
            if (isUcum) {
                Sys.out1(t + " is Ucum; human readable is " + Ucum.ucum2human(t));
            } else {
                Sys.out1(t + " is human readable; ucum is " + Ucum.human2ucum(t));
            }
        }
    }

    public static void splitForGit() throws IOException {
        File testExcel = new File("C:\\Users\\frank\\Nextcloud\\Shared\\POLAR\\Testdaten\\POLAR_Testdaten_UKE.xlsx");
        File csvDir = new File("H:\\git\\csv2fhir\\resources");
        SplitExcel se = new SplitExcel();
        se.splitExcel(testExcel, excelSheetNames, csvDir);
    }

    // curl -v -H "Content-Type: application/fhir+json" -d @POLAR_Testdaten_UKB.json http://localhost:8080/baseR4/

    public static void splitTestDir() throws IOException {
        File excelDir = new File("C:\\Users\\frank\\Nextcloud\\Shared\\POLAR\\Testdaten");
        SplitExcel se = new SplitExcel();
        se.convertAllExcelInDir(excelDir, excelSheetNames);

    }

    public static void ucumTest() {
        assertEquals("%", Ucum.human2ucum("%"));
        assertEquals("/min", Ucum.human2ucum("1/min"));
        assertEquals("umol/L", Ucum.human2ucum("µmol/l"));
        assertEquals("mL/min/{1.73_m2}", Ucum.human2ucum("ml/min/1.73m^2"));
        assertEquals("10*9/L", Ucum.human2ucum("x10^9/l"));

        Set<String> s = LoincUcum.set();
        assert s.contains("/min");

        //        assertSystem.out.println(Ucum.human2ucum("1/min"));
    }

    public static void VHF() {
        String files[] = {
                //                "C:\\Users\\frank\\Nextcloud\\Shared\\POLAR\\Testdaten\\VHF-Testdaten.xlsx"
                "D:\\frank\\Nextcloud\\Shared\\POLAR\\Testdaten\\VHF-Testdaten.xlsx"
        };
        for (String f : files) {
            splitTestSingle(f);
        }
    }

    public static void splitTestSingle() {
        String files[] = {
                //                "C:\\Users\\frank\\Nextcloud\\Shared\\POLAR\\Testdaten\\POLAR_Testdaten_UKB.xlsx",
                //                "C:\\Users\\frank\\Nextcloud\\Shared\\POLAR\\Testdaten\\POLAR_Testdaten_UKFR.xlsx",
                //                "C:\\Users\\frank\\Nextcloud\\Shared\\POLAR\\Testdaten\\POLAR_Testdaten_UKSH.xlsx",
                //                "C:\\Users\\frank\\Nextcloud\\Shared\\POLAR\\Testdaten\\POLAR_Testdaten_UKFAU.xlsx",
                //                "C:\\Users\\frank\\Nextcloud\\Shared\\POLAR\\Testdaten\\POLAR_Testdaten_UKE.xlsx"
                "D:\\frank\\Nextcloud\\Shared\\POLAR\\Testdaten\\POLAR_Testdaten_UKB.xlsx"
        };
        for (String f : files) {
            splitTestSingle(f);
        }
    }

    public static void splitTestSingle(String filename) {
        File testExcel = new File(filename);
        SplitExcel se = new SplitExcel();
        try {
            se.splitExcel(testExcel, excelSheetNames);
            File csvDir = new File(FilenameUtils.removeExtension(testExcel.getPath()));
            //            File resultJson = new File(testExcel.getParent(),
            //                    FilenameUtils.removeExtension(testExcel.getName())+".json");
            Csv2Fhir converter = new Csv2Fhir(csvDir, FilenameUtils.removeExtension(testExcel.getName()));
            converter.convertFiles(JSON, true);

        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void xxxxx() {
        for (int i = 1; i < 10002; i++) {
            System.out.printf(",VHF%05d", i);
            if (i % 1000 == 0) {
                System.out.printf("\n");
            }
        }
    }
}
