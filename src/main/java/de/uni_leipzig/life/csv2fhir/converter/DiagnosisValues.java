package de.uni_leipzig.life.csv2fhir.converter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.codesystems.DataAbsentReason;

/** Explicit selections used by the diagnosis sheet. No terminology lookup or code repair. */
public final class DiagnosisValues {
    public static final String SNOMED = "SNOMED CT (Version nicht angegeben)";
    public static final String DAR_PREFIX = "!dar:";
    public static final Map<String, String> CLINICAL = selections(
            "Aktiv", "active", "Rezidiv", "recurrence", "Rückfall", "relapse",
            "Inaktiv", "inactive", "Remission", "remission", "Abgeklungen", "resolved");
    public static final Map<String, String> VERIFICATION = selections(
            "Unbestätigt", "unconfirmed", "Vorläufig", "provisional", "Differentialdiagnose", "differential",
            "Bestätigt", "confirmed", "Widerlegt", "refuted", "Irrtümlich erfasst", "entered-in-error");

    public static final Map<String, String> ABSENT_LABELS = loadAbsentLabels();

    private static Map<String, String> loadAbsentLabels() {
        try (var reader = new java.io.InputStreamReader(java.util.Objects.requireNonNull(
                DiagnosisValues.class.getResourceAsStream("/workbook-absent-reasons.json")),
                java.nio.charset.StandardCharsets.UTF_8)) {
            Map<String, String> labels = new LinkedHashMap<>();
            com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().entrySet()
                    .forEach(entry -> labels.put(entry.getValue().getAsString(), entry.getKey()));
            return Collections.unmodifiableMap(labels);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    public static boolean isAbsent(String value) {
        return value != null && (value.startsWith(DAR_PREFIX) || ABSENT_LABELS.containsKey(value));
    }

    private DiagnosisValues() {
    }

    private static Map<String, String> selections(String... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("Selections require complete label/code pairs");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 1; i < pairs.length; i += 2) {
            values.put(pairs[i - 1], pairs[i]);
        }
        return Collections.unmodifiableMap(values);
    }

    public static Map<String, Coding> systems() {
        Map<String, Coding> values = new LinkedHashMap<>();
        values.put(SNOMED, new Coding().setSystem("http://snomed.info/sct"));
        for (int year = 2009; year <= 2026; year++) {
            values.put("ICD-10-GM " + year, new Coding().setSystem("http://fhir.de/CodeSystem/bfarm/icd-10-gm")
                    .setVersion(Integer.toString(year)));
        }
        return values;
    }

    public static Extension absentReason(String value) {
        if (!isAbsent(value)) {
            return null;
        }
        String reason = ABSENT_LABELS.getOrDefault(value, value.startsWith(DAR_PREFIX)
                ? value.substring(DAR_PREFIX.length()) : value);
        DataAbsentReason parsed = DataAbsentReason.fromCode(reason);
        if (parsed == null || parsed == DataAbsentReason.NULL) {
            throw new IllegalArgumentException("Unknown data absent reason: " + reason);
        }
        return new Extension("http://hl7.org/fhir/StructureDefinition/data-absent-reason", new CodeType(reason));
    }
}
