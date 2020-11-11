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

    public static void splitForGit() throws IOException {
        File testExcel = new File("C:\\Users\\frank\\Nextcloud\\Shared\\POLAR\\Testdaten\\POLAR_Testdaten_UKE.xlsx");
        File csvDir = new File("H:\\git\\csv2fhir\\resources");
        SplitExcel se = new SplitExcel();
        se.splitExcel(testExcel,csvDir);
    }
    
    // curl -v -H "Content-Type: application/fhir+json" -d @POLAR_Testdaten_UKB.json http://localhost:8080/baseR4/
        
    public static void splitTestDir() {
        File excelDir =  new File("C:\\Users\\frank\\Nextcloud\\Shared\\POLAR\\Testdaten");
        SplitExcel se = new SplitExcel();
        se.convertAllExcelInDir(excelDir);

    }
    public static void splitTestSingle() {
//         File testExcel = new File("C:\\Users\\frank\\Nextcloud\\Shared\\POLAR\\Testdaten\\POLAR_Testdaten_UKFR.xlsx");
         File testExcel = new File("C:\\Users\\frank\\Nextcloud\\Shared\\POLAR\\Testdaten\\POLAR_Testdaten_UKE.xlsx");
//        File testExcel = new File("C:\\Users\\frank\\Nextcloud\\Shared\\POLAR\\Testdaten\\POLAR_Testdaten_UKB.xlsx");
//        File testExcel = new File("C:\\Users\\frank\\Nextcloud\\Shared\\POLAR\\Testdaten\\POLAR_Testdaten_UKFAU_20201008.xlsx");
//         File testExcel = new File("C:\\Users\\frank\\Nextcloud\\Shared\\POLAR\\Testdaten\\POLAR_Testdaten_UKSH-20201002.xlsx");
        SplitExcel se = new SplitExcel();
        try {
            se.splitExcel(testExcel);
            File csvDir = new File(FilenameUtils.removeExtension(testExcel.getPath()));
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
