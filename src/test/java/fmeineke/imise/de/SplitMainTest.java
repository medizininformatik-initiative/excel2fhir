package fmeineke.imise.de;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FilenameUtils;

import de.uni_leipzig.imise.csv2fhir.SplitExcel;
import de.uni_leipzig.life.csv2fhir.Csv2Fhir;
import de.uni_leipzig.life.csv2fhir.Ucum;
import junit.framework.TestCase;

public class SplitMainTest extends TestCase {
    public SplitMainTest(String testName) {
        super(testName);
    }

    public static void testUcum() {
        String test[] = { "mg/dl","U/l","ng/ml"};

        for (String t : test) {
            boolean isUcum =Ucum.isUcum(t);
            if (isUcum) {
                System.out.println(t + " is Ucum; human readable is " + Ucum.ucum2human(t));				
            } else {
                System.out.println(t + " is human readable; ucum is " + Ucum.human2ucum(t));
            }
        }
    }

    public static void splitTestDir() {
        File excelDir =  new File("C:\\Users\\frank\\Nextcloud\\Shared\\POLAR\\Testdaten");
        SplitExcel se = new SplitExcel();
        se.convertAllExcelInDir(excelDir);

    }
    public static void splitTestSingle() {
        // File testExcel = new File("C:\\Users\\frank\\Nextcloud\\Shared\\POLAR\\Testdaten\\POLAR_Testdaten_UKFR.xlsx");
        // File testExcel = new File("C:\\Users\\frank\\Nextcloud\\Shared\\POLAR\\Testdaten\\POLAR_Testdaten_UKE.xlsx");
        // File testExcel = new File("C:\\Users\\frank\\Nextcloud\\Shared\\POLAR\\Testdaten\\POLAR_Testdaten_UKB.xlsx");
        File testExcel = new File("C:\\Users\\frank\\Nextcloud\\Shared\\POLAR\\Testdaten\\POLAR_Testdaten_UKFAU_20201008.xlsx");
        // File testExcel = new File("C:\\Users\\frank\\Nextcloud\\Shared\\POLAR\\Testdaten\\POLAR_Testdaten_UKSH-20201002.xlsx");
        SplitExcel se = new SplitExcel();
        try {
            File csvDir = se.splitExcel(testExcel);
            File resultJson = new File(testExcel.getParent(),            			
                    FilenameUtils.removeExtension(testExcel.getName())+".json");
            Csv2Fhir converter = new Csv2Fhir(csvDir,resultJson);
            converter.convertFiles();	

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}
