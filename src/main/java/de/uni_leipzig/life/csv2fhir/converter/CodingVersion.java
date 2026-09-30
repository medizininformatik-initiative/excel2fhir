package de.uni_leipzig.life.csv2fhir.converter;

import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.StringType;

/** Explicit version input: text, Data Absent Reason, or omission. */
public final class CodingVersion {
    private CodingVersion() { }

    public static Coding apply(Coding coding, String value) {
        if (coding == null) return null;
        coding.setVersionElement(null);
        if (value == null || value.isBlank()) return coding;
        Extension absent = DiagnosisValues.absentReason(value);
        if (absent == null) coding.setVersion(value);
        else coding.setVersionElement((StringType) new StringType().addExtension(absent));
        return coding;
    }
}
