package de.uni_leipzig.life.csv2fhir.converter;

import java.util.LinkedHashMap;
import java.util.Map;
import org.hl7.fhir.r4.model.*;
import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.TableColumnIdentifier;
import de.uni_leipzig.life.csv2fhir.utils.DateUtil;

/** Shared explicit clinical inputs. Blank is omitted; DAR must be entered explicitly. */
public final class ClinicalValues {
    public enum Column implements TableColumnIdentifier {
        Codesystem, Zusatzcode, Zusatzcodesystem, Ende, Status, Kategorie,
        Absicht, Dosierungstext, Werttyp, Wertcode, Wertcodesystem, Untersuchung_ID,
        Komponente_von, Ausgabezeitpunkt, Einheitencode, Wirkstoffcode, Wirkstoffcodesystem, Straße, Postleitzahl, Ort, Bundesland, Land, Sterbezeitpunkt, Dokumenttext, Dokumentcode, Dokumentcodesystem, Dokumentbezeichner;
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
        values.put("ASK", new Coding().setSystem("http://fhir.de/CodeSystem/ask"));
        for (int year = 2009; year <= 2026; year++) {
            values.put("OPS " + year, new Coding().setSystem("http://fhir.de/CodeSystem/bfarm/ops").setVersion("" + year));
        }
        return values;
    }
    public static Coding coding(String code, String selection) {
        if (code == null || code.isBlank()) return null;
        Coding system = systems().get(selection);
        if (system == null) throw new IllegalArgumentException("Explicit codesystem required: " + selection);
        Coding coding = system.copy();
        Extension absent = DiagnosisValues.absentReason(code);
        if (absent == null) coding.setCode(code); else coding.getCodeElement().addExtension(absent);
        return coding;
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
