package de.uni_leipzig.life.csv2fhir;

import static com.google.common.base.Strings.isNullOrEmpty;
import static de.uni_leipzig.life.csv2fhir.ConverterOptions.IntOption.PID_LAST_NUMBER_INCREASE_LOOP_COUNT;
import static de.uni_leipzig.life.csv2fhir.OutputFileType.JSON;
import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Konvertierungsoptionen;
import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Person;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;

import de.uni_leipzig.imise.utils.Alphabetical;
import de.uni_leipzig.imise.validate.FHIRValidator;
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

    /** The options to convert the current csv file set. */
    private final List<ConverterOptions> allConverterOptions;

    /** Counters for all resources created from one set of CSV files */
    private final ConverterResultStatistics fileSetStatistics = new ConverterResultStatistics();

    /** Cache for the parsed records */
    private final Map<TableIdentifier, List<CSVRecord>> tableIdentifierToParsedRecords = new HashMap<>();

    /*
     * Resource classes which are not dependant of a patient (which have no
     * subject reference)
     */
    private static final Set<Class<? extends Resource>> PID_INDIPENDENT_RESOURCE_TYPES = Set.of(Medication.class, Location.class);

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
        csvFormat = CSVFormat.DEFAULT.builder()
                .setNullString("")
                .setIgnoreSurroundingSpaces(true)
                .setTrim(true)
                .setAllowMissingColumnNames(true)
                .setHeader()
                .setSkipHeaderRecord(true).build();
        this.validator = validator;
        allConverterOptions = loadConverterOptions(inputDirectory, outputFileNameBase);
    }

    /**
     * @return
     */
    private static final List<ConverterOptions> loadConverterOptions(File inputDirectory, String outputFileNameBase) {
        List<ConverterOptions> allConverterOptions = new ArrayList<>();
        // If there is no Konvertierungsoptionen.csv file in the outputLocal directory (that was extracted
        // from the Excel file) then only the default options are loaded from the resources. If the file
        // exists then it is loaded after the defaults are loaded.
        String converterOptionsFileNamePattern = outputFileNameBase + Konvertierungsoptionen.getTableNamePattern().toString() + ".csv";
        for (File file : inputDirectory.listFiles()) {
            String fileName = file.getName();
            if (fileName.matches(converterOptionsFileNamePattern)) {
                ConverterOptions converterOptions = new ConverterOptions(file.getAbsolutePath());
                allConverterOptions.add(converterOptions);
            }
        }
        return allConverterOptions;
    }

    /**
     * @param csvFileBaseName
     * @param columnName
     * @param distinct
     * @param alphabetical
     * @return
     * @throws IOException
     */
    private Collection<String> getValues(TableIdentifier csvFileBaseName, Object columnName, boolean distinct, boolean alphabetical)
            throws IOException {
        String columnNameString = String.valueOf(columnName);
        Collection<String> values = distinct ? new HashSet<>() : new ArrayList<>();

        File file = new File(inputDirectory, outputFileNameBase + csvFileBaseName + ".csv");
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
     * @return the counters of all created resources
     * @throws Exception
     */
    public ConverterResultStatistics convertFiles(int patientsPerBundle, OutputFileType... outputFileTypes) throws Exception {
        Collection<String> pids = getValues(Person, Person.getPIDColumnIdentifier(), true, true);

        for (ConverterOptions converterOptions : allConverterOptions) {

            int pids2ConvertCount = pids.size() * (converterOptions.getValue(PID_LAST_NUMBER_INCREASE_LOOP_COUNT) + 1);

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

            for (; converterOptions.loopCounter <= converterOptions.getValue(PID_LAST_NUMBER_INCREASE_LOOP_COUNT); converterOptions.loopCounter++) {
                for (String pid : pids) {
                    fullPIDCount++;
                    if (bundlePIDCount++ == 0) {
                        firstPID = converterOptions.getFullPID(pid);
                        if (!baseFileTypes.isEmpty() || !compressedFileTypes.isEmpty()) {
                            bundle = createTransactionBundle();
                        }
                    }
                    if (bundlePIDCount == patientsPerBundle || bundlePIDCount == pids2ConvertCount) {
                        lastPID = converterOptions.getFullPID(pid);
                    }
                    LOG.info("Start add patient to Fhir-Json-Bundle for Patient-ID " + pid + " ...");
                    Stopwatch stopwatch = Stopwatch.createStarted();
                    String filter = isNullOrEmpty(pid) ? null : pid.toUpperCase();
                    ConverterResult bundlesWithCSVData = fillBundlesWithCSVData(bundle, singlePatientBundle, filter, converterOptions);
                    ConverterResultStatistics singleBundleStatistics = bundlesWithCSVData.getStatistics();
                    if (bundle != null) {
                        BundlePostProcessor.convert(bundle, converterOptions);
                    }
                    if (multiSinglePatientBundlesFileWriter != null) {
                        //same convertion here as with the bundle
                        BundlePostProcessor.convert(singlePatientBundle, converterOptions);
                        multiSinglePatientBundlesFileWriter.appendBundle(singlePatientBundle);
                        singlePatientBundle = createTransactionBundle();
                    }
                    pid = pid.replace('_', '-'); // see comment at ConverterOptions#getFullPID()
                    if (lastPID != null) {
                        String fileNameExtendsion = converterOptions.getPrefixWithSuffix();
                        if (pids.size() > patientsPerBundle) {
                            fileNameExtendsion = firstPID == lastPID ? firstPID : firstPID + "-" + lastPID;
                        }
                        writeOutputFile(bundle, fileNameExtendsion, baseFileTypes, compressedFileTypes);
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
                    LOG.info("Patient " + pid + " bundle content:\n" + singleBundleStatistics);
                    fileSetStatistics.add(singleBundleStatistics);
                }
            }
        }
        LOG.info("All bundles of current file set content:\n" + fileSetStatistics);
        return fileSetStatistics;
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
        appendNewLineAtEOF(outputFile);
        return outputFile;
    }

    /**
     * @param file
     * @throws IOException
     */
    private static void appendNewLineAtEOF(File file) throws IOException {
        Writer output = new BufferedWriter(new FileWriter(file, true)).append("\n");
        output.close();
    }

    /**
     * @param bundle
     * @param ndjsonBundle
     * @param filterID
     * @param options
     * @return
     * @throws Exception
     */
    private ConverterResult fillBundlesWithCSVData(Bundle bundle, Bundle ndjsonBundle, String filterID, ConverterOptions options) throws Exception {
        LOG.info("Start parsing CSV files for Patient-ID " + filterID + "...");
        Stopwatch stopwatch = Stopwatch.createStarted();
        ConverterResult result = new ConverterResult(options);
        boolean filter = !Strings.isNullOrEmpty(filterID);
        for (TableIdentifier table : TableIdentifier.values()) {
            if (table.isConvertableTableSheet()) {
                List<CSVRecord> parsedRecords = tableIdentifierToParsedRecords.get(table);
                if (parsedRecords == null) {
                    String fileName = table.getCsvFileName(outputFileNameBase);
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
                        parsedRecords = csvParser.getRecords();
                        tableIdentifierToParsedRecords.put(table, parsedRecords);
                        csvParser.close();
                    }
                }
                String previousPID = null;
                for (int i = 0; i < parsedRecords.size(); i++) {
                    CSVRecord record = parsedRecords.get(i);
                    try {
                        if (filter) {
                            String pidColumnName = table.getPIDColumnIdentifier().toString();
                            String pid = record.get(pidColumnName);
                            if (isNullOrEmpty(pid)) {
                                if (isRecordEmpty(record, table.getMandatoryColumnNames())) {
                                    continue;
                                }
                                pid = previousPID;
                            } else {
                                previousPID = pid;
                            }
                            if (!pid.toUpperCase().matches(filterID)) {
                                continue;
                            }
                        }
                        List<? extends Resource> list = table.convert(record, previousPID, result, validator, options);
                        for (Resource resource : list) {
                            addEntry(bundle, resource);
                            addEntry(ndjsonBundle, resource);
                        }
                    } catch (Exception e) {
                        LOG.error("Error (" + e.getMessage() + ") while converting file " + table + " in record " + record);
                    }
                }
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
            //Medications or Locations or ... can be created multiple with the same ID (if
            //multiple patients in the same bundle get the same medication/location/...)
            Class<? extends Resource> newResourceClass = resource.getClass();
            if (PID_INDIPENDENT_RESOURCE_TYPES.contains(newResourceClass)) {
                String newResourceID = resource.getId();
                if (containsResource(bundle, newResourceClass, newResourceID)) {
                    return;
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
     * @param bundle
     * @param resourceClass
     * @param id
     * @return <code>true</code> if the bundle contains a resource with the
     *         given id.
     */
    public static final boolean containsResource(Bundle bundle, Class<? extends Resource> resourceClass, String id) {
        List<BundleEntryComponent> entries = bundle.getEntry();
        for (BundleEntryComponent bundleEntry : entries) {
            Resource existingResource = bundleEntry.getResource();
            Class<? extends Resource> existingResourceClass = existingResource.getClass();
            if (resourceClass.isAssignableFrom(existingResourceClass)) {
                String existingResourceID = existingResource.getId();
                if (existingResourceID.equals(id)) {
                    return true;
                }
            }
        }
        return false;
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
     * @param record
     * @param mandatoryColumnsNames
     * @return true if all values in the mandatory columns of the record are
     *         empty
     */
    private static boolean isRecordEmpty(CSVRecord record, Collection<String> mandatoryColumnsNames) {
        for (String columnName : mandatoryColumnsNames) {
            String value = record.get(columnName);
            if (!isNullOrEmpty(value)) {
                return false;
            }
        }
        return true;
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
