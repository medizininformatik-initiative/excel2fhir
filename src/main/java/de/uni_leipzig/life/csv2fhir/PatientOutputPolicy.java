package de.uni_leipzig.life.csv2fhir;

import java.util.List;

import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

/** Applied only to output copies; patient identity stays available to conversion. */
public enum PatientOutputPolicy {
    GENERATE_REFERENCE("generate-reference"), REFERENCE_ONLY("reference-only"), NEITHER("neither");

    private final String value;
    PatientOutputPolicy(String value) { this.value = value; }

    public static PatientOutputPolicy fromValue(String value) {
        for (PatientOutputPolicy policy : values()) if (policy.value.equals(value)) return policy;
        throw new IllegalArgumentException("Unknown Patient mode: " + value);
    }

    /** Null denotes an intentionally omitted Patient. Other resource IDs never change. */
    public Resource output(Resource source) {
        if (this == GENERATE_REFERENCE) return source;
        if (source instanceof Patient) return null;
        if (this == REFERENCE_ONLY) return source;
        Resource output = source;
        for (var property : source.children()) {
            if (!List.of("subject", "patient").contains(property.getName())) continue;
            for (var value : property.getValues()) {
                if (value instanceof Reference && isPatient((Reference)value)) {
                    if (output == source) output = source.copy();
                    output.setProperty(property.getName(), null);
                    break;
                }
            }
        }
        return output;
    }

    private static boolean isPatient(Reference reference) {
        if (reference.hasType() && reference.getType().equals("Patient")) return true;
        return reference.hasReference() && "Patient".equals(reference.getReferenceElement().getResourceType());
    }
}
