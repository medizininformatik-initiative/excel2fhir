package de.uni_leipzig.life.csv2fhir;

import static com.google.common.base.Strings.isNullOrEmpty;
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
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.uni_leipzig.imise.FHIRValidator;
import de.uni_leipzig.imise.FHIRValidator.ValidationResultType;
import de.uni_leipzig.imise.utils.Alphabetical;
import de.uni_leipzig.life.csv2fhir.converterFactory.PersonConverterFactory;

/**
 * @author fheuschkel (02.11.2020)
 */
public class Csv2Fhir {

    /**  */
    private static Logger LOG = LoggerFactory.getLogger(Csv2Fhir.class);

    /**
     * @author AXS (07.11.2021)
     */
    public static enum OutputFileType {

        JSON {
            @Override
            public IParser getParser() {
                return fhirContext.newJsonParser();
            }
        },

        XML {
            @Override
            public IParser getParser() {
                return fhirContext.newXmlParser();
            }
        };

        public String getFileExtension() {
            return "." + name().toLowerCase();
        }

        /** The context to generate the parser */
        private static final FhirContext fhirContext = FhirContext.forR4();

        /**
         * @return the parser to write the bundles
         */
        public abstract IParser getParser();
    }

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
     * @param convertFilesPerPatient
     * @throws Exception
     */
    public void convertFiles(OutputFileType outputFileType, boolean convertFilesPerPatient) throws Exception {
        if (convertFilesPerPatient) {
            convertFilesPerPatient(outputFileType);
        } else {
            convertFiles(outputFileType);
        }
    }

    /**
     * @param outputFileType
     * @throws Exception
     */
    public void convertFiles(OutputFileType outputFileType) throws Exception {
        convertFiles(outputFileType, null);
    }

    /**
     * @param outputFileType
     * @throws Exception
     */
    private void convertFilesPerPatient(OutputFileType outputFileType) throws Exception {
        Collection<String> pids = getValues(Person + ".csv", PersonConverterFactory.NeededColumns.Patient_ID, true, true);
        for (String pid : pids) {
            LOG.info("Start create Fhir-Json-Bundle for Patient-ID " + pid + " ...");
            Stopwatch stopwatch = Stopwatch.createStarted();
            convertFiles(outputFileType, pid);
            LOG.info("Finished create Fhir-Json-Bundle for Patient-ID " + pid + " in " + stopwatch.stop());
        }
    }

    /**
     * @param outputFileType
     * @param pid if not null or empty than the reuslt bundle contains only
     *            values of this patient (all values are filtered with this id)
     * @throws Exception
     */
    private void convertFiles(OutputFileType outputFileType, String pid) throws Exception {
        String filter = isNullOrEmpty(pid) ? null : pid.toUpperCase();
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.TRANSACTION);
        convertFiles(bundle, filter);
        // However, since we do not want to attach just any diagnosis to these
        // Part-Of-Encounters, after ALL diagnoses have been converted, we must
        // select an appropriate one. Which one this can be is not yet
        // determined during the conversion, so it has to be done afterwards.
        BundlePostProcessor.convert(bundle);
        writeOutputFile(bundle, pid == null ? "" : "-" + pid, outputFileType);
        TableIdentifier.clearAll();
    }

    /**
     * @param bundle
     * @param fileNameExtension
     * @param outputFileType
     * @throws IOException
     */
    private void writeOutputFile(Bundle bundle, String fileNameExtension, OutputFileType outputFileType)
            throws IOException {
        if (validator == null || !validator.validateBundle(bundle).isError()) {
            String fileName = outputFileNameBase + (Strings.isNullOrEmpty(fileNameExtension) ? "" : fileNameExtension)
                    + outputFileType.getFileExtension();
            File outputFile = new File(outputDirectory, fileName);
            LOG.info("writing file " + fileName);
            try (FileWriter fileWriter = new FileWriter(outputFile)) {
                outputFileType.getParser()
                        .setPrettyPrint(true)
                        .encodeResourceToWriter(bundle, fileWriter);
            }
        }
    }

    /**
     * @param bundle
     * @param filterID
     * @throws Exception
     */
    private void convertFiles(Bundle bundle, String filterID) throws Exception {
        LOG.info("Start parsing CSV files for Patient-ID " + filterID + "...");
        Stopwatch stopwatch = Stopwatch.createStarted();
        for (TableIdentifier csvFileName : TableIdentifier.values()) {
            String fileName = csvFileName.getCsvFileName();
            File file = new File(inputDirectory, fileName);
            if (!file.exists() || file.isDirectory()) {
                continue;
            }
            try (Reader in = new FileReader(file)) {
                CSVParser csvParser = csvFormat.parse(in);
                LOG.info("Start parsing File:" + fileName);
                Map<String, Integer> headerMap = csvParser.getHeaderMap();
                List<String> neededColumnNames = csvFileName.getNeededColumnNames();
                if (isColumnMissing(headerMap, neededColumnNames)) {
                    csvParser.close();
                    LOG.error("Error - File: " + fileName + " not convertable!");
                    continue;
                }
                for (CSVRecord record : csvParser) {
                    try {
                        if (!Strings.isNullOrEmpty(filterID)) {
                            String idColumnName = csvFileName.getPIDColumnIdentifier().toString();
                            String p = record.get(idColumnName);
                            if (!p.toUpperCase().matches(filterID)) {
                                continue;
                            }
                        }
                        List<Resource> list = csvFileName.convert(record, validator);
                        if (list != null) {
                            for (Resource resource : list) {
                                if (resource != null) {
                                    ValidationResultType validationResult = validator == null ? ValidationResultType.VALID : validator.validate(resource);
                                    //only add valid Resources
                                    if (!validationResult.isError()) {
                                        BundleEntryComponent entry = bundle.addEntry();
                                        entry.setResource(resource);
                                        BundleEntryRequestComponent requestComponent = getRequestComponent(resource);
                                        entry.setRequest(requestComponent);
                                        String url = requestComponent.getUrl();
                                        entry.setFullUrl(url);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        LOG.error("Error (" + e.getMessage() + ") while converting file " + csvFileName + " in record " + record);
                    }
                }
                csvParser.close();
            }
        }
        LOG.info("Finished parsing CSV files for Patient-ID " + filterID + " in " + stopwatch.stop());
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
    private static boolean isColumnMissing(Map<String, Integer> map, List<String> neededColumnNames) {
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
