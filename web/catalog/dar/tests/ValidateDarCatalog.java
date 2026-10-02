import de.uni_leipzig.imise.validate.FHIRValidator;



import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;

import org.hl7.fhir.r4.model.*;



import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class ValidateDarCatalog {
    public static void main(String[] args) throws Exception {
        load();
        var probe = new ValidateDarCatalog();
        probe.catalogueMatchesItsProfileAndSemanticSources();
        probe.bundledPatientProfileAcceptsMaskedBirthDate();
        probe.observationReasonsReplaceTheMeasurement();
        probe.conditionEndStillRequiresAnActualClinicalStatus();
        System.out.println("DAR catalogue: 4 validation checks passed");
    }

    private static void assertTrue(boolean condition) { assertTrue("Assertion failed", condition); }
    private static void assertTrue(String message, boolean condition) {
        if (!condition) throw new AssertionError(message);
    }
    private static void assertFalse(boolean condition) { assertTrue(!condition); }
    private static void assertEquals(Object expected, Object actual) {
        assertTrue("Expected " + expected + ", got " + actual, java.util.Objects.equals(expected, actual));
    }
    private static void assertEquals(long expected, long actual) { assertTrue(expected == actual); }

    private static JsonObject catalog;
    private static FHIRValidator validator;

    public static void load() throws Exception {
        catalog = JsonParser.parseString(Files.readString(Path.of("web/catalog/dar/generated/catalog.json"))).getAsJsonObject();
        validator = new FHIRValidator(null);
    }

    public void catalogueMatchesItsProfileAndSemanticSources() throws Exception {
        var sources = catalog.getAsJsonObject("sources");
        for (String key : new String[] {"rules", "labels"}) {
            var source = sources.getAsJsonObject(key);
            assertEquals(source.get("sha256").getAsString(), sha256(Path.of(source.get("file").getAsString())));
        }
        assertEquals(sources.get("generatorSha256").getAsString(), sha256(Path.of("web/catalog/dar/generator/generate_catalog.py")));
        for (var value : sources.getAsJsonArray("packages")) {
            var source = value.getAsJsonObject();
            assertEquals(source.get("sha256").getAsString(), sha256(Path.of("src/main/resources/fhir", source.get("file").getAsString())));
        }
        var ids = new HashSet<String>();
        var codes = new HashSet<String>();
        for (var code : catalog.getAsJsonArray("codes")) codes.add(code.getAsJsonObject().get("code").getAsString());
        for (var value : catalog.getAsJsonArray("fields")) {
            var field = value.getAsJsonObject();
            assertTrue(ids.add(field.get("id").getAsString()));
            assertFalse(field.getAsJsonArray("profileEvidence").isEmpty());
            for (var code : field.getAsJsonArray("allowedCodes")) assertTrue(codes.contains(code.getAsString()));
            if (!field.get("semanticGroup").getAsString().equals("numeric-measurement")) {
                assertFalse(field.getAsJsonArray("allowedCodes").toString().contains("not-a-number"));
            }
        }
    }

    public void bundledPatientProfileAcceptsMaskedBirthDate() {
        Patient patient = new Patient();
        patient.getMeta().addProfile("https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Patient");
        patient.getBirthDateElement().addExtension(dar("masked"));
        assertEquals(FHIRValidator.ValidationResultType.VALID, validator.validate(patient));
    }

    public void observationReasonsReplaceTheMeasurement() {
        var field = catalog.getAsJsonArray("fields").asList().stream().map(value -> value.getAsJsonObject())
                .filter(value -> value.get("id").getAsString().equals("VitalSigns.value[x].numeric-measurement"))
                .findFirst().orElseThrow();
        for (var code : field.getAsJsonArray("allowedCodes")) {
            Observation observation = new Observation().setStatus(Observation.ObservationStatus.FINAL);
            observation.setCode(new CodeableConcept().setText("Synthetic measurement"));
            observation.setDataAbsentReason(new CodeableConcept(new Coding(
                    "http://terminology.hl7.org/CodeSystem/data-absent-reason", code.getAsString(), null)));
            var result = validator.validate(observation);
            assertTrue(code.getAsString() + ": " + result, result == FHIRValidator.ValidationResultType.VALID
                    || result == FHIRValidator.ValidationResultType.WARNING);
        }
        Observation invalid = new Observation().setStatus(Observation.ObservationStatus.FINAL);
        invalid.setCode(new CodeableConcept().setText("Synthetic measurement"));
        invalid.setValue(new Quantity().setValue(1));
        invalid.setDataAbsentReason(new CodeableConcept(new Coding(
                "http://terminology.hl7.org/CodeSystem/data-absent-reason", "unknown", null)));
        assertEquals(FHIRValidator.ValidationResultType.ERROR, validator.validate(invalid));
    }

    public void conditionEndStillRequiresAnActualClinicalStatus() {
        Condition condition = new Condition();
        condition.getMeta().addProfile("https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose");
        condition.setSubject(new Reference("Patient/p1"));
        condition.setCode(new CodeableConcept(new Coding("http://snomed.info/sct", null, null)));
        condition.getCode().getCodingFirstRep().getCodeElement().addExtension(dar("unknown"));
        condition.getRecordedDateElement().addExtension(dar("unknown"));
        condition.setAbatement(new DateTimeType("2026-01-01"));
        condition.setClinicalStatus((CodeableConcept) new CodeableConcept().addExtension(dar("unknown")));
        assertEquals(FHIRValidator.ValidationResultType.ERROR, validator.validate(condition));
    }

    private static Extension dar(String code) {
        return new Extension("http://hl7.org/fhir/StructureDefinition/data-absent-reason", new CodeType(code));
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
