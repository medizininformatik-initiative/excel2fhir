package de.uni_leipzig.imise.validate;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.junit.Test;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import de.uni_leipzig.imise.validate.FHIRValidator.ValidationResultType;
import de.uni_leipzig.life.csv2fhir.MultiSinglePatientBundlesFileWriter;
import de.uni_leipzig.life.csv2fhir.OutputFileType;

public class FHIRValidatorTest {
    private FHIRValidator validator(ResultSeverityEnum severity, String text) {
        FhirContext context = FhirContext.forR4();
        SingleValidationMessage message = new SingleValidationMessage();
        message.setSeverity(severity);
        message.setMessage(text);
        return new FHIRValidator(new FhirValidator(context) {
            @Override
            public ValidationResult validateWithResult(String input) {
                return new ValidationResult(context, text == null ? List.of() : List.of(message, message));
            }
        }, null);
    }

    @Test
    public void fatalAndInvalidCodesCannotBeIgnored() {
        for (ResultSeverityEnum severity : List.of(ResultSeverityEnum.ERROR, ResultSeverityEnum.FATAL)) {
            FHIRValidator validator = validator(severity, "Unknown code 'http://loinc.org#invalid'");
            assertEquals(ValidationResultType.ERROR, validator.validate("{}", true));
            assertTrue(validator.hasValidationProblems());
        }
    }

    @Test
    public void missingTerminologyIsIncompleteRatherThanValid() {
        FHIRValidator validator = validator(ResultSeverityEnum.ERROR,
                "Unable to expand ValueSet because CodeSystem could not be found: http://snomed.info/sct|version");
        assertEquals(ValidationResultType.NOT_CHECKED, validator.validate("{}", true));
        FHIRValidator missingValueSet = validator(ResultSeverityEnum.WARNING,
                "ValueSet http://example.org/lab vom Validator nicht gefunden");
        assertEquals(ValidationResultType.NOT_CHECKED, missingValueSet.validate("{}", true));
        assertTrue(validator.hasValidationProblems());
    }

    @Test
    public void countsValidationCallsNotMessages() throws Exception {
        FHIRValidator validator = validator(ResultSeverityEnum.WARNING, "Warning");
        validator.validate("{}", true);
        assertEquals(1, counter(validator, "resources"));
        assertEquals(2, counter(validator, "warnings"));
        validator = validator(ResultSeverityEnum.INFORMATION, null);
        assertEquals(ValidationResultType.VALID, validator.validate("{}", true));
        assertEquals(1, counter(validator, "resources"));
        assertEquals(1, counter(validator, "valid"));
    }

    private int counter(FHIRValidator validator, String name) throws Exception {
        var field = FHIRValidator.class.getDeclaredField("fullResultCounter");
        field.setAccessible(true);
        Object counter = field.get(validator);
        var value = counter.getClass().getDeclaredField(name);
        value.setAccessible(true);
        return value.getInt(counter);
    }

    @Test
    public void invalidBundleIsStillWrittenWithRawReport() throws Exception {
        var directory = Files.createTempDirectory("validation-preserves-data");
        FHIRValidator validator = validator(ResultSeverityEnum.ERROR, "Patient error");
        Bundle bundle = new Bundle().setType(Bundle.BundleType.TRANSACTION);
        bundle.addEntry().setResource(new Patient().setId("patient"));
        var writer = MultiSinglePatientBundlesFileWriter.create(directory.toFile(), "case-", validator,
                OutputFileType.NDJSON);
        writer.appendBundle(bundle);
        writer.closeWriterAndRenameOrDeleteIfEmpty("");
        assertTrue(Files.readString(directory.resolve("case.ndjson")).contains("patient"));
        try (var paths = Files.list(directory)) {
            var report = paths.filter(p -> p.toString().endsWith(".validation.json")).findFirst().orElseThrow();
            assertTrue(Files.readString(report).contains("Patient error"));
            assertTrue(Files.readString(report).contains("RETAINED"));
        }
        assertTrue(validator.hasValidationProblems());
        assertEquals(1, bundle.getEntry().size());
    }
}
