package de.uni_leipzig.life.csv2fhir.converter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.hl7.fhir.r4.model.MedicationRequest.MedicationRequestIntent;

/** One input contract shared by Excel preflight and CSV conversion. */
public final class MedicationValues {
    public static final String REQUEST = "Verordnung";
    public static final String ADMINISTRATION = "Verabreichung";
    public static final String STATEMENT = "Medikationsaussage";
    public static final Map<String, List<String>> STATUSES = Map.of(
            REQUEST, List.of("active", "on-hold", "cancelled", "completed", "entered-in-error", "stopped", "draft", "unknown"),
            ADMINISTRATION, List.of("in-progress", "not-done", "on-hold", "completed", "entered-in-error", "stopped", "unknown"),
            STATEMENT, List.of("active", "completed", "entered-in-error", "intended", "stopped", "on-hold", "unknown", "not-taken"));
    public static final Set<String> PRODUCT_SYSTEMS = Set.of("PZN", "RxNorm", "CVX", DiagnosisValues.SNOMED);
    public static final Set<String> INGREDIENT_SYSTEMS = Set.of("ASK", "UNII", "RxNorm", DiagnosisValues.SNOMED);

    private MedicationValues() { }

    public static String value(Function<String, String> input, String key) {
        String value = input.apply(key);
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static List<String> errors(Function<String, String> input) {
        List<String> errors = new ArrayList<>();
        Function<String, String> get = key -> value(input, key);
        String type = get.apply("Medikationstyp");
        if (!STATUSES.containsKey(type == null ? "" : type)) {
            errors.add("Medikationstyp must be Verordnung, Verabreichung or Medikationsaussage");
        } else {
            String status = get.apply("Status");
            if (status != null && !STATUSES.get(type).contains(status)) errors.add("Status is not valid for " + type);
        }
        String intent = get.apply("Absicht");
        if (!REQUEST.equals(type) && intent != null) errors.add("Absicht applies only to Verordnung");
        if (intent != null) {
            try { MedicationRequestIntent.fromCode(intent); }
            catch (RuntimeException e) { errors.add("Unknown medication request intent: " + intent); }
        }
        if (REQUEST.equals(type) && (get.apply("Beginn") != null || get.apply("Ende") != null)) {
            errors.add("Verordnung: leave Beginn/Ende empty; Dokumentationszeitpunkt specifies the creation time");
        }
        if (ADMINISTRATION.equals(type) && get.apply("Dokumentationszeitpunkt") != null) {
            errors.add("Verabreichung: specify the administration time in Beginn and leave Dokumentationszeitpunkt empty");
        }
        if ((ADMINISTRATION.equals(type) || STATEMENT.equals(type)) && get.apply("Beginn") == null) {
            errors.add("Beginn is required; select Unbekannt for an unknown timestamp");
        }
        for (String key : List.of("Dokumentationszeitpunkt", "Beginn", "Ende")) {
            try { ClinicalValues.date(get.apply(key)); }
            catch (Exception e) { errors.add(key + ": invalid date or Data Absent Reason"); }
        }
        try {
            var start = ClinicalValues.date(get.apply("Beginn"));
            var end = ClinicalValues.date(get.apply("Ende"));
            if (start != null && end != null && start.hasValue() && end.hasValue()
                    && start.getPrecision() == end.getPrecision() && start.getValue().after(end.getValue())) {
                errors.add("End precedes start");
            }
        } catch (Exception e) { /* Individual date errors are reported above. */ }
        checkCode(get, "Präparatcode", "Präparatcodesystem", PRODUCT_SYSTEMS, errors);
        checkCode(get, "Wirkstoffcode", "Wirkstoffcodesystem", INGREDIENT_SYSTEMS, errors);
        String ingredients = get.apply("Wirkstoffcode");
        if (ingredients != null && !DiagnosisValues.isAbsent(ingredients)) {
            var seen = new java.util.HashSet<String>();
            for (String ingredient : ingredients.split(";", -1)) {
                String code = ingredient.trim();
                if (code.isEmpty() || DiagnosisValues.isAbsent(code) || !seen.add(code)) {
                    errors.add("Wirkstoffcode: separate distinct, nonempty codes with semicolons");
                } else if ("UNII".equals(get.apply("Wirkstoffcodesystem")) && !code.matches("[A-Z0-9]{10}")) {
                    errors.add("UNII must contain ten uppercase letters or digits");
                }
            }
        }
        if (get.apply("Wirkstoffcode") == null) errors.add("Wirkstoffcode is required; for an unknown code, select Unbekannt and specify the code system");
        if (get.apply("Präparatcode") == null && get.apply("ATC-Code") == null && get.apply("Präparatbezeichnung") == null) {
            errors.add("At least one of Präparatcode, ATC-Code or Präparatbezeichnung is required");
        }
        String atc = get.apply("ATC-Code"), version = get.apply("ATC-Version");
        if ((atc == null) != (version == null)) errors.add("Specify both ATC-Code and ATC-Version");
        try { DiagnosisValues.absentReason(atc); }
        catch (RuntimeException e) { errors.add("ATC-Code: invalid Data Absent Reason"); }
        if (version != null && !version.matches("[0-9]{4}")) errors.add("Specify ATC-Version as a four-digit year");
        if (ADMINISTRATION.equals(type) && get.apply("Einzeldosis") == null
                && (get.apply("Dosierungstext") != null || get.apply("Dosen pro Tag") != null)) {
            errors.add("Administration dosage requires Einzeldosis; select Unbekannt for an unknown dose (FHIR mad-1)");
        }
        if (get.apply("Dosiereinheit") != null && get.apply("Einzeldosis") == null) errors.add("Dosiereinheit ohne Einzeldosis");
        for (String key : List.of("Einzeldosis", "Dosen pro Tag")) {
            String number = get.apply(key);
            if (number == null) continue;
            try {
                if ("Einzeldosis".equals(key) && DiagnosisValues.absentReason(number) != null) continue;
                BigDecimal n = de.uni_leipzig.life.csv2fhir.utils.DecimalUtil.parseDecimal(number);
                if (n.signum() < 0 || ("Dosen pro Tag".equals(key) && n.signum() == 0)) errors.add(key + ": invalid quantity");
            } catch (Exception e) { errors.add(key + ": a number is required"); }
        }
        return errors;
    }

    private static void checkCode(Function<String, String> get, String column, String systemColumn,
            Set<String> systems, List<String> errors) {
        String code = get.apply(column), system = get.apply(systemColumn);
        if (code == null && system == null) return;
        if (code == null || system == null || !systems.contains(system)) {
            errors.add(column + " requires a code and a matching " + systemColumn);
            return;
        }
        try {
            var absent = DiagnosisValues.absentReason(code);
            if ("PZN".equals(system) && absent == null && !code.matches("[0-9]{8}")) errors.add("PZN must contain eight digits");
        } catch (RuntimeException e) { errors.add(column + ": invalid Data Absent Reason"); }
    }
}
