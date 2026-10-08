package de.uni_leipzig.life.csv2fhir;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.List;

import org.hl7.fhir.r4.model.*;
import org.junit.Test;

public class ObservationOutputPolicyTest {
    private Observation observation(String category) {
        Observation value = new Observation();
        value.setId("measurement");
        value.addCategory().addCoding().setSystem("http://terminology.hl7.org/CodeSystem/observation-category").setCode(category);
        value.setValue(quantity());
        value.addComponent().setValue(quantity());
        value.addComponent().setValue(new StringType("unchanged"));
        value.addReferenceRange().setLow(quantity());
        return value;
    }

    private Quantity quantity() {
        return new Quantity().setValue(new BigDecimal("37.20")).setComparator(Quantity.QuantityComparator.LESS_THAN)
                .setSystem("http://unitsofmeasure.org").setCode("Cel").setUnit("degrees Celsius");
    }

    private ObservationOutputPolicy policy(String settings) {
        ContractConfiguration configuration = ContractConfiguration.parse("CONFIGURATION_VERSION=1\n" + settings);
        assertTrue(configuration.unsupportedSettings().toString(), configuration.unsupportedSettings().isEmpty());
        return new ObservationOutputPolicy(configuration);
    }

    @Test public void movesOnlyMeasurementCodesWithoutMutatingSourceOrReferenceRanges() {
        for (String category : List.of("laboratory", "vital-signs")) {
            String property = category.equals("laboratory") ? "LABORATORY" : "VITAL_SIGNS";
            Observation source = observation(category);
            var policy = policy("OBSERVATION_" + property + "_UCUM_CODE_IN_UNIT=true\n");
            Observation output = (Observation)policy.output(source);
            for (Quantity value : List.of(output.getValueQuantity(), output.getComponentFirstRep().getValueQuantity())) {
                assertEquals("Cel", value.getUnit());
                assertFalse(value.hasCode());
                assertEquals("http://unitsofmeasure.org", value.getSystem());
                assertEquals(new BigDecimal("37.20"), value.getValue());
                assertEquals(Quantity.QuantityComparator.LESS_THAN, value.getComparator());
            }
            assertEquals("unchanged", output.getComponent().get(1).getValue().primitiveValue());
            assertEquals("Cel", output.getReferenceRangeFirstRep().getLow().getCode());
            assertEquals("degrees Celsius", source.getValueQuantity().getUnit());
            assertEquals("Cel", source.getValueQuantity().getCode());
            assertEquals("Cel", source.getComponentFirstRep().getValueQuantity().getCode());
            assertTrue(output.equalsDeep(policy.output(output)));
            String json = ca.uhn.fhir.context.FhirContext.forR4Cached().newJsonParser().encodeResourceToString(output);
            Observation roundtrip = (Observation)ca.uhn.fhir.context.FhirContext.forR4Cached().newJsonParser().parseResource(json);
            assertFalse(roundtrip.getValueQuantity().hasCode());
            assertEquals("Cel", roundtrip.getValueQuantity().getUnit());
        }
    }

    @Test public void defaultsCategorySelectionAndDisabledResourcesPreserveMeasurements() {
        Observation lab = observation("laboratory");
        Observation vital = observation("vital-signs");
        assertSame(lab, policy("").output(lab));
        assertSame(lab, new ObservationOutputPolicy(null).output(lab));
        assertSame(vital, policy("OBSERVATION_LABORATORY_UCUM_CODE_IN_UNIT=true\n").output(vital));
        assertSame(lab, policy("OBSERVATION_LABORATORY_UCUM_CODE_IN_UNIT=true\nOBSERVATION_LABORATORY_ENABLED=false\n").output(lab));
        Patient patient = new Patient();
        assertSame(patient, policy("OBSERVATION_LABORATORY_UCUM_CODE_IN_UNIT=true\n").output(patient));
    }

    @Test public void missingCodesOtherSystemsAndNonQuantityValuesArePreserved() {
        Observation source = observation("laboratory");
        source.getValueQuantity().setSystem("urn:custom");
        source.getComponentFirstRep().getValueQuantity().setCodeElement(null);
        source.addComponent().setValue(new CodeableConcept().setText("positive"));
        Observation output = (Observation)policy("OBSERVATION_LABORATORY_UCUM_CODE_IN_UNIT=true\n").output(source);
        assertTrue(source.equalsDeep(output));
    }
    @Test public void numericDarOverrideTakesPrecedenceOverTheUnitErrorScenario() {
        var configuration = ContractConfiguration.parse("CONFIGURATION_VERSION=1\n"
                + "OBSERVATION_LABORATORY_UCUM_CODE_IN_UNIT=true\n"
                + "DAR_LABORATORY_VALUE_X_NUMERIC_MEASUREMENT=masked\n");
        Observation source = observation("laboratory");
        Resource changed = new ObservationOutputPolicy(configuration).output(source);
        Observation output = (Observation)new DarOverrides(configuration).output(changed);
        assertFalse(output.hasValue());
        assertEquals("masked", output.getDataAbsentReason().getCodingFirstRep().getCode());
        assertEquals("Cel", source.getValueQuantity().getCode());
    }

    @Test public void absentCodeExtensionsArePreservedWhenNoCodeValueExists() {
        Observation source = observation("laboratory");
        Quantity value = source.getValueQuantity();
        value.setCodeElement(new CodeType());
        value.getCodeElement().addExtension("http://hl7.org/fhir/StructureDefinition/data-absent-reason", new CodeType("unknown"));
        value.setUnitElement(new StringType());
        value.getUnitElement().addExtension("http://hl7.org/fhir/StructureDefinition/data-absent-reason", new CodeType("unknown"));
        Observation output = (Observation)policy("OBSERVATION_LABORATORY_UCUM_CODE_IN_UNIT=true\n").output(source);
        assertTrue(value.equalsDeep(output.getValueQuantity()));
    }

}
