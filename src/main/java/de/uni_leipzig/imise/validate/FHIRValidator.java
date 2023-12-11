package de.uni_leipzig.imise.validate;

import static de.uni_leipzig.imise.utils.StringUtils.getNumberSignSurroundedLogStrings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.util.Strings;
import org.hl7.fhir.common.hapi.validation.support.CachingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.NpmPackageValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;

import ca.uhn.fhir.context.ConfigurationException;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import de.uni_leipzig.life.csv2fhir.OutputFileType;

/**
 * @author fmeineke (12.10.2021), @author AXS (22.11.2021)
 */
public class FHIRValidator {

    /**  */
    private static final Logger LOG = LoggerFactory.getLogger(FHIRValidator.class);

    /** The directory with the validator packages in the resources */
    private static final String VALIDATOR_PACKAGES_DIR_IN_RESOURCES = "fhir";

    /**  */
    private final FhirValidator validator;

    /**
     *
     */
    public enum ValidationResultType {
        ERROR,
        IGNORED,
        WARNING,
        VALID;

        public boolean isError() {
            return this == ERROR;
        }
    }

    /** Counters for the validation result */
    private class ResultCounter {
        /** count of all warnings in the bundle */
        int warnings = 0;
        /** count of all errors in the bundle */
        int errors = 0;
        /** count of all ignored errors or warnings in the bundle */
        int ignored = 0;
        /** count of valid resources in the bundle */
        int valid = 0;
        /** count of all resources in the bundle */
        int resources = 0;

        /**
         * @param i
         * @return a string value of i with at least the string length of the
         *         current value of resources. The string is filled with leading
         *         whitespaces if it is s
         */
        String toString(int i) {
            String s = String.valueOf(i);
            if (i >= resources) {
                return s;
            }
            String r = String.valueOf(resources);
            while (s.length() < r.length()) {
                s = " " + s;
            }
            return s;
        }
    }

    /** Counters for the validation result */
    public class Result {
        /** the bundle the result is created from */
        public Bundle bundle;
        /** the file of the bundle */
        private File bundleFile;
        /** all resources with warnings in the bundle */
        public List<BundleEntryComponent> warningResources = new ArrayList<>();
        /** all resources with errors in the bundle */
        public List<BundleEntryComponent> errorResources = new ArrayList<>();
        /** all resources with ignored errors or warnings in the bundle */
        public List<BundleEntryComponent> ignoredResources = new ArrayList<>();
        /** all valid resources in the bundle */
        public List<BundleEntryComponent> validResources = new ArrayList<>();
    }

    /** Counter for a Bundle result */
    private ResultCounter bundleResultCounter = new ResultCounter();

    /** Counter for all Bundle results */
    private final ResultCounter fullResultCounter = new ResultCounter();

    /**
     * Determines which messeages are logged. Only messages with the same or an
     * lower ordinal()-Value are logged. If <code>null</code> then nothing will
     * be logged.
     */
    private final ValidationResultType minLogLevel;

    /**
     * If the validation result of a full bundle or a single resource contains
     * one of this error message parts then the error will be ignored.
     */
    private static final String[] VALIDATION_BUNLE_IGNORE_ERROR_MESSAGE_PARTS = {

            // The validator the error  messages change from time to time. Newer messages
            // uses ' instead of ".
            "Validation failed für 'http://loinc.org#",
            "Validation failed für 'http://fhir.de/CodeSystem/ifa/pzn#",
            "Validation failed für 'http://snomed.info/sct#",
            "Validation failed für 'http://unitsofmeasure.org#",
            "Validation failed für 'http://fhir.de/CodeSystem/ask#",

            "Unknown code 'http://loinc.org#",
            "Unknown code 'http://fhir.de/CodeSystem/bfarm/icd-10-gm#",
            "Unknown code 'http://fhir.de/CodeSystem/bfarm/atc#",

            "Could not validate code http://fhir.de/CodeSystem/bfarm/ops#",
            "Could not validate code http://fhir.de/CodeSystem/bfarm/icd-10-gm#",

            // Following error message is generated for Observations -> the followng ignore string is
            // the very last part of this message:
            // Keiner der angegebenen Codes ist im Valueset 'IdentifierType' (http://hl7.org/fhir/ValueSet/identifier-type|4.0.1), und ein Code sollte aus diesem Valueset stammen, es sei denn, er enthält keinen geeigneten Code) (Codes = http://terminology.hl7.org/CodeSystem/v2-0203#OBI)
            "Codes = http://terminology.hl7.org/CodeSystem/v2-0203#OBI",

    };

    /**
     * If the validation result of a single resource contains one of this error
     * message parts then the error will be ignored.<br>
     * the following errors will/must be fixed in the BundlePostProcessor
     * because the correct data for the resource comes from an other table sheet
     * resp. CSV entry than the resource itself.
     */
    private static final String[] VALIDATION_SINGLE_RESOURCE_IGNORE_ERROR_MESSAGE_PARTS = {
            "Falls der Encounter abgeschlossen wurde muss eine Diagnose bekannt sein",
            "Encounter.class: mindestens erforderlich = 1, aber nur gefunden 0",
    };

    /**
    *
    */
    public FHIRValidator() {
        this(ValidationResultType.ERROR);
    }

    /**
     * @param minLogLevel Determines which messeages are logged. Only messages
     *            with the same or an lower ordinal()-Value are logged.
     */
    public FHIRValidator(ValidationResultType minLogLevel) {
        this.minLogLevel = minLogLevel;
        // Create a validator. Note that for good performance you can create as many validator objects
        // as you like, but you should reuse the same validation support object in all of the,.
        FhirContext fhirContext = FhirContext.forR4();
        validator = fhirContext.newValidator();
        init();
    }

    /**
     * @param filesOrDirectoriesToValidate
     * @param validateBundleEntriesSeparately
     */
    public List<Result> validate(String[] filesOrDirectoriesToValidate, boolean validateBundleEntriesSeparately) {
        return validate(Arrays.asList(filesOrDirectoriesToValidate), validateBundleEntriesSeparately);
    }

    /**
     * @param filesOrDirectoriesToValidate
     */
    private List<Result> validate(List<String> filesOrDirectoriesToValidate, boolean validateBundleEntriesSeparately) {
        LOG.info("Start Validating...");
        Stopwatch stopwatch = Stopwatch.createStarted();
        boolean validateOnlyOneFile = true;
        List<Result> results = new ArrayList<>();
        for (String inputFileOrDirectoryName : filesOrDirectoriesToValidate) {
            File inputFileOrDirectory = new File(inputFileOrDirectoryName);
            File[] inputFiles;
            if (inputFileOrDirectory.isDirectory()) {
                inputFiles = inputFileOrDirectory.listFiles();
                validateOnlyOneFile = inputFiles.length < 2;
            } else {
                inputFiles = new File[] {inputFileOrDirectory};
            }

            for (File inputFile : inputFiles) {
                String inputFileName = inputFile.getName();
                Bundle bundle = null;
                try {
                    for (String logMessage : getNumberSignSurroundedLogStrings("Read Bundle " + inputFileName)) {
                        LOG.info(logMessage);
                    }
                    bundle = readBundle(inputFile);
                } catch (Exception e) {
                    LOG.error("Could not read bundle " + inputFileName);
                    continue;
                }
                try {
                    LOG.info("Start Validate Bundle...");
                    Stopwatch bundleValidationStopwatch = Stopwatch.createStarted();
                    if (validateBundleEntriesSeparately) {
                        Result singleResourcesValidationResult = getSingleResourcesValidationResult(bundle);
                        singleResourcesValidationResult.bundleFile = inputFile;
                        results.add(singleResourcesValidationResult);
                    } else {
                        validateBundle(bundle);
                    }
                    LOG.info("Finished Validate Bundle in " + bundleValidationStopwatch.stop());
                    logResult(inputFileName);
                } catch (Exception e) {
                    LOG.error("Could not validate bundle " + inputFileName);
                    continue;
                }
                bundleResultCounter = new ResultCounter();
            }
        }
        if (!validateOnlyOneFile) {
            logResult(null);
        }
        LOG.info("Finished Validating in " + stopwatch.stop());
        return results;
    }

    /**
     *
     */
    public void init() {
        LOG.info("Start Init FHIR Validator Bundles...");
        Stopwatch stopwatch = Stopwatch.createStarted();
        FhirContext fhirContext = FhirContext.forR4();
        NpmPackageValidationSupport npmPackageSupport = new NpmPackageValidationSupport(fhirContext);
        File[] validatorPackages = getValidatorPackages();
        if (validatorPackages != null) {
            for (File validatorPackage : validatorPackages) {
                if (validatorPackage.isFile()) {
                    try {
                        LOG.info("Load Validation Package: " + validatorPackage.getCanonicalPath());
                        npmPackageSupport.loadPackageFromClasspath(VALIDATOR_PACKAGES_DIR_IN_RESOURCES + "/" + validatorPackage.getName());
                    } catch (IOException e) {
                        LOG.error(e.getMessage(), e);
                    }
                }
            }

            // Create a support chain including the NPM Package Support
            ValidationSupportChain validationSupportChain = new ValidationSupportChain(
                    npmPackageSupport,
                    new DefaultProfileValidationSupport(fhirContext),
                    new CommonCodeSystemsTerminologyService(fhirContext),
                    new InMemoryTerminologyServerValidationSupport(fhirContext),
                    new SnapshotGeneratingValidationSupport(fhirContext));
            CachingValidationSupport validationSupport = new CachingValidationSupport(validationSupportChain);

            FhirInstanceValidator instanceValidator = new FhirInstanceValidator(validationSupport);
            validator.registerValidatorModule(instanceValidator);
        }
        LOG.info("Finished Init FHIR Validator Bundles in " + stopwatch.stop());
    }

    /**
     * @return the validator packages or <code>null</code> in case of error
     */
    private File[] getValidatorPackages() {
        URL validatorPackagesDirURL = getClass().getClassLoader().getResource(VALIDATOR_PACKAGES_DIR_IN_RESOURCES);
        if (validatorPackagesDirURL == null) {
            LOG.error("Could not find FHIR validator packages under directory name \"" + VALIDATOR_PACKAGES_DIR_IN_RESOURCES + "\"");
            return null;
        }
        // replace "%20" encoded whitespaces by real whitespaces to find files
        File validatorPackagesDir = new File(validatorPackagesDirURL.getPath().replace("%20", " "));
        if (!validatorPackagesDir.isDirectory() && validatorPackagesDir.canRead()) {
            LOG.error("Could not read Validator packages directory  " + validatorPackagesDir.getPath());
            return null;
        }
        File[] validatorPackages = validatorPackagesDir.listFiles();
        if (validatorPackages.length == 0) {
            LOG.error("Could not find FHIR validator packages in directory ");
            return null;
        }
        return validatorPackages;
    }

    /**
     * @param resource
     */
    public ValidationResultType validate(Resource resource) {
        if (resource == null) {
            return ValidationResultType.ERROR;
        }
        String resourceAsJson = OutputFileType.JSON.getParser().setPrettyPrint(true).encodeResourceToString(resource);
        return validate(resourceAsJson, resource instanceof Bundle);
    }

    /**
     * @param resourceAsJson
     * @param strict
     * @param minLogLevel
     * @return
     */
    public ValidationResultType validate(String resourceAsJson, boolean strict) {
        if (Strings.isBlank(resourceAsJson)) {
            return ValidationResultType.ERROR;
        }
        ValidationResultType resultType = ValidationResultType.VALID;
        LOG.debug("Validated Resource Content \n" + resourceAsJson);
        //ValidationResult validationResult = validator.validateWithResult(resource);
        ValidationResult validationResult = validator.validateWithResult(resourceAsJson);
        List<SingleValidationMessage> validationMessages = validationResult.getMessages();
        for (SingleValidationMessage validationMessage : validationMessages) {
            ResultSeverityEnum severity = validationMessage.getSeverity();
            String locationString = validationMessage.getLocationString();
            Integer locationLine = validationMessage.getLocationLine();
            Integer locationCol = validationMessage.getLocationCol();
            String message = validationMessage.getMessage();
            String logMessage = severity + " " + locationString + " Line " + locationLine + " Col " + locationCol + " : " + message;

            if (!isIgnorableError(validationMessage, strict)) {
                if (severity == ResultSeverityEnum.ERROR) {
                    if (log(ValidationResultType.ERROR)) {
                        LOG.error(logMessage);
                    }
                    bundleResultCounter.errors++;
                    fullResultCounter.errors++;
                    resultType = ValidationResultType.ERROR;
                } else if (severity == ResultSeverityEnum.WARNING) {
                    if (log(ValidationResultType.WARNING)) {
                        LOG.warn(logMessage);
                    }
                    bundleResultCounter.warnings++;
                    fullResultCounter.warnings++;
                    if (resultType.ordinal() > ValidationResultType.WARNING.ordinal()) {
                        resultType = ValidationResultType.WARNING;
                    }
                } else {
                    if (log(ValidationResultType.VALID)) {
                        LOG.info(logMessage);
                    }
                    bundleResultCounter.valid++;
                    fullResultCounter.valid++;
                }
            } else {
                if (log(ValidationResultType.IGNORED)) {
                    LOG.info("IGNORED " + logMessage);
                }
                bundleResultCounter.ignored++;
                fullResultCounter.ignored++;
                if (resultType.ordinal() > ValidationResultType.IGNORED.ordinal()) {
                    resultType = ValidationResultType.IGNORED;
                }
            }
            bundleResultCounter.resources++;
            fullResultCounter.resources++;
        }
        return resultType;
    }

    /**
     * @param logLevel
     * @return
     */
    private boolean log(ValidationResultType logLevel) {
        return minLogLevel != null && minLogLevel.ordinal() >= logLevel.ordinal();
    }

    /**
     * @param validationMessage
     * @param strict only if <code>false</code> the the
     *            {@link #VALIDATION_SINGLE_RESOURCE_IGNORE_ERROR_MESSAGE_PARTS}
     *            are ignored too. This is used to ignore errors that are
     *            allowed for a single resource (strcit = <code>false</code>)
     *            but not allowed in the whole bundle (strict =
     *            <code>true</code>).
     * @return <code>true</code> if the text of the message contains a String of
     *         {@link #VALIDATION_IGNORE_ERROR_MESSAGE_PARTS}
     */
    private static boolean isIgnorableError(SingleValidationMessage validationMessage, boolean strict) {
        String message = validationMessage.getMessage();
        for (String ignoreMessagePart : VALIDATION_BUNLE_IGNORE_ERROR_MESSAGE_PARTS) {
            if (message.contains(ignoreMessagePart)) {
                return true;
            }
        }
        if (!strict) {
            for (String ignoreMessagePart : VALIDATION_SINGLE_RESOURCE_IGNORE_ERROR_MESSAGE_PARTS) {
                if (message.contains(ignoreMessagePart)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @param file
     * @return
     * @throws ConfigurationException
     * @throws DataFormatException
     * @throws IOException
     */
    static public Bundle readBundle(File file) throws ConfigurationException, DataFormatException, IOException {
        FhirContext ctx = FhirContext.forR4();
        try (FileInputStream resourceStream = new FileInputStream(file)) {
            IBaseResource r = ctx.newJsonParser().parseResource(resourceStream);
            assert r instanceof Bundle;
            return (Bundle) r;
        }
    }

    /**
     * @param bundle
     * @throws ConfigurationException
     * @throws DataFormatException
     * @throws FileNotFoundException
     */
    public ValidationResultType validateBundle(Bundle bundle) throws ConfigurationException, DataFormatException, FileNotFoundException {
        return validate(bundle);
    }

    /**
     * @param bundle
     * @return
     */
    public Result getSingleResourcesValidationResult(Bundle bundle) {
        Result result = new Result();
        result.bundle = bundle;
        List<BundleEntryComponent> entries = bundle.getEntry(); //is an ArrayList -> values can be changed
        for (BundleEntryComponent e : entries) {
            ValidationResultType validateResultType = validate(e.getResource());
            if (validateResultType == ValidationResultType.ERROR) {
                result.errorResources.add(e);
            } else if (validateResultType == ValidationResultType.WARNING) {
                result.warningResources.add(e);
            } else if (validateResultType == ValidationResultType.IGNORED) {
                result.ignoredResources.add(e);
            } else {
                result.validResources.add(e);
            }
        }
        return result;
    }

    /**
     * @param bundleName
     */
    public void logResult(String bundleName) {
        boolean logFullErrors = bundleName == null;
        ResultCounter result = logFullErrors ? fullResultCounter : bundleResultCounter;
        LOG.info(logFullErrors ? "All Bundles Result:" : "Bundle Result: (" + bundleName + ")");
        LOG.info("Errors  : " + result.toString(result.errors));
        LOG.info("Warnings: " + result.toString(result.warnings));
        LOG.info("Ignored : " + result.toString(result.ignored));
        LOG.info("Valid   : " + result.toString(result.valid));
        LOG.info("All     : " + result.toString(result.resources));
    }

    /**
     * @param args
     */
    static public void main(String args[]) {
        LOG.info("Start Validation Process...");
        Stopwatch stopwatch = Stopwatch.createStarted();
        FHIRValidator fhirValidator = new FHIRValidator();
        fhirValidator.validate(args, false);
        LOG.info("Finished Validation Process in " + stopwatch.stop());
        System.exit(0);
    }
}
