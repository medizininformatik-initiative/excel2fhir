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
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;


public class FHIRValidator {
    FhirValidator validator;
    public FHIRValidator() throws IOException {
        FhirContext ctx = FhirContext.forR4();
        
        NpmPackageValidationSupport npmPackageSupport = new NpmPackageValidationSupport(ctx);
        File dir = new File(this.getClass().getClassLoader().getResource("package").getPath());
        for (File f : dir.listFiles()) {
            if (f.isFile()) {
                System.out.println("loadPackage: " + f.getCanonicalPath());            
                npmPackageSupport.loadPackageFromClasspath("package/" + f.getName());
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

        // Create a validator. Note that for good performance you can create as many validator objects
        // as you like, but you should reuse the same validation support object in all of the,.
        validator = ctx.newValidator();
        FhirInstanceValidator instanceValidator = new FhirInstanceValidator(validationSupport);
        validator.registerValidatorModule(instanceValidator);      
    }
    public void validate(Resource r) {
        // Perform the validation
        System.out.println("validate: " + r.getId());
        ValidationResult outcome = validator.validateWithResult(r);
        
        for (SingleValidationMessage i : outcome.getMessages()) {        
            System.out.println(i.getLocationString() + " " + i.getLocationLine() + ": " + i.getMessage());
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
    static public void main(String args[]) throws IOException {
        
        FHIRValidator v = new FHIRValidator();
        String f = "C:\\Users\\frank\\Nextcloud\\Shared\\POLAR\\Testdaten\\POLAR_Testdaten_UKB\\POLAR_Testdaten_UKB-UKB002.json";

        v.validateBundle(readBundle(new File(f)));
        // Create a test patient to validate
//        Patient patient = new Patient();
//        patient.getMeta().addProfile("https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Patient");
//        // System but not value set for NHS identifier (this should generate an error)
//        patient.addIdentifier().setSystem("https://fhir.nhs.uk/Id/nhs-number");
//        v.validate(patient);
    }
}
