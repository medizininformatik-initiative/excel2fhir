package fmeineke.imise.de;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FilenameUtils;

import de.uni_leipzig.imise.csv2fhir.SplitExcel;
import de.uni_leipzig.life.csv2fhir.Csv2Fhir;
import junit.framework.TestCase;

public class SplitMainTest extends TestCase {
	public SplitMainTest(String testName) {
		super(testName);
	}
	public static void splitTestDir() {
		File excelDir =  new File("C:\\Users\\frank\\Nextcloud\\Shared\\POLAR\\Testdaten");
		SplitExcel se = new SplitExcel();
		se.convertAllExcelInDir(excelDir);
		
	}
	public static void splitTestSingle() {
		//		File testExcel = new File("C:\\Users\\frank\\Nextcloud\\Shared\\POLAR\\Testdaten\\POLAR_Testdaten_UKFR.xlsx");
		//		File testExcel = new File("C:\\Users\\frank\\Nextcloud\\Shared\\POLAR\\Testdaten\\POLAR_Testdaten_UKE.xlsx");
		File testExcel = new File("C:\\Users\\frank\\Nextcloud\\Shared\\POLAR\\Testdaten\\POLAR_Testdaten_UKB.xlsx");
		//		File testExcel = new File("C:\\Users\\frank\\Nextcloud\\Shared\\POLAR\\Testdaten\\POLAR_Testdaten_UKFAU_20201008.xlsx");
		//		File testExcel = new File("C:\\Users\\frank\\Nextcloud\\Shared\\POLAR\\Testdaten\\POLAR_Testdaten_UKSH-20201002.xlsx");
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
