package de.uni_leipzig.life.synthifhir.converter;

import static org.junit.Assert.*;
import java.util.List;
import org.hl7.fhir.r4.model.*;
import org.junit.Test;
import ca.uhn.fhir.context.FhirContext;
import de.uni_leipzig.life.synthifhir.OutputFileType;

public class InputCodeTest {
    @Test public void arbitraryCodesAndDarSurviveCopiesAndSerialization() {
        for (String code : List.of("female", "made-up", "!dar:made-up")) {
            Patient patient = new Patient();
            patient.setGenderElement(InputCode.of(patient.getGenderElement(), code));
            for (Patient output : List.of(patient, patient.copy())) {
                for (OutputFileType format : List.of(OutputFileType.JSON, OutputFileType.XML, OutputFileType.NDJSON)) {
                    String encoded = format.getParser().encodeResourceToString(output);
                    assertTrue(encoded, encoded.contains(code.replace("!dar:", "")));
                    if (code.startsWith("!dar:")) assertTrue(encoded.contains("data-absent-reason"));
                }
            }
        }
    }

    @Test public void optionalValidatorReportsFreeCodesInsteadOfPreventingSerialization() {
        var context = FhirContext.forR4Cached();
        var support = new org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain(
                new ca.uhn.fhir.context.support.DefaultProfileValidationSupport(context),
                new org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService(context),
                new org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport(context));
        var validator = context.newValidator().registerValidatorModule(
                new org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator(support));
        for (String value : List.of("made-up", "!dar:made-up")) {
            Patient patient = new Patient();
            patient.setGenderElement(InputCode.of(patient.getGenderElement(), value));
            String encoded = context.newJsonParser().encodeResourceToString(patient);
            var result = validator.validateWithResult(encoded);
            assertFalse(result.getMessages().toString(), result.isSuccessful());
            assertTrue(result.getMessages().toString(), result.getMessages().stream()
                    .anyMatch(message -> message.getMessage().contains("made-up")));
            assertEquals(encoded, context.newJsonParser().encodeResourceToString(patient));
        }
    }
}
