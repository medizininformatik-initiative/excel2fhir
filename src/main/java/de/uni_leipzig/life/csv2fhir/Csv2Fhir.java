package de.uni_leipzig.life.csv2fhir;

import ca.uhn.fhir.context.FhirContext;
import de.uni_leipzig.life.csv2fhir.converterFactory.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class Csv2Fhir {

    private final File dir;
    private final Map<String, ConverterFactory> converterFactorys;
    private final FhirContext ctx;
    private final CSVFormat csvFormat;

    public Csv2Fhir(File directory) {
        this.dir = directory;
        this.converterFactorys = new HashMap<>() {{
            put("Person.csv", new PersonConverterFactory());
            put("Versorgungsfall.csv", new VersorgungsfallConverterFactory());
            put("Abteilungsfall.csv", new AbteilungsfallConverterFactory());
            put("Laborbefund.csv", new LaborbefundConverterFactory());
            put("Diagnose.csv", new DiagnoseConverterFactory());
            put("Prozedur.csv", new ProzedurConverterFactory());
            put("Medikation (2).csv", new MedikationConverterFactory());
            put("Medikation.csv", new MedikationConverterFactory());
            put("Klinische Dokumentation.csv", new KlinischeDokumentationConverterFactory());
        }};
        this.ctx = FhirContext.forR4();
        csvFormat = CSVFormat.DEFAULT
                .withNullString("").withIgnoreSurroundingSpaces().withTrim(true)
                .withAllowMissingColumnNames(true).withFirstRecordAsHeader();
    }

    public void convertFiles() throws Exception {
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.TRANSACTION);

        String[] files = dir.list();
        if (files != null) {
            for (String fileName : files) {
                ConverterFactory factory = converterFactorys.get(fileName);
                if (factory == null) {
                    continue;
                }
                File file = new File(dir.getPath(), fileName);
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
                            System.out.println(e.getMessage());
                        }
                    }
                }
            }
        }
        File out = new File(dir.getPath(), "out.json");
        ctx.newJsonParser().encodeResourceToWriter(bundle, new FileWriter(out));
    }

    private boolean isColumnMissing(Map<String, Integer> map, String[] neededColls) {
        boolean b= !map.keySet().stream().map(String::trim).collect(Collectors.toSet()).containsAll(Arrays.asList(neededColls));
        if(b){//Error message
            for(String s : neededColls){
                System.out.print(map.keySet().stream().map(String::trim).collect(Collectors.toSet()).contains(s) + " - '" + s + "' ");
            }
            System.out.println();
            System.out.println(map.keySet().stream().map(String::trim).collect(Collectors.toSet()).toString());
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

