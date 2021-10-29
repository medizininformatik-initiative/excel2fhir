package de.uni_leipzig.life.csv2fhir;

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

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.Bundle;
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

public class Csv2Fhir {

    private final File inputDirectory;
    private final Map<String, ConverterFactory> converterFactorys;
    private final FhirContext ctx;
    private final CSVFormat csvFormat;
    private final String outputFilenamebase;
    public Csv2Fhir(File inputDir, String outputFilenamebase) {
        this.inputDirectory = inputDir;
        this.outputFilenamebase = outputFilenamebase;
        this.converterFactorys = new HashMap<>() {{
            put("Person.csv", new PersonConverterFactory());
            put("Versorgungsfall.csv", new VersorgungsfallConverterFactory());
            put("Abteilungsfall.csv", new AbteilungsfallConverterFactory());
            put("Laborbefund.csv", new LaborbefundConverterFactory());
            put("Diagnose.csv", new DiagnoseConverterFactory());
            put("Prozedur.csv", new ProzedurConverterFactory());
            put("Medikation.csv", new MedikationConverterFactory());
            put("Klinische Dokumentation.csv", new KlinischeDokumentationConverterFactory());
        }};
        this.ctx = FhirContext.forR4();
        csvFormat = CSVFormat.DEFAULT
                .withNullString("").withIgnoreSurroundingSpaces().withTrim(true)
                .withAllowMissingColumnNames(true).withFirstRecordAsHeader();
    }

    String filter = ".*";
    public void setFilter(String f) {
        filter = f.toUpperCase();
    }
    public String[] getIds(String fileName) throws IOException {
        Set<String> pids = new HashSet<String>(); 
        File file = new File(inputDirectory.getPath(), fileName);

        if (!file.exists() || file.isDirectory()) {
            return null;
        }
        try (Reader in = new FileReader(file)) {
            CSVParser records = csvFormat.parse(in);
            for (CSVRecord record : records) {
                String pid = record.get("Patient-ID");
                if (pid != null) {
                    pids.add(pid.toUpperCase());
                    System.out.println("found pid="+pid);
                }
            }
        }
        String[] a = new String[pids.size()];
        return pids.toArray(a);                    
    }
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
                    System.out.println("Start parsing File:" + fileName);
                    if (isColumnMissing(records.getHeaderMap(), factory.getNeededColumnNames())) {
                        throw new Exception("Error - File: " + fileName + " not convertable!");
                    }
                    for (CSVRecord record : records) {
                        try {
                            List<Resource> list = factory.create(record).convert();
                            if (list != null) {
                                for (Resource resource : list) {
                                    if (resource != null) {
                                        bundle.addEntry()
                                                .setResource(resource)
                                                .setRequest(getRequestComponent(resource));
                                    }
                                }
                            }
                        } catch (Exception e) {
                            if (e.getMessage()==null) e.printStackTrace();
                            else System.out.println(e.getMessage());
                        }
                    }
                }
            }
        }
        File outputFile = new File(inputDirectory.getAbsolutePath(),outputFilenamebase + ".json");
       ctx.newJsonParser().setPrettyPrint(true).encodeResourceToWriter(bundle, new FileWriter(outputFile));
    }

    
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
                        System.out.println("Start parsing File:" + fileName);
                        if (isColumnMissing(records.getHeaderMap(), factory.getNeededColumnNames())) {
                            throw new Exception("Error - File: " + fileName + " not convertable!");
                        }
                        for (CSVRecord record : records) {
                            try {
                                String p = record.get("Patient-ID");
                                if (!p.toUpperCase().matches(filter)) continue; 
                                List<Resource> list = factory.create(record).convert();
                                if (list != null) {
                                    for (Resource resource : list) {
                                        if (resource != null) {
                                            bundle.addEntry()
                                            .setResource(resource)
                                            .setRequest(getRequestComponent(resource));
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                if (e.getMessage()==null) e.printStackTrace();
                                else System.out.println(e.getMessage());
                            }
                        }
                    }
                }
                File outputFile = new File(inputDirectory.getAbsolutePath(),outputFilenamebase + "-" + pid + ".json");
                System.out.println("writing pid="+pid);

                try (FileWriter fw = new FileWriter(outputFile)) {
                    ctx.newJsonParser().setPrettyPrint(true).encodeResourceToWriter(bundle, new FileWriter(outputFile));
                    //        ctx.newXmlParser().setPrettyPrint(true).encodeResourceToWriter(bundle, new FileWriter(outputFile));
                }
            }
        }
    }

    private boolean isColumnMissing(Map<String, Integer> map, String[] neededColls) {
        boolean b = !map.keySet().stream().map(String::trim).collect(Collectors.toSet()).containsAll(Arrays.asList(neededColls));
        if (b) {//Error message
            for (String s : neededColls) {
                if (!map.keySet().stream().map(String::trim).collect(Collectors.toSet()).contains(s) ) {
                    //                    System.out.print(map.keySet().stream().map(String::trim).collect(Collectors.toSet()).contains(s) + " - '" + s + "' ");
                    System.out.println("Column " + s + " missing");

                }
            }
            //            System.out.println();
            //            System.out.println(map.keySet().stream().map(String::trim).collect(Collectors.toSet()).toString());
        }
        return b;
    }

    private Bundle.BundleEntryRequestComponent getRequestComponent(Resource resource) {
        if (resource.getId() == null) {
            return new Bundle.BundleEntryRequestComponent()
                    .setMethod(Bundle.HTTPVerb.POST)
                    .setUrl(resource.getResourceType().toString());
        } else {
            return new Bundle.BundleEntryRequestComponent()
                    .setMethod(Bundle.HTTPVerb.PUT)
                    .setUrl(resource.getResourceType() + "/" + resource.getId());
        }
    }
}

