package de.uni_leipzig.life.csv2fhir;

import static com.google.common.base.Strings.isNullOrEmpty;
import static de.uni_leipzig.life.csv2fhir.OutputFileType.JSON;
import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Person;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleEntryRequestComponent;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;

import de.uni_leipzig.imise.FHIRValidator;
import de.uni_leipzig.imise.utils.Alphabetical;
import de.uni_leipzig.life.csv2fhir.ConverterResult.ConverterResultStatistics;

/**
 * @author fheuschkel (02.11.2020)
 */
public class Csv2Fhir {

    /**  */
    private static Logger LOG = LoggerFactory.getLogger(Csv2Fhir.class);

    /**  */
    private final File inputDirectory;

    /**  */
    private final File outputDirectory;

    /**  */
    private final String outputFileNameBase;

    /**  */
    private final CSVFormat csvFormat;

    /** The validator to validate all separate Resoruces and then the bundle */
    private final FHIRValidator validator;

    /**
     * @param inputDirectory
     * @param outputFileNameBase
     * @param validator
     */
    public Csv2Fhir(File inputDirectory, String outputFileNameBase, @Nullable FHIRValidator validator) {
        this(inputDirectory, inputDirectory, outputFileNameBase, validator);
    }

    /**
     * @param inputDirectory
     * @param outputDirectory
     * @param outputFileNameBase
     * @param validator
     */
    public Csv2Fhir(File inputDirectory, File outputDirectory, String outputFileNameBase, @Nullable FHIRValidator validator) {
        this.inputDirectory = inputDirectory;
        this.outputDirectory = outputDirectory;
        this.outputFileNameBase = outputFileNameBase;
        csvFormat = CSVFormat.DEFAULT
                .withNullString("")
                .withIgnoreSurroundingSpaces()
                .withTrim(true)
                .withAllowMissingColumnNames(true)
                .withFirstRecordAsHeader();
        this.validator = validator;
    }

    /**
     * @return the validator
     */
    public FHIRValidator getValidator() {
        return validator;
    }

    /**
     * @param csvFileName
     * @param columnName
     * @param distinct
     * @param alphabetical
     * @return
     * @throws IOException
     */
    public Collection<String> getValues(String csvFileName, Object columnName, boolean distinct, boolean alphabetical)
            throws IOException {
        String columnNameString = String.valueOf(columnName);
        Collection<String> values = distinct ? new HashSet<>() : new ArrayList<>();
        File file = new File(inputDirectory, csvFileName);

        if (!file.exists() || file.isDirectory()) {
            return null;
        }
        try (CSVParser records = csvFormat.parse(new FileReader(file))) {
            for (CSVRecord record : records) {
                String pid = record.get(columnNameString);
                if (pid != null) {
                    values.add(pid.toUpperCase());
                    LOG.info("found pid=" + pid);
                }
            }
            if (alphabetical) {
                if (distinct) {
                    values = new ArrayList<>(values);
                }
                Alphabetical.sort((List<String>) values);
            }
            return values;
        }
    }

    /**
     * @param patientsPerBundle
     * @param outputFileTypes
     * @throws Exception
     */
    public void convertFiles(int patientsPerBundle, OutputFileType... outputFileTypes) throws Exception {
        Collection<String> pids = getValues(Person + ".csv", Person.getPIDColumnIdentifier(), true, true);
        int pids2ConvertCount = pids.size();
        Bundle bundle = null; //this bundle contains up to patientsPerBundle patients
        Bundle singlePatientBundle = null; // this bundle contains always only 1 patient (it is used to write the ndjson and zip files)
        MultiSinglePatientBundlesFileWriter multiSinglePatientBundlesFileWriter = null;

        //we must check which file types should be written
        List<OutputFileType> baseFileTypes = new ArrayList<>();
        List<OutputFileType> compressedFileTypes = new ArrayList<>();
        if (outputFileTypes.length == 0) {
            baseFileTypes.add(JSON); // no type specified -> default is plain JSON
        } else {
            for (OutputFileType outputFileType : outputFileTypes) {
                if (!outputFileType.isMultiSinglePatientBundlesFileType()) { //NDJSON or ZIPJSON will be processed later
                    if (outputFileType.isCompressedFileType()) {
                        compressedFileTypes.add(outputFileType);
                    } else {
                        baseFileTypes.add(outputFileType);
                    }
                }
            }
            //is only not null if the outputFileTypes contains NDJSON or ZIPJSON
            multiSinglePatientBundlesFileWriter = MultiSinglePatientBundlesFileWriter.create(outputDirectory, outputFileNameBase, validator, outputFileTypes);
            if (multiSinglePatientBundlesFileWriter != null) {
                singlePatientBundle = createTransactionBundle();
            }
        }

        int bundlePIDCount = 0;
        int fullPIDCount = 0;
        String firstPID = null;
        String lastPID = null;

        ConverterResultStatistics fullStatistics = new ConverterResultStatistics();

        for (String pid : pids) {
            fullPIDCount++;
            if (bundlePIDCount++ == 0) {
                firstPID = pid;
                if (!baseFileTypes.isEmpty() || !compressedFileTypes.isEmpty()) {
                    bundle = createTransactionBundle();
                }
            }
            if (bundlePIDCount == patientsPerBundle || bundlePIDCount == pids2ConvertCount) {
                lastPID = pid;
            }
            LOG.info("Start add patient to Fhir-Json-Bundle for Patient-ID " + pid + " ...");
            Stopwatch stopwatch = Stopwatch.createStarted();
            String filter = isNullOrEmpty(pid) ? null : pid.toUpperCase();
            ConverterResult bundlesWithCSVData = fillBundlesWithCSVData(bundle, singlePatientBundle, filter);
            ConverterResultStatistics statistics = bundlesWithCSVData.getStatistics();
            if (bundle != null) {
                BundlePostProcessor.convert(bundle);
            }
            if (multiSinglePatientBundlesFileWriter != null) {
                //same convertion here as with the bundle
                BundlePostProcessor.convert(singlePatientBundle);
                multiSinglePatientBundlesFileWriter.appendBundle(singlePatientBundle);
                singlePatientBundle = createTransactionBundle();
            }
            pid = pid.replace('_', '-'); // see comment at Converter#parsePatientId()
            boolean bundleSavedAsFile = false;
            if (lastPID != null) {
                String fileNameExtendsion = firstPID == lastPID ? "-" + firstPID : "-" + firstPID + "-" + lastPID;
                bundleSavedAsFile = writeOutputFile(bundle, fileNameExtendsion, baseFileTypes, compressedFileTypes);
                bundle = createTransactionBundle();
                if (multiSinglePatientBundlesFileWriter != null) {
                    multiSinglePatientBundlesFileWriter.closeWriterAndRenameOrDeleteIfEmpty(fileNameExtendsion);
                    if (fullPIDCount != pids2ConvertCount) {
                        multiSinglePatientBundlesFileWriter.reset();
                    }
                }
                bundlePIDCount = 0;
                firstPID = null;
                lastPID = null;
            }
            LOG.info("Finished create Fhir-Json-Bundle for Patient-ID " + pid + " in " + stopwatch.stop());
            LOG.info("Patient " + pid + " bundle content:\n" + statistics);
            if (bundleSavedAsFile) {
                fullStatistics.add(statistics);
            }
        }
        LOG.info("All bundles content:\n" + fullStatistics);
    }

    /**
     * @return
     */
    private static Bundle createTransactionBundle() {
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.TRANSACTION);
        return bundle;
    }

    /**
     * @param bundle
     * @param fileNameExtension
     * @param baseFileTypes
     * @param compressedFileTypes
     * @throws IOException
     */
    private boolean writeOutputFile(Bundle bundle, String fileNameExtension, List<OutputFileType> baseFileTypes, List<OutputFileType> compressedFileTypes) throws Exception {
        List<OutputFileType> compressedFileTypesCopy = new ArrayList<>(compressedFileTypes); //copy the global list because we remove from it
        boolean written = false;
        if (bundle != null && !bundle.getEntry().isEmpty()) {
            if (validator == null || !validator.validateBundle(bundle).isError()) {
                for (OutputFileType baseFileType : baseFileTypes) {
                    File baseFile = writeBaseOutputFile(bundle, fileNameExtension, baseFileType);
                    for (int i = compressedFileTypesCopy.size() - 1; i >= 0; i--) {
                        OutputFileType compressedFileType = compressedFileTypesCopy.get(i);
                        if (compressedFileType.getBaseFileType() == baseFileType) {
                            compressedFileType.compress(baseFile);
                            compressedFileTypesCopy.remove(i);
                        }
                    }
                    written = true;
                }
                //for this compressed file types the base file type was not yet created
                for (int i = 0; i < compressedFileTypesCopy.size(); i++) {
                    OutputFileType compressedFileType = compressedFileTypesCopy.get(i);
                    OutputFileType baseFileType = compressedFileType.getBaseFileType();
                    File baseFile = writeBaseOutputFile(bundle, fileNameExtension, baseFileType);
                    compressedFileType.compress(baseFile);
                    compressedFileTypesCopy.remove(i--);
                    for (int j = i; j > 0 && j < compressedFileTypesCopy.size(); j++) {
                        OutputFileType nextCompressedFileType = compressedFileTypesCopy.get(j);
                        if (nextCompressedFileType.getBaseFileType().equals(baseFileType)) {
                            nextCompressedFileType.compress(baseFile);
                            compressedFileTypesCopy.remove(j--);
                        }
                    }
                    baseFile.delete();
                    written = true;
                }
            }
        }
        return written;
    }

    /**
     * @param bundle
     * @param fileNameExtension
     * @param outputFileType
     */
    private File writeBaseOutputFile(Bundle bundle, String fileNameExtension, OutputFileType outputFileType) throws IOException {
        String fileName = outputFileNameBase + (Strings.isNullOrEmpty(fileNameExtension) ? "" : fileNameExtension)
                + outputFileType.getFileExtension();
        File outputFile = new File(outputDirectory, fileName);
        LOG.info("writing file " + fileName);
        try (FileWriter fileWriter = new FileWriter(outputFile)) {
            outputFileType.getParser()
                    .setPrettyPrint(true)
                    .encodeResourceToWriter(bundle, fileWriter);
        }
        return outputFile;
    }

    /**
     * @param bundle
     * @param ndjsonBundle
     * @param filterID
     * @return
     * @throws Exception
     */
    private ConverterResult fillBundlesWithCSVData(Bundle bundle, Bundle ndjsonBundle, String filterID) throws Exception {
        LOG.info("Start parsing CSV files for Patient-ID " + filterID + "...");
        Stopwatch stopwatch = Stopwatch.createStarted();
        ConverterResult result = new ConverterResult();
        for (TableIdentifier table : TableIdentifier.values()) {
            String fileName = table.getCsvFileName();
            File file = new File(inputDirectory, fileName);
            if (!file.exists() || file.isDirectory()) {
                continue;
            }
            try (Reader in = new FileReader(file)) {
                CSVParser csvParser = csvFormat.parse(in);
                LOG.info("Start parsing File:" + fileName);
                Map<String, Integer> headerMap = csvParser.getHeaderMap();
                Collection<String> neededColumnNames = table.getMandatoryColumnNames();
                if (isColumnMissing(headerMap, neededColumnNames)) {
                    csvParser.close();
                    LOG.error("Error - File: " + fileName + " not convertable!");
                    continue;
                }
                for (CSVRecord record : csvParser) {
                    try {
                        if (!Strings.isNullOrEmpty(filterID)) {
                            String idColumnName = table.getPIDColumnIdentifier().toString();
                            String p = record.get(idColumnName);
                            if (!p.toUpperCase().matches(filterID)) {
                                continue;
                            }
                        }
                        List<? extends Resource> list = table.convert(record, result, validator);
                        for (Resource resource : list) {
                            addEntry(bundle, resource);
                            addEntry(ndjsonBundle, resource);
                        }
                    } catch (Exception e) {
                        LOG.error("Error (" + e.getMessage() + ") while converting file " + table + " in record " + record);
                    }
                }
                csvParser.close();
            }
        }
        LOG.info("Finished parsing CSV files for Patient-ID " + filterID + " in " + stopwatch.stop());
        return result;
    }

    /**
     * @param bundle
     * @param resource
     * @throws Exception
     */
    private static void addEntry(Bundle bundle, Resource resource) throws Exception {
        if (bundle != null) {
            //prevent adding some resources twice to the bundle
            //Medications can be created multiple with the same ID (if
            //multiple patients in the same bundle get the same medication)
            Class<? extends Resource> newResourceClass = resource.getClass();
            if (newResourceClass == Medication.class) {
                String newResourceID = resource.getId();
                List<BundleEntryComponent> entries = bundle.getEntry();
                for (BundleEntryComponent bundleEntry : entries) {
                    Resource existingResource = bundleEntry.getResource();
                    String existingResourceID = existingResource.getId();
                    if (existingResourceID.equals(newResourceID)) {
                        Class<? extends Resource> existingResourceClassclass1 = existingResource.getClass();
                        if (existingResourceClassclass1 == newResourceClass) {
                            return;
                        }
                    }
                }
            }
            BundleEntryComponent entry = bundle.addEntry();
            entry.setResource(resource);
            BundleEntryRequestComponent requestComponent = getRequestComponent(resource);
            entry.setRequest(requestComponent);
            String url = requestComponent.getUrl();
            entry.setFullUrl(url);
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
    private static boolean isColumnMissing(Map<String, Integer> map, Collection<String> neededColumnNames) {
        Set<String> columns = getTrimmedKeys(map);
        if (!columns.containsAll(neededColumnNames)) {//Error message
            for (String s : neededColumnNames) {
                if (!columns.contains(s)) {
                    LOG.info("Column " + s + " missing");
                }
            }
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
