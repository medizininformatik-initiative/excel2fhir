package de.uni_leipzig.life.csv2fhir.utils;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * @author AXS (04.11.2021)
 */
public abstract class JSONFilesConverter extends FilesConverter<JSONObject> {

    /**  */
    private static Logger LOG = LoggerFactory.getLogger(FilesConverter.class);

    /**
    *
    */
    public JSONFilesConverter() {
        this(null, null);
    }

    /**
     * @param source
     * @param target
     */
    public JSONFilesConverter(final File source, final File target) {
        this(source, target, false);
    }

    /**
     * @param source
     * @param target
     * @param printDebug
     */
    public JSONFilesConverter(final File source, final File target, final boolean printDebug) {
        this(source, target, 0, -1, printDebug);
    }

    /**
     * @param source
     * @param target
     * @param startFileIndex
     * @param filesCount
     * @param printDebug
     */
    public JSONFilesConverter(final File source, final File target, final int startFileIndex, final int filesCount, final boolean printDebug) {
        super(source, target, ".json", startFileIndex, filesCount, printDebug);
    }

    @Override
    public JSONObject readFile(final File sourceFile) {
        JSONParser parser = new JSONParser();
        JSONObject jsonObject = null;
        try (FileReader fileReader = new FileReader(sourceFile)) {
            Object obj = parser.parse(fileReader);
            jsonObject = (JSONObject) obj;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    @Override
    public void writeObject(final JSONObject convertedObject, final File targetFile) {
        //Write JSON file
        try {
            File parentFile = targetFile.getParentFile();
            parentFile.mkdirs();
            targetFile.createNewFile();
        } catch (Exception e) {
            LOG.error("\"" + targetFile + "\"");
            return;
        }
        try (FileWriter file = new FileWriter(targetFile)) {

            //https://stackoverflow.com/questions/4105795/pretty-print-json-in-java
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String prettyJsonString = gson.toJson(convertedObject);
            //System.out.println(prettyJsonString);
            file.write(prettyJsonString);
            file.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
