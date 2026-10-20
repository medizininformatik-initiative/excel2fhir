package de.uni_leipzig.life.synthifhir.converter;

import java.util.LinkedHashMap;
import java.util.Map;
import org.hl7.fhir.r4.model.*;
import de.uni_leipzig.life.synthifhir.Converter;
import de.uni_leipzig.life.synthifhir.TableColumnIdentifier;
import de.uni_leipzig.life.synthifhir.utils.DateUtil;

/** Shared explicit clinical inputs. Blank is omitted; DAR must be entered explicitly. */
public final class ClinicalValues {
    public enum Column implements TableColumnIdentifier {
        Codesystem, Zusatzcode, Zusatzcodesystem, Version, Zusatzversion, Ende, Status, Kategorie,
        Absicht, Dosierungstext, Werttyp, Wertcode, Wertcodesystem, Untersuchung_ID,
        Komponente_von, Ausgabezeitpunkt, Einheitencode, Einheitencodesystem, Wirkstoffcode, Wirkstoffcodesystem, Straße, Postleitzahl, Ort, Bundesland, Land, Sterbezeitpunkt, Dokumenttext, Dokumentcode, Dokumentcodesystem, Dokumentbezeichner;
        @Override public boolean isMandatory() { return false; }
        @Override public String toString() { return name().replace('_', ' '); }
    }
    private ClinicalValues() { }
    public static String get(Converter c, Column column) {
        String value = c.get(column);
        return value == null || value.isBlank() ? null : value;
    }
    public static String resourceId(String patient, String type, String source) {
        return type + "-" + java.util.UUID.nameUUIDFromBytes((patient + "|" + type + "|" + source)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    public static Map<String, Coding> systems() {
        Map<String, Coding> values = new LinkedHashMap<>(DiagnosisValues.systems());
        values.put("LOINC", new Coding().setSystem("http://loinc.org"));
        values.put("PZN", new Coding().setSystem("http://fhir.de/CodeSystem/ifa/pzn"));
        values.put("RxNorm", new Coding().setSystem("http://www.nlm.nih.gov/research/umls/rxnorm"));
        values.put("CVX", new Coding().setSystem("http://hl7.org/fhir/sid/cvx"));
        values.put("OPS", new Coding().setSystem("http://fhir.de/CodeSystem/bfarm/ops"));
        values.put("ATC", new Coding().setSystem("http://fhir.de/CodeSystem/bfarm/atc"));
        values.put("ATC 2025", new Coding().setSystem("http://fhir.de/CodeSystem/bfarm/atc").setVersion("2025"));
        values.put("ATC 2026", new Coding().setSystem("http://fhir.de/CodeSystem/bfarm/atc").setVersion("2026"));
        values.put("ASK", new Coding().setSystem("http://fhir.de/CodeSystem/ask"));
        values.put("UNII", new Coding().setSystem("http://fdasis.nlm.nih.gov"));
        for (int year = 2009; year <= 2026; year++) {
            values.put("OPS " + year, new Coding().setSystem("http://fhir.de/CodeSystem/bfarm/ops").setVersion("" + year));
        }
        return values;
    }
    public static Coding coding(String code, String selection) {
        if ((code == null || code.isBlank()) && (selection == null || selection.isBlank())) return null;
        Coding coding = selection == null || selection.isBlank() ? new Coding() : DiagnosisValues.system(selection, systems());
        Extension absent = DiagnosisValues.absentReason(code);
        if (absent == null) coding.setCode(code); else coding.getCodeElement().addExtension(absent);
        return coding;
    }
    public static StringType text(String value) {
        if (value == null || value.isBlank()) return null;
        Extension absent = value.startsWith(DiagnosisValues.DAR_PREFIX) || value.endsWith(" (Data Absent Reason)")
                ? DiagnosisValues.absentReason(value) : null;
        return absent == null ? new StringType(value) : (StringType) new StringType().addExtension(absent);
    }
    public static InstantType instant(String value) {
        if (value == null || value.isBlank()) return null;
        Extension absent = DiagnosisValues.absentReason(value);
        return absent == null ? new InstantType(value) : (InstantType) new InstantType().addExtension(absent);
    }
    public static DateTimeType date(String value) throws Exception {
        if (value == null || value.isBlank()) return null;
        Extension absent = DiagnosisValues.absentReason(value);
        if (absent != null) return (DateTimeType) new DateTimeType().addExtension(absent);
        return value.matches("\\d{4}(-\\d{2}(-\\d{2})?)?(T.*)?") ? new DateTimeType(value) : DateUtil.parseDateTimeType(value);
    }
    public static CodeableConcept concept(String code, String system, String text) {
        CodeableConcept cc = new CodeableConcept();
        Coding coding = coding(code, system);
        if (coding != null) cc.addCoding(coding);
        if (text != null && !text.isBlank()) cc.setText(text);
        return cc;
    }
}
