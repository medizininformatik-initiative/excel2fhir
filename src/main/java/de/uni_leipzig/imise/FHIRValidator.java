package de.uni_leipzig.imise;

import static de.uni_leipzig.imise.utils.StringUtils.getNumberSignSurroundedLogStrings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import org.hl7.fhir.common.hapi.validation.support.CachingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.NpmPackageValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
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
import de.uni_leipzig.life.csv2fhir.Csv2Fhir.OutputFileType;

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

    /** Counter for a Bundle result */
    private ResultCounter bundleResultCounter = new ResultCounter();

    /** Counter for all Bundle results */
    private final ResultCounter fullResultCounter = new ResultCounter();

    /**
     * If the validation result contains one of this error message parts then
     * the error will be ignored.
     */
    private static final String[] VALIDATION_IGNORE_ERROR_MESSAGE_PARTS = {
            "Validation failed für \"http://loinc.org",
            "Unknown code 'http://loinc.org#",
            "Validation failed für \"http://fhir.de/CodeSystem/ask#",
            "Validation failed für \"http://fhir.de/CodeSystem/ifa/pzn",
            "http://terminology.hl7.org/CodeSystem/v2-0203#OBI",
            "Validation failed für \"http://snomed.info/sct"
    };

    /**
     *
     */
    public FHIRValidator() {
        // Create a validator. Note that for good performance you can create as many validator objects
        // as you like, but you should reuse the same validation support object in all of the,.
        FhirContext ctx = FhirContext.forR4();
        validator = ctx.newValidator();
        init();
    }

    /**
     * @param filesOrDirectoriesToValidate
     */
    public void validate(String[] filesOrDirectoriesToValidate) {
        validate(Arrays.asList(filesOrDirectoriesToValidate));
    }

    /**
     * @param filesOrDirectoriesToValidate
     */
    private void validate(List<String> filesOrDirectoriesToValidate) {
        LOG.info("Start Validating...");
        Stopwatch stopwatch = Stopwatch.createStarted();
        boolean validateOnlyOneFile = true;
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
                    validateBundle(bundle);
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
    public void validate(Resource resource) {
        String resourceAsJson = OutputFileType.JSON.getParser().setPrettyPrint(true).encodeResourceToString(resource);
        LOG.debug("Validated Resource Content \n" + resourceAsJson);
        //ValidationResult validationResult = validator.validateWithResult(resource);
        ValidationResult validationResult = validator.validateWithResult(resourceAsJson);
        for (SingleValidationMessage validationMessage : validationResult.getMessages()) {
            ResultSeverityEnum severity = validationMessage.getSeverity();
            String locationString = validationMessage.getLocationString();
            Integer locationLine = validationMessage.getLocationLine();
            Integer locationCol = validationMessage.getLocationCol();
            String message = validationMessage.getMessage();
            String logMessage = severity + " " + locationString + " Line " + locationLine + " Col " + locationCol + " : " + message;

            if (!isIgnorableError(validationMessage)) {
                if (severity == ResultSeverityEnum.ERROR) {
                    LOG.error(logMessage);
                    bundleResultCounter.errors++;
                    fullResultCounter.errors++;
                } else if (severity == ResultSeverityEnum.WARNING) {
                    LOG.warn(logMessage);
                    bundleResultCounter.warnings++;
                    fullResultCounter.warnings++;
                } else {
                    LOG.info(logMessage);
                    bundleResultCounter.valid++;
                    fullResultCounter.valid++;
                }
            } else {
                LOG.info("IGNORED " + logMessage);
                bundleResultCounter.ignored++;
                fullResultCounter.ignored++;
            }
            bundleResultCounter.resources++;
            fullResultCounter.resources++;
        }
    }

    /**
     * @param validationMessage
     * @return <code>true</code> if the text of the message contains a String of
     *         {@link #VALIDATION_IGNORE_ERROR_MESSAGE_PARTS}
     */
    private static boolean isIgnorableError(SingleValidationMessage validationMessage) {
        String message = validationMessage.getMessage();
        for (String ignoreMessagePart : VALIDATION_IGNORE_ERROR_MESSAGE_PARTS) {
            if (message.contains(ignoreMessagePart)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param f
     * @return
     * @throws ConfigurationException
     * @throws DataFormatException
     * @throws IOException
     */
    static public Bundle readBundle(File f) throws ConfigurationException, DataFormatException, IOException {
        FhirContext ctx = FhirContext.forR4();
        try (FileInputStream resourceStream = new FileInputStream(f)) {
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
    public void validateBundle(Bundle bundle) throws ConfigurationException, DataFormatException, FileNotFoundException {
        validate(bundle);
        // Alternative: but a lot of errors (reference errors) will only be detected if you validate the whole bundle
        //        for (BundleEntryComponent e : bundle.getEntry()) {
        //            validate(e.getResource());
        //        }
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
        fhirValidator.validate(args);
        LOG.info("Finished Validation Process in " + stopwatch.stop());
        System.exit(0);
    }
}
