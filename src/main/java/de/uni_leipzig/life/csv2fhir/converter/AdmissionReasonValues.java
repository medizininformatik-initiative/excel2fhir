package de.uni_leipzig.life.csv2fhir.converter;

import java.util.Map;

import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;

/** Fourth component of the German admission reason, separate from encounter class. */
public final class AdmissionReasonValues {
    public static final String COLUMN = "Aufnahmegrund (4. Stelle)";
    public static final String SYSTEM = "http://fhir.de/CodeSystem/dkgev/AufnahmegrundVierteStelle";
    public static final String EXTENSION = "http://fhir.de/StructureDefinition/Aufnahmegrund";
    public static final Map<String, String> VALUES = Map.of(
            "Normalfall", "1",
            "Arbeitsunfall/Berufskrankheit", "2",
            "Verkehrsunfall/Sportunfall/Sonstiger Unfall", "3",
            "Hinweis auf Einwirkung von äußerer Gewalt", "4",
            "Kriegsbeschädigten-Leiden/BVG-Leiden", "6",
            "Notfall", "7");

    private AdmissionReasonValues() {
    }

    public static Extension extension(String selection) {
        if (selection == null || selection.isBlank()) {
            return null;
        }
        Coding coding = new Coding().setSystem(SYSTEM);
        Extension absent = DiagnosisValues.absentReason(selection);
        if (absent != null) {
            coding.getCodeElement().addExtension(absent);
        } else {
            String code = VALUES.get(selection);
            if (code == null) {
                throw new IllegalArgumentException("Unknown admission reason: " + selection);
            }
            coding.setCode(code);
        }
        Extension reason = new Extension(EXTENSION);
        reason.addExtension("VierteStelle", coding);
        return reason;
    }
}
