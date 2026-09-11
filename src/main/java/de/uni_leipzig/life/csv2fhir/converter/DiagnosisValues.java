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

    private DiagnosisValues() {
    }

    private static Map<String, String> selections(String... pairs) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            values.put(pairs[i], pairs[i + 1]);
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
        if (value == null || !value.startsWith(DAR_PREFIX)) {
            return null;
        }
        String reason = value.substring(DAR_PREFIX.length());
        DataAbsentReason parsed = DataAbsentReason.fromCode(reason);
        if (parsed == null || parsed == DataAbsentReason.NULL) {
            throw new IllegalArgumentException("Unknown data absent reason: " + reason);
        }
        return new Extension("http://hl7.org/fhir/StructureDefinition/data-absent-reason", new CodeType(reason));
    }
}
