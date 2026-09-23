package de.uni_leipzig.life.csv2fhir;

import static com.google.common.base.Strings.isNullOrEmpty;
import static de.uni_leipzig.life.csv2fhir.ConverterOptions.IntOption.PID_LAST_NUMBER_INCREASE_LOOP_COUNT;
import static de.uni_leipzig.life.csv2fhir.OutputFileType.JSON;
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
import java.util.LinkedHashSet;
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
    private final List<ConverterOptionSet> optionSets;
    private boolean variantImportProblems;

    /** Counters for all resources created from one set of CSV files */
    private final ConverterResultStatistics fileSetStatistics = new ConverterResultStatistics();

    /** Cache for the parsed records */
    private final Map<TableIdentifier, List<CSVRecord>> tableIdentifierToParsedRecords = new HashMap<>();

    private final ImportReport importReport = new ImportReport();
    private final Map<TableIdentifier, Map<Long, String>> recordPatients = new HashMap<>();

    public boolean hasImportProblems() { return variantImportProblems || importReport.hasErrors(); }
    public ImportReport getImportReport() { return importReport; }

    private Collection<String> loadInputs() throws IOException {
        // Read each table once, even when patients or output options are repeated.
        for (TableIdentifier table : TableIdentifier.values()) {
            if (!table.isConvertableTableSheet()) continue;
            File file = getCsvInputFile(outputFileNameBase, table.toString());
            if (!file.isFile()) {
                if (table == Person) importReport.failure(null, null, "MISSING_PATIENT_FILE", "Person.csv is missing", null);
                continue;
            }
            ImportReport.Table summary = importReport.table(table, file.getName());
            try (Reader in = new FileReader(file); CSVParser parser = csvFormat.parse(in)) {
                List<CSVRecord> records = parser.getRecords();
                summary.rowsRead = records.size();
                Set<String> headers = new HashSet<>();
                boolean duplicate = parser.getHeaderNames().stream()
                        .filter(h -> !isNullOrEmpty(h)).anyMatch(h -> !headers.add(h));
                if (duplicate) {
                    importReport.failure(table, null, "DUPLICATE_COLUMNS", "Duplicate column name", null);
                    continue;
                }
                Set<String> required = new LinkedHashSet<>(table.getMandatoryColumnNames());
                required.add(table.getPIDColumnIdentifier().toString());
                if (isColumnMissing(parser.getHeaderMap(), required)) {
                    importReport.failure(table, null, "MISSING_COLUMNS", "Required columns are missing: " +
                            required.stream().filter(c -> !parser.getHeaderMap().containsKey(c)).collect(Collectors.joining(", ")), null);
                    continue;
                }
                tableIdentifierToParsedRecords.put(table, records);
                Map<Long, String> patients = new HashMap<>();
                recordPatients.put(table, patients);
                String previous = null;
                for (CSVRecord record : records) {
                    if (record.size() != parser.getHeaderNames().size()) {
                        importReport.failure(table, record.getRecordNumber(), "MALFORMED_RECORD", "Field count does not match the header", null);
                        // A malformed explicit ID must not silently redirect following continuation rows.
                        previous = null;
                        continue;
                    }
                    String pid = record.get(table.getPIDColumnIdentifier().toString());
                    if (isNullOrEmpty(pid) && isRecordEmpty(record, table.getMandatoryColumnNames())) { summary.emptyRows++; continue; }
                    if (!isNullOrEmpty(pid)) previous = pid;
                    if (previous == null) importReport.failure(table, record.getRecordNumber(), "MISSING_PATIENT", "Patient-ID is empty and no preceding Patient-ID is available", null);
                    else patients.put(record.getRecordNumber(), previous);
                }
            } catch (Exception e) {
                tableIdentifierToParsedRecords.remove(table); recordPatients.remove(table);
                importReport.failure(table, null, "READ_ERROR", ImportReport.describe(e), null);
            }
        }
        Set<String> knownPatients = recordPatients.getOrDefault(Person, Map.of()).values().stream()
                .map(pid -> pid.toUpperCase(java.util.Locale.ROOT)).collect(Collectors.toSet());
        List<String> pids = new ArrayList<>(knownPatients);
        Alphabetical.sort(pids);
        if (pids.isEmpty()) importReport.failure(null, null, "NO_PATIENTS", "No patient IDs to process", null);
        for (var entry : recordPatients.entrySet()) {
            var iterator = entry.getValue().entrySet().iterator();
            while (iterator.hasNext()) {
                var row = iterator.next();
                if (!knownPatients.contains(row.getValue().toUpperCase(java.util.Locale.ROOT))) {
                    importReport.failure(entry.getKey(), row.getKey(), "UNKNOWN_PATIENT", "Patient-ID is missing from the Person sheet", null);
                    iterator.remove();
                }
            }
        }
        return pids;
    }

    /*
     * Resource classes which are not dependant of a patient (which have no subject
     * reference)
     */
    private static final Set<Class<? extends Resource>> PID_INDIPENDENT_RESOURCE_TYPES = Set.of(Medication.class,
            Location.class);

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
    public Csv2Fhir(File inputDirectory, File outputDirectory, String outputFileNameBase,
            @Nullable FHIRValidator validator) {
        this(inputDirectory, outputDirectory, outputFileNameBase, validator, null);
    }

    public Csv2Fhir(File inputDirectory, File outputDirectory, String outputFileNameBase,
            @Nullable FHIRValidator validator, @Nullable ConverterOptions selectedOptions) {
        this.inputDirectory = inputDirectory;
        this.outputDirectory = outputDirectory;
        this.outputFileNameBase = outputFileNameBase;
        csvFormat = CSVFormat.DEFAULT.builder()
                .setNullString("")
                .setIgnoreSurroundingSpaces(true)
                .setTrim(false)
                .setAllowMissingColumnNames(true)
                .setHeader()
                .setSkipHeaderRecord(true).get();
        this.validator = validator;
        try {
            optionSets = selectedOptions == null ? ConverterOptionSet.csv(inputDirectory, outputFileNameBase) : List.of();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        allConverterOptions = selectedOptions == null ? optionSets.stream().map(ConverterOptionSet::options).toList()
                : List.of(selectedOptions);
    }

    private static List<String> getOutputFileNameBaseVariants(String outputFileNameBase) {
        LinkedHashSet<String> variants = new LinkedHashSet<>();
        variants.add(outputFileNameBase);

        if (outputFileNameBase.endsWith("_")) {
            variants.add(outputFileNameBase.substring(0, outputFileNameBase.length() - 1));
            variants.add(outputFileNameBase.substring(0, outputFileNameBase.length() - 1) + "-");
        } else if (outputFileNameBase.endsWith("-")) {
            variants.add(outputFileNameBase.substring(0, outputFileNameBase.length() - 1));
            variants.add(outputFileNameBase.substring(0, outputFileNameBase.length() - 1) + "_");
        }
        return new ArrayList<>(variants);
    }

    private File getCsvInputFile(String outputFileNameBase, String tableName) {
        for (String inputBaseNameVariant : getOutputFileNameBaseVariants(outputFileNameBase)) {
            File candidate = new File(inputDirectory, inputBaseNameVariant + tableName + ".csv");
            if (candidate.exists() && candidate.isFile()) {
                return candidate;
            }
        }
        return new File(inputDirectory, outputFileNameBase + tableName + ".csv");
    }

    /**
     * @param patientsPerBundle
     * @param outputFileTypes
     * @return the counters of all created resources
     * @throws Exception
     */
    public ConverterResultStatistics convertFiles(int patientsPerBundle, OutputFileType... outputFileTypes)
            throws Exception {
        if (optionSets.size() > 1) {
            for (var set : optionSets) {
                var destination = outputDirectory.toPath().resolve(set.directoryName());
                java.nio.file.Files.createDirectories(destination);
                set.snapshot(outputDirectory.toPath().resolve("options").resolve(set.directoryName()));
                Csv2Fhir converter = new Csv2Fhir(inputDirectory, destination.toFile(), outputFileNameBase, validator, set.options());
                fileSetStatistics.add(converter.convertFiles(patientsPerBundle, outputFileTypes));
                variantImportProblems |= converter.hasImportProblems();
            }
            return fileSetStatistics;
        }
        try {
            Collection<String> patients = loadInputs();
            preflightContacts();
            preflightOptions();
            if (importReport.hasErrors()) {
                LOG.error("Input validation failed with {} issues. See the import report.", importReport.issues.size());
                return new ConverterResultStatistics();
            }
            return convertPreparedFiles(patients, patientsPerBundle, outputFileTypes);
        } catch (Exception e) {
            importReport.failure(null, null, "ABORTED", ImportReport.describe(e), null);
            throw e;
        } finally {
            String name = getOutputFileName(outputFileNameBase, "", JSON).replaceFirst("\\.json$", ".import.json");
            importReport.write(new File(outputDirectory, name).toPath());
        }
    }

    private void preflightOptions() {
        Collection<String> patients = new LinkedHashSet<>(recordPatients.getOrDefault(Person, Map.of()).values());
        for (ConverterOptions options : allConverterOptions) {
            for (String error : options.getErrors())
                importReport.failure(null, null, "OPTION_ERROR", error, options);
            if (!options.getErrors().isEmpty()) continue;
            int repetitions = options.getValue(PID_LAST_NUMBER_INCREASE_LOOP_COUNT);
            try {
                Math.multiplyExact(patients.size(), Math.addExact(repetitions, 1));
            } catch (ArithmeticException e) {
                importReport.failure(null, null, "OPTION_ERROR", "Patient count including repetitions exceeds the supported numeric range", options);
                continue;
            }
            Set<String> generated = new HashSet<>();
            // Check each output ID before writing any bundle, including collisions
            // caused by different source IDs, offsets or underscore normalization.
            for (int iteration = 0; iteration <= repetitions; iteration++) {
                for (String patient : patients) {
                    try {
                        String id = options.getFullPID(patient, iteration);
                        if (!generated.add(id)) importReport.failure(null, null, "PATIENT_ID_COLLISION",
                                "Duplicate generated patient ID: " + id + " (iteration " + iteration + ")", options);
                    } catch (IllegalArgumentException | ArithmeticException e) {
                        importReport.failure(null, null, "PATIENT_ID_ERROR",
                                patient + " (iteration " + iteration + "): " + ImportReport.describe(e), options);
                    }
                }
            }
        }
    }

    private void preflightContacts() {
        var contacts = new de.uni_leipzig.life.csv2fhir.converter.ContactInputValidator();
        Map<Long, String> patients = recordPatients.getOrDefault(TableIdentifier.Fall, Map.of());
        for (CSVRecord record : tableIdentifierToParsedRecords.getOrDefault(TableIdentifier.Fall, List.of())) {
            String patient = patients.get(record.getRecordNumber());
            if (patient == null) continue; // Empty/malformed/unknown-patient rows have already been accounted for.
            for (var issue : contacts.accept(new de.uni_leipzig.life.csv2fhir.converter.ContactInputValidator.Input(
                    record.getRecordNumber(), patient, record.toMap()))) {
                importReport.failure(TableIdentifier.Fall, issue.row(), "CONTACT_INPUT_ERROR",
                        issue.field() + ": " + issue.message(), null);
            }
        }
    }

    private ConverterResultStatistics convertPreparedFiles(Collection<String> pids, int patientsPerBundle,
            OutputFileType... outputFileTypes) throws Exception {

        for (ConverterOptions converterOptions : allConverterOptions) {

            int pids2ConvertCount = pids.size() * (converterOptions.getValue(PID_LAST_NUMBER_INCREASE_LOOP_COUNT) + 1);

            Bundle bundle = null; // this bundle contains up to patientsPerBundle patients
            Bundle singlePatientBundle = null; // this bundle contains always only 1 patient (it is used to write the
                                               // ndjson and zip files)
            MultiSinglePatientBundlesFileWriter multiSinglePatientBundlesFileWriter = null;

            // we must check which file types should be written
            List<OutputFileType> baseFileTypes = new ArrayList<>();
            List<OutputFileType> compressedFileTypes = new ArrayList<>();
            if (outputFileTypes.length == 0) {
                baseFileTypes.add(JSON); // no type specified -> default is plain JSON
            } else {
                for (OutputFileType outputFileType : outputFileTypes) {
                    if (!outputFileType.isMultiSinglePatientBundlesFileType()) { // NDJSON or ZIPJSON will be processed
                                                                                 // later
                        if (outputFileType.isCompressedFileType()) {
                            compressedFileTypes.add(outputFileType);
                        } else {
                            baseFileTypes.add(outputFileType);
                        }
                    }
                }
                // is only not null if the outputFileTypes contains NDJSON or ZIPJSON
                multiSinglePatientBundlesFileWriter = MultiSinglePatientBundlesFileWriter.create(outputDirectory,
                        outputFileNameBase, validator, outputFileTypes);
                if (multiSinglePatientBundlesFileWriter != null) {
                    singlePatientBundle = createTransactionBundle();
                }
            }

            int bundlePIDCount = 0;
            int fullPIDCount = 0;
            String firstPID = null;
            String lastPID = null;

            for (; converterOptions.loopCounter <= converterOptions
                    .getValue(PID_LAST_NUMBER_INCREASE_LOOP_COUNT); converterOptions.loopCounter++) {
                for (String pid : pids) {
                    fullPIDCount++;
                    if (bundlePIDCount++ == 0) {
                        firstPID = converterOptions.getFullPIDForFileName(pid);
                        if (!baseFileTypes.isEmpty() || !compressedFileTypes.isEmpty()) {
                            bundle = createTransactionBundle();
                        }
                    }
                    if (bundlePIDCount == patientsPerBundle || bundlePIDCount == pids2ConvertCount) {
                        lastPID = converterOptions.getFullPIDForFileName(pid);
                    }
                    LOG.info("Start add patient to Fhir-Json-Bundle for Patient-ID " + pid + " ...");
                    Stopwatch stopwatch = Stopwatch.createStarted();
                    String filter = isNullOrEmpty(pid) ? null : pid.toUpperCase();
                    ConverterResult bundlesWithCSVData = fillBundlesWithCSVData(bundle, singlePatientBundle, filter,
                            converterOptions);
                    ConverterResultStatistics singleBundleStatistics = bundlesWithCSVData.getStatistics();
                    if (bundle != null) {
                        BundlePostProcessor.convert(bundle, converterOptions);
                    }
                    if (multiSinglePatientBundlesFileWriter != null) {
                        // same convertion here as with the bundle
                        BundlePostProcessor.convert(singlePatientBundle, converterOptions);
                        multiSinglePatientBundlesFileWriter.appendBundle(singlePatientBundle);
                        singlePatientBundle = createTransactionBundle();
                    }
                    pid = pid.replace('_', '-'); // see comment at ConverterOptions#getFullPID()
                    if (lastPID != null) {
                        String fileNameExtendsion = converterOptions.getPrefixWithSuffix();
                        if (pids.size() >= patientsPerBundle) {
                            if (firstPID == null) {
                                throw new IllegalStateException(
                                        "Cannot build output file name extension without first patient ID");
                            }
                            fileNameExtendsion = firstPID.equals(lastPID) ? firstPID : firstPID + "-" + lastPID;
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
    private boolean writeOutputFile(Bundle bundle, String fileNameExtension, List<OutputFileType> baseFileTypes,
            List<OutputFileType> compressedFileTypes) throws Exception {
        List<OutputFileType> compressedFileTypesCopy = new ArrayList<>(compressedFileTypes); // copy the global list
                                                                                             // because we remove from
                                                                                             // it
        boolean written = false;
        if (bundle != null && !bundle.getEntry().isEmpty()) {
            if (validator != null) {
                validator.validateAndWriteReport(bundle, new File(outputDirectory,
                        outputFileNameBase + fileNameExtension + ".validation.json"));
            }
            {
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
                // for this compressed file types the base file type was not yet created
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
    private File writeBaseOutputFile(Bundle bundle, String fileNameExtension, OutputFileType outputFileType)
            throws IOException {
        String fileName = getOutputFileName(outputFileNameBase, fileNameExtension, outputFileType);
        File outputFile = new File(outputDirectory, fileName);
        LOG.info("writing file " + fileName);
        try (Writer fileWriter = new FileWriter(outputFile)) {
            outputFileType.getParser()
                    .setPrettyPrint(true)
                    .encodeResourceToWriter(bundle, fileWriter);
        }
        appendNewLineAtEOF(outputFile);
        return outputFile;
    }

    static String getOutputFileName(String outputFileNameBase, String fileNameExtension,
            OutputFileType outputFileType) {
        String normalizedExtension = Strings.nullToEmpty(fileNameExtension);
        String fileNameBase = Strings.isNullOrEmpty(normalizedExtension) ? removeTrailingSeparator(outputFileNameBase)
                : getOutputFileNameBase(outputFileNameBase, normalizedExtension) + normalizedExtension;
        return (fileNameBase.isEmpty() ? "patients" : fileNameBase.replaceAll("__", "_")) + outputFileType.getFileExtension();
    }

    private static String getOutputFileNameBase(String outputFileNameBase, String fileNameExtension) {
        return fileNameExtension.startsWith("_") ? removeTrailingSeparator(outputFileNameBase) : outputFileNameBase;
    }

    private static String removeTrailingSeparator(String fileNameBase) {
        return fileNameBase.endsWith("_") || fileNameBase.endsWith("-")
                ? fileNameBase.substring(0, fileNameBase.length() - 1)
                : fileNameBase;
    }

    /**
     * @param file
     * @throws IOException
     */
    private static void appendNewLineAtEOF(File file) throws IOException {
        try (Writer output = new BufferedWriter(new FileWriter(file, true))) {
            output.append("\n");
        }
    }

    /**
     * @param bundle
     * @param ndjsonBundle
     * @param filterID
     * @param options
     * @return
     * @throws Exception
     */
    private ConverterResult fillBundlesWithCSVData(Bundle bundle, Bundle ndjsonBundle, String filterID,
            ConverterOptions options) throws Exception {
        LOG.info("Start parsing CSV files for Patient-ID " + filterID + "...");
        Stopwatch stopwatch = Stopwatch.createStarted();
        ConverterResult result = new ConverterResult(options);
        for (TableIdentifier table : TableIdentifier.values()) {
            List<CSVRecord> records = tableIdentifierToParsedRecords.get(table);
            if (records == null) continue;
            Map<Long, String> patients = recordPatients.get(table);
            for (CSVRecord record : records) {
                String pid = patients.get(record.getRecordNumber());
                if (pid == null || !pid.equalsIgnoreCase(filterID)) continue;
                try {
                    List<? extends Resource> resources = table.convert(record, pid, result, validator, options);
                    for (Resource resource : resources) {
                        addEntry(bundle, resource);
                        addEntry(ndjsonBundle, resource);
                    }
                    importReport.success(table, record.getRecordNumber(), resources.size());
                } catch (Exception e) {
                    importReport.failure(table, record.getRecordNumber(), "CONVERSION_ERROR", ImportReport.describe(e), options);
                    LOG.error("Conversion error in {} record {}: {}", table, record.getRecordNumber(),
                            importReport.issues.get(importReport.issues.size() - 1).reason);
                }
            }
        }
        importReport.contactEndDerivations.addAll(result.contactEndDerivations);
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
            // prevent adding some resources twice to the bundle
            // Medications or Locations or ... can be created multiple with the same ID (if
            // multiple patients in the same bundle get the same medication/location/...)
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
            String fullUrl = "https://diz.de/" + requestComponent.getUrl();
            entry.setFullUrl(fullUrl);
        }
    }

    /**
     * @param bundle
     * @param resourceClass
     * @param id
     * @return <code>true</code> if the bundle contains a resource with the given
     *         id.
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
     * @param neededColumnNames
     * @return
     */
    private static boolean isColumnMissing(Map<String, Integer> map, Collection<String> neededColumnNames) {
        Set<String> columns = getTrimmedKeys(map);
        if (!columns.containsAll(neededColumnNames)) {// Error message
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
     * @return true if all values in the mandatory columns of the record are empty
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
