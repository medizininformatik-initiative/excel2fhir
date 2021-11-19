package de.uni_leipzig.imise;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

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

import ca.uhn.fhir.context.ConfigurationException;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import de.uni_leipzig.imise.utils.Sys;


public class FHIRValidator {
    FhirValidator validator;
    int warn = 0;
    int err = 0;
    int filter = 0;
    public FHIRValidator() {
        // Create a validator. Note that for good performance you can create as many validator objects
        // as you like, but you should reuse the same validation support object in all of the,.
        FhirContext ctx = FhirContext.forR4();
        validator = ctx.newValidator();
    }
    public void init() {

        FhirContext ctx = FhirContext.forR4();

        NpmPackageValidationSupport npmPackageSupport = new NpmPackageValidationSupport(ctx);
        File dir = new File(this.getClass().getClassLoader().getResource("fhir").getPath());
        for (File f : dir.listFiles()) {
            if (f.isFile()) {
                try {
                    System.out.println("loadPackage: " + f.getCanonicalPath());
                    npmPackageSupport.loadPackageFromClasspath("fhir/" + f.getName());
                } catch (IOException e) {
                    Sys.err(e.getMessage());
                }
            }
        }


        // Create a support chain including the NPM Package Support
        ValidationSupportChain validationSupportChain = new ValidationSupportChain(
                npmPackageSupport,
                new DefaultProfileValidationSupport(ctx),
                new CommonCodeSystemsTerminologyService(ctx),
                new InMemoryTerminologyServerValidationSupport(ctx),
                new SnapshotGeneratingValidationSupport(ctx)
                );
        CachingValidationSupport validationSupport = new CachingValidationSupport(validationSupportChain);

        FhirInstanceValidator instanceValidator = new FhirInstanceValidator(validationSupport);
        validator.registerValidatorModule(instanceValidator);
    }
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
                filter++;
                continue;
            }
            System.out.println(i.getSeverity() + " " + i.getLocationString() + " " + i.getLocationLine() + ": " + i.getMessage());
            if (i.getSeverity() == ResultSeverityEnum.ERROR) {
                err++;
            }
            if (i.getSeverity() == ResultSeverityEnum.WARNING) {
                warn++;
            }
        }
    }

    static public Bundle readBundle(File f) throws ConfigurationException, DataFormatException, FileNotFoundException {
        FhirContext ctx = FhirContext.forR4();
        IBaseResource r = ctx.newJsonParser().parseResource(new FileInputStream(f));
        assert r instanceof Bundle;
        return (Bundle) r;
    }
    public void validateBundle(Bundle bundle) throws ConfigurationException, DataFormatException, FileNotFoundException {
        for (BundleEntryComponent e : bundle.getEntry()) {
            validate(e.getResource());
        }

        // Geht auch:
        //        validate(bundle);
    }
    public void log() {
        System.out.println("Warnings: " + warn);
        System.out.println("Error: " + err);
        System.out.println("Filtered: " + filter);
    }

    static public void main(String args[]) throws IOException {
        FHIRValidator v = new FHIRValidator();
        v.init();
        for (String f : args)  {

            //        String f = "outputGlobal\\POLAR_Testdaten_UKB-UKB001.json";

            v.validateBundle(readBundle(new File(f)));
        }
        v.log();
    }
}
