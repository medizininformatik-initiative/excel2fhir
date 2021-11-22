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

    /**  */
    int bundleWarnings = 0;

    /**  */
    int bundleErrors = 0;

    /**  */
    int bundleResources = 0;

    /**  */
    int fullWarnings = 0;

    /**  */
    int fullErrors = 0;

    /**  */
    int fullResources = 0;

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
                    log(inputFileName);
                } catch (Exception e) {
                    LOG.error("Could not validate bundle " + inputFileName);
                    continue;
                }
                resetBundleValidateResult();
            }
        }
        if (!validateOnlyOneFile) {
            log(null);
        }
        LOG.info("Finished Validating in " + stopwatch.stop());
    }

    /**
     *
     */
    public void init() {
        LOG.info("Start Init FHIR Validator Bundles...");
        Stopwatch stopwatch = Stopwatch.createStarted();
        FhirContext ctx = FhirContext.forR4();
        NpmPackageValidationSupport npmPackageSupport = new NpmPackageValidationSupport(ctx);
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
                    new DefaultProfileValidationSupport(ctx),
                    new CommonCodeSystemsTerminologyService(ctx),
                    new InMemoryTerminologyServerValidationSupport(ctx),
                    new SnapshotGeneratingValidationSupport(ctx));
            CachingValidationSupport validationSupport = new CachingValidationSupport(validationSupportChain);

            FhirInstanceValidator instanceValidator = new FhirInstanceValidator(validationSupport);
            validator.registerValidatorModule(instanceValidator);
        }
        LOG.info("Finished Init FHIR Validator Bundles in " + stopwatch.stop());
    }

    /**
     * Reset the validation result for a bundle
     */
    public void resetBundleValidateResult() {
        bundleErrors = 0;
        bundleWarnings = 0;
        bundleResources = 0;
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
     * @param r
     */
    public void validate(Resource r) {
        // Perform the validation
        //        System.out.println("validate: " + r.getId());
        ValidationResult outcome = validator.validateWithResult(r);

        for (SingleValidationMessage i : outcome.getMessages()) {
            if (i.getMessage().contains("Validation failed für \"http://loinc.org") ||
                    i.getMessage().contains("Unknown code 'http://loinc.org#") ||
                    i.getMessage().contains("Validation failed für \"http://fhir.de/CodeSystem/ask#") ||
                    i.getMessage().contains("Validation failed für \"http://fhir.de/CodeSystem/ifa/pzn") ||
                    i.getMessage().contains("http://terminology.hl7.org/CodeSystem/v2-0203#OBI") ||
                    i.getMessage().contains("Validation failed für \"http://snomed.info/sct")) {
                bundleResources++;
                fullResources++;
                continue;
            }
            String validationResultMessage = i.getSeverity() + " " + i.getLocationString() + " " + i.getLocationLine() + ": " + i.getMessage();
            if (i.getSeverity() == ResultSeverityEnum.ERROR) {
                LOG.error(validationResultMessage);
                bundleErrors++;
                fullErrors++;
            } else if (i.getSeverity() == ResultSeverityEnum.WARNING) {
                LOG.warn(validationResultMessage);
                bundleWarnings++;
                fullWarnings++;
            } else {
                LOG.info(validationResultMessage);
            }
        }
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
        for (BundleEntryComponent e : bundle.getEntry()) {
            validate(e.getResource());
        }
        // Alternative:
        //validate(bundle);
    }

    /**
     * @param bundleName
     */
    public void log(String bundleName) {
        boolean logFullErrors = bundleName == null;
        LOG.info(logFullErrors ? "All Bundles Result:" : "Bundle Result: (" + bundleName + ")");
        LOG.info("Errors   : " + (logFullErrors ? fullErrors : bundleErrors));
        LOG.info("Warnings : " + (logFullErrors ? fullWarnings : bundleWarnings));
        LOG.info("Resources: " + (logFullErrors ? fullResources : bundleResources));
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
