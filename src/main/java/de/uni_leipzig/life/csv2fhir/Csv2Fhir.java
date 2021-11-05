package de.uni_leipzig.life.csv2fhir;

import static de.uni_leipzig.life.csv2fhir.PrintExceptionMessageHandler.printException;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryRequestComponent;
import org.hl7.fhir.r4.model.Resource;

import ca.uhn.fhir.context.FhirContext;
import de.uni_leipzig.life.csv2fhir.converterFactory.AbteilungsfallConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.DiagnoseConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.KlinischeDokumentationConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.LaborbefundConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.MedikationConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.PersonConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.ProzedurConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.VersorgungsfallConverterFactory;
import de.uni_leipzig.life.csv2fhir.utils.Sys;

/**
 * @author fheuschkel (02.11.2020)
 */
public class Csv2Fhir {

    /**  */
    private final File inputDirectory;

    /**  */
    private final Map<String, ConverterFactory> converterFactorys;

    /**  */
    private final FhirContext fhirContext;

    /**  */
    private final CSVFormat csvFormat;

    /**  */
    private final String outputFilenamebase;

    /**  */
    private String filter = ".*";

    /**
     * @param inputDirectory
     * @param outputFileNameBase
     */
    @SuppressWarnings("serial")
    public Csv2Fhir(File inputDirectory, String outputFileNameBase) {
        this.inputDirectory = inputDirectory;
        outputFilenamebase = outputFileNameBase;
        converterFactorys = new HashMap<>() {
            {
                put("Person.csv", new PersonConverterFactory());
                put("Versorgungsfall.csv", new VersorgungsfallConverterFactory());
                put("Abteilungsfall.csv", new AbteilungsfallConverterFactory());
                put("Laborbefund.csv", new LaborbefundConverterFactory());
                put("Diagnose.csv", new DiagnoseConverterFactory());
                put("Prozedur.csv", new ProzedurConverterFactory());
                put("Medikation.csv", new MedikationConverterFactory());
                put("Klinische Dokumentation.csv", new KlinischeDokumentationConverterFactory());
            }
        };
        fhirContext = FhirContext.forR4();
        csvFormat = CSVFormat.DEFAULT.withNullString("").withIgnoreSurroundingSpaces().withTrim(true)
                .withAllowMissingColumnNames(true).withFirstRecordAsHeader();
    }

    /**
     * @param f
     */
    public void setFilter(String f) {
        filter = f.toUpperCase();
    }

    /**
     * @param fileName
     * @return
     * @throws IOException
     */
    public String[] getIds(String fileName) throws IOException {
        Set<String> pids = new HashSet<>();
        File file = new File(inputDirectory.getPath(), fileName);

        if (!file.exists() || file.isDirectory()) {
            return null;
        }
        try (CSVParser records = csvFormat.parse(new FileReader(file))) {
            for (CSVRecord record : records) {
                String pid = record.get("Patient-ID");
                if (pid != null) {
                    pids.add(pid.toUpperCase());
                    Sys.out1("found pid=" + pid);
                }
            }
            String[] a = new String[pids.size()];
            return pids.toArray(a);
        }
    }

    /**
     * @throws Exception
     */
    public void convertFiles() throws Exception {
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.TRANSACTION);

        String[] files = inputDirectory.list();
        if (files != null) {
            for (String fileName : files) {
                ConverterFactory factory = converterFactorys.get(fileName);
                if (factory == null) {
                    continue;
                }
                File file = new File(inputDirectory.getPath(), fileName);
                if (!file.exists() || file.isDirectory()) {
                    continue;
                }
                try (Reader in = new FileReader(file)) {
                    CSVParser records = csvFormat.parse(in);
                    Sys.out1("Start parsing File:" + fileName);
                    if (isColumnMissing(records.getHeaderMap(), factory.getNeededColumnNames())) {
                        records.close();
                        throw new Exception("Error - File: " + fileName + " not convertable!");
                    }
                    for (CSVRecord record : records) {
                        try {
                            List<Resource> list = factory.create(record).convert();
                            if (list != null) {
                                for (Resource resource : list) {
                                    if (resource != null) {
                                        bundle.addEntry().setResource(resource).setRequest(getRequestComponent(resource));
                                    }
                                }
                            }
                        } catch (Exception e) {
                            printException(e);
                        }
                    }
                    records.close();
                }
            }
        }
        File outputFile = new File(inputDirectory.getAbsolutePath(), outputFilenamebase + ".json");
        try (FileWriter fileWriter = new FileWriter(outputFile)) {
            fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToWriter(bundle, fileWriter);
        }
    }

    /**
     * @throws Exception
     */
    public void convertFilesPerPatient() throws Exception {
        String[] pids = getIds("Person.csv");
        for (String pid : pids) {
            setFilter(pid);
            String[] files = inputDirectory.list();
            if (files != null) {
                Bundle bundle = new Bundle();
                bundle.setType(Bundle.BundleType.TRANSACTION);
                for (String fileName : files) {
                    ConverterFactory factory = converterFactorys.get(fileName);
                    if (factory == null) {
                        continue;
                    }
                    File file = new File(inputDirectory.getPath(), fileName);
                    if (!file.exists() || file.isDirectory()) {
                        continue;
                    }
                    try (Reader in = new FileReader(file)) {
                        CSVParser records = csvFormat.parse(in);
                        Sys.out1("Start parsing File:" + fileName);
                        if (isColumnMissing(records.getHeaderMap(), factory.getNeededColumnNames())) {
                            records.close();
                            throw new Exception("Error - File: " + fileName + " not convertable!");
                        }
                        for (CSVRecord record : records) {
                            try {
                                String p = record.get("Patient-ID");
                                if (!p.toUpperCase().matches(filter)) {
                                    continue;
                                }
                                List<Resource> list = factory.create(record).convert();
                                if (list != null) {
                                    for (Resource resource : list) {
                                        if (resource != null) {
                                            bundle.addEntry().setResource(resource).setRequest(getRequestComponent(resource));
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                printException(e);
                            }
                        }
                        records.close();
                    }
                }
                File outputFile = new File(inputDirectory.getAbsolutePath(), outputFilenamebase + "-" + pid + ".json");
                Sys.out1("writing pid=" + pid);

                try (FileWriter fw = new FileWriter(outputFile)) {
                    try (FileWriter fileWriter = new FileWriter(outputFile)) {
                        fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToWriter(bundle, fileWriter);
                        //        ctx.newXmlParser().setPrettyPrint(true).encodeResourceToWriter(bundle, fileWriter);
                    }

                }
            }
        }
    }

    /**
     * @param map
     * @return
     */
    private static Set<String> getTrimmedKeys(Map<String, Integer> map) {
        Set<String> keySet = map.keySet();
        Stream<String> keySetStream = keySet.stream();
        keySetStream = keySetStream.map(String::trim);
        keySet = keySetStream.collect(Collectors.toSet());
        return keySet;
    }

    /**
     * @param map
     * @param neededColls
     * @return
     */
    private static boolean isColumnMissing(Map<String, Integer> map, String[] neededColls) {
        Set<String> columns = getTrimmedKeys(map);
        List<String> neededColumns = Arrays.asList(neededColls);
        if (!columns.containsAll(neededColumns)) {//Error message
            for (String s : neededColls) {
                if (!columns.contains(s)) {
                    Sys.out1("Column " + s + " missing");
                }
            }
            //            System.out.println();
            //            Sys.out1(columns);
            return true;
        }
        return false;
    }

    /**
     * @param resource
     * @return
     */
    private static Bundle.BundleEntryRequestComponent getRequestComponent(Resource resource) {
        String resourceID = resource.getId();
        Bundle.HTTPVerb method = resourceID == null ? Bundle.HTTPVerb.POST : Bundle.HTTPVerb.PUT;

        String url = resource.getResourceType().toString();
        if (resourceID != null) {
            url += "/" + resourceID;
        }
        BundleEntryRequestComponent requestComponent = new Bundle.BundleEntryRequestComponent();
        requestComponent = requestComponent.setMethod(method);
        requestComponent = requestComponent.setUrl(url);
        return requestComponent;
    }
}
