package de.uni_leipzig.life.csv2fhir.ucum;

import static de.uni_leipzig.life.csv2fhir.utils.JSONFunctions.getJSONArray;
import static de.uni_leipzig.life.csv2fhir.utils.JSONFunctions.getString;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import de.uni_leipzig.imise.utils.Alphabetical;
import de.uni_leipzig.imise.utils.ApplicationManager;
import de.uni_leipzig.life.csv2fhir.utils.BothDirectionResourceMapper;
import de.uni_leipzig.life.csv2fhir.utils.FileHandler;
import de.uni_leipzig.life.csv2fhir.utils.JSONFilesConverter;

/**
 * This class can be used to extract all valid UCUM codes and ist display string
 * from the JSON file of the hl7 core. This json file is used to validate the
 * resulting json object using UCUM codes.<br>
 * The file json file can be found in the tgz file of the hl7 core, e.g. in core
 * Version 4.0.1 in
 * hl7.fhir.r4.core-4.0.1\package\ValueSet-ucum-common.json.<br>
 * <br>
 * This class extracts all codes and its display from the
 * ValueSet-ucum-common.json in the reosource subdirectory "ucum" and writes a
 * UTF-8 text file with key values pairs where the key is the UCUM code and the
 * value the display string. The file will be written in the same resource
 * directory.
 *
 * @author AXS (14.12.2021)
 */
public class UcumCodesExtractor {

    /**
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {

        // create the resource file ucum/UCUM_Codes.map
        extractUCUMCodesAndDisplay();

        BothDirectionResourceMapper mapper = new BothDirectionResourceMapper(UCUM_CODE_TO_DISPLAY_MAP_RESOURCE_FILE_NAME);

        //        Set<String> ucumCodes = mapper.keySet();
        //        //check the result
        //        List<String> sortedUcumCodes = Alphabetical.getSorted(ucumCodes);
        //        for (String key : sortedUcumCodes) {
        //            Sys.out1(key + " -> " + mapper.getForwardValue(key));
        //        }
        //

        // create the resource file ucum/UCUM_Synonyms_automatic.map
        extractSynonymsMap(mapper);

    }

    /////////////////////////////////////////////////////////
    // PART ONE = Create the UCUM code to display map file //
    /////////////////////////////////////////////////////////

    /**  */
    private static final File RESOURCE_SUB_DIR = new File(ApplicationManager.getApplicationDir(), "src/main/resources/");

    /**
     * source JSON file fom which the codes and displays must to be extracted
     */
    private static final File UCUM_CONCEPTS_JSON_SOURCE_FILE = new File(RESOURCE_SUB_DIR, "ucum/ValueSet-ucum-common.json");

    /**
     * The map file wit the valid UCUM codes as keys and its display text as
     * values.
     */
    public static final String UCUM_CODE_TO_DISPLAY_MAP_RESOURCE_FILE_NAME = "ucum/UCUM_Codes.map";

    /**
     * Extracts the map UCUM_Codes.map from the json-file with all valid UCUM
     * codes and its display text.
     *
     * @return a set with all ucum codes.
     */
    private static void extractUCUMCodesAndDisplay() {
        File targetMapFile = new File(RESOURCE_SUB_DIR, UCUM_CODE_TO_DISPLAY_MAP_RESOURCE_FILE_NAME);
        if (FileHandler.guaranteeWriteableFile(targetMapFile)) {
            JSONFilesConverter jsonFilesConverter = new JSONFilesConverter(UCUM_CONCEPTS_JSON_SOURCE_FILE, targetMapFile) {

                String mapFileContent = "##########################################################################\n"
                        + "### This map was created automatically from the file                   ###\n"
                        + "### hl7.fhir.r4.core-X.X.X\\package\\ValueSet-ucum-common.json in the    ###\n"
                        + "### class UcumCodesExtractor.java.                                     ###\n"
                        + "### The keys are all valid UCUM codes and the values the display text. ###\n"
                        + "##########################################################################\n";

                @Override
                public void convert(JSONObject objectToConvert) {
                    //extract the array with all ucum codes and its dispaly from json file
                    JSONArray ucumConceptsArray = getJSONArray(objectToConvert, "compose/include/concept");
                    for (Object jsonObject : ucumConceptsArray) {
                        JSONObject ucumConcept = (JSONObject) jsonObject;
                        String code = getString(ucumConcept, "code").replaceAll(" ", "\\\\u0020"); //whitespaces in the key codes must be UTF-8 encoded in the map file
                        String display = getString(ucumConcept, "display");
                        mapFileContent += code + "\t" + display + "\n";
                    }
                }

                @Override
                public void writeObject(JSONObject convertedObject, File targetFile) {
                    FileHandler.writeFile(targetMapFile, mapFileContent);
                }

            };
            jsonFilesConverter.convert();
        }
    }

    ////////////////////////////////////////////
    // PART TWO = Create the synonym map file //
    ////////////////////////////////////////////

    /**
     * Name of the UCUM file copied from
     * http://download.hl7.de/documents/ucum/concepts.tsv<br>
     * Link found on page: http://download.hl7.de/documents/ucum/ucumdata.html
     */
    private static final String UCUM_CONCEPTS_TSV_FILE_NAME = "ucum/concepts.tsv";

    /** Column name with the correct UCUM code */
    private static final String UCUM_TSV_FILE_COLUMN_NAME_CODE = "Code";

    /** Column name with common synonym codes (comma separated) */
    private static final String UCUM_TSV_FILE_COLUMN_NAME_SYNONYMS = "Synonym";

    /**
     * Name of the map file that maps from a synonym to the correct UCUM code.
     * This file is wtitten by the socond process.
     */
    public static final String UCUM_AUTOMATIC_CREATED_SYNONYM_TO_UCUM_CODE_MAP_RESOURCE_FILE_NAME = "ucum/UCUM_Synonyms_automatic.map";

    /**
     * CSVFormat to parse the tsv-File with the name
     * {@link #UCUM_CONCEPTS_TSV_FILE_NAME}
     */
    private static final CSVFormat TSV_FORMAT = CSVFormat.DEFAULT.builder()
            .setDelimiter(('\t'))
    		.setNullString("")
            .setIgnoreSurroundingSpaces(true)
            .setTrim(true)
            .setAllowMissingColumnNames(true)
            .setHeader()
            .setSkipHeaderRecord(true).build();
    
    /**
     * @param ucumCodeToDisplayMapper
     * @throws IOException
     * @throws URISyntaxException
     */
    private static void extractSynonymsMap(BothDirectionResourceMapper ucumCodeToDisplayMapper) throws IOException {
        File targetMapFile = new File(RESOURCE_SUB_DIR, UCUM_AUTOMATIC_CREATED_SYNONYM_TO_UCUM_CODE_MAP_RESOURCE_FILE_NAME);
        if (FileHandler.guaranteeWriteableFile(targetMapFile)) {
            BothDirectionResourceMapper synonymsMap = new BothDirectionResourceMapper();
            //load the UCUM concepts from the hl7 tsv file
            URL ucumConceptsTSVFile = ClassLoader.getSystemResource(UCUM_CONCEPTS_TSV_FILE_NAME);
            try (BufferedReader in = new BufferedReader(new InputStreamReader(ucumConceptsTSVFile.openStream()))) {
                CSVParser csvParser = TSV_FORMAT.parse(in);
                Set<String> validUcumCodes = ucumCodeToDisplayMapper.keySet();
                for (CSVRecord record : csvParser) {
                    String code = record.get(UCUM_TSV_FILE_COLUMN_NAME_CODE).trim();
                    code = code.replace('´', '\'').replace('`', '\''); //all 'new' valid UCUM codes only use ' instead of ` or ´
                    if (validUcumCodes.contains(code)) {
                        String commaSeparatedSynonyms = record.get(UCUM_TSV_FILE_COLUMN_NAME_SYNONYMS);
                        String[] synonyms = commaSeparatedSynonyms.split("\\s*,\\s*"); //sepearted by comma
                        for (String synonym : synonyms) {
                            synonym = synonym.trim();
                            if (!validUcumCodes.contains(synonym)) {
                                synonym = synonym.replaceAll(" ", "\\\\u0020"); //whitespaces in the key codes must be UTF-8 encoded in the map file
                                synonymsMap.put(synonym, code);
                            }
                        }
                    }
                }
                csvParser.close();
            }

            addDefaultSynonyms(ucumCodeToDisplayMapper, synonymsMap);

            String synonymMapFileContent = "#############################################################################\n"
                    + "### This map was created automatically in class UcumCodesExtractor.java   ###\n"
                    + "### from the file concepts.tsv downloaded from                            ###\n"
                    + "### http://download.hl7.de/documents/ucum/ucumdata.html                   ###\n"
                    + "### The keys are all common UCUM synonyms and the values valid UCUM code. ###\n"
                    + "#############################################################################\n";
            List<String> sortedSynonyms = Alphabetical.getSorted(synonymsMap.keySet());
            for (String synonym : sortedSynonyms) {
                String validUcumCode = synonymsMap.getForwardValue(synonym);
                synonymMapFileContent += synonym + "\t" + validUcumCode + "\n";
            }
            FileHandler.writeFile(targetMapFile, synonymMapFileContent);
        }
    }

    /**
     * @param ucumCodeToDisplayMapper
     * @return
     */
    private static void addDefaultSynonyms(BothDirectionResourceMapper ucumCodeToDisplayMapper, BothDirectionResourceMapper synonymToValidUcumCodeMapper) {
        List<String> synonyms = new ArrayList<>(synonymToValidUcumCodeMapper.keySet()); //we need a copy because we change the map in the loop
        for (String synonym : synonyms) {
            String validUcumCode = synonymToValidUcumCodeMapper.getForwardValue(synonym);
            addSynonym(ucumCodeToDisplayMapper, synonymToValidUcumCodeMapper, validUcumCode, synonym);
        }
        Set<String> validUcumCodes = ucumCodeToDisplayMapper.keySet();
        for (String validUcumCode : validUcumCodes) {
            addSynonym(ucumCodeToDisplayMapper, synonymToValidUcumCodeMapper, validUcumCode, validUcumCode);
        }
    }

    /**
     * @param ucumCodeToDisplayMapper
     * @param synonymToValidUcumCodeMapper
     * @param validUcumCode
     * @param code2Convert
     */
    private static final void addSynonym(BothDirectionResourceMapper ucumCodeToDisplayMapper, BothDirectionResourceMapper synonymToValidUcumCodeMapper, String validUcumCode, String code2Convert) {
        String display = ucumCodeToDisplayMapper.getForwardValue(validUcumCode);
        // if liter is contained in the ucum code -> create synonym with the 'L' as 'l'
        // there are no codes which contain a capital L in another context than liter,
        // if liter is contained in the display text (manually checked...)
        String lowerCaseDisplay = display.toLowerCase();

        if (lowerCaseDisplay.contains("liter") || lowerCaseDisplay.contains("liiter")) { //typo in json file for code ng/mL -> nanogram per millliiter
            String otherSynonym = code2Convert.replace('L', 'l');
            if (!otherSynonym.equals(code2Convert)) {
                Set<String> validUcumCodes = ucumCodeToDisplayMapper.keySet();
                if (!validUcumCodes.contains(otherSynonym)) { //safe is safe
                    synonymToValidUcumCodeMapper.put(otherSynonym, validUcumCode);
                }
            }
        }
    }

}
