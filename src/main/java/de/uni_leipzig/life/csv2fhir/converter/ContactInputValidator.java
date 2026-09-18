package de.uni_leipzig.life.csv2fhir.converter;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Linear input preflight, without resource generation or terminology validation. */
public final class ContactInputValidator {
    public static final class Input {
        private final long row;
        private final String patient;
        private final Map<String, String> values;
        public Input(long row, String patient, Map<String, String> values) {
            this.row = row; this.patient = patient; this.values = values;
        }
        public long row() { return row; }
        public String patient() { return patient; }
        String get(String key) {
            String value = values.get(key);
            return value == null || value.isBlank() ? null : value.trim();
        }
    }
    public static final class Issue {
        private final long row;
        private final String field, message;
        public Issue(long row, String field, String message) {
            this.row = row; this.field = field; this.message = message;
        }
        public long row() { return row; }
        public String field() { return field; }
        public String message() { return message; }
    }
    private static final class Span {
        private final Date start, end;
        Span(Date start, Date end) { this.start = start; this.end = end; }
        Date start() { return start; }
        Date end() { return end; }
    }
    private static final Set<String> SECONDARY = Set.of("Operation", "Untersuchung und Behandlung", "Konsil");
    private static final class State {
        String number, classification, department;
        Span facility, primary, departmentPeriod;
        boolean bounded, derived, invalid;
        final List<Span> secondary = new ArrayList<>();
    }
    private final Map<String, State> patients = new HashMap<>();

    public List<Issue> accept(Input input) {
        List<Issue> issues = new ArrayList<>();
        Date start = date(input, "Start", true, issues);
        Date end = date(input, "Ende", false, issues);
        if (start != null && end != null && end.before(start))
            issue(issues, input, "Start/Ende", "Kontaktende liegt vor Beginn");
        String kind = input.get("Kontaktart");
        String department = input.get("Fachabteilung");
        boolean places = input.get("Station") != null || input.get("Zimmer") != null || input.get("Bett") != null;
        boolean secondary = SECONDARY.contains(kind == null ? "" : kind);
        if (kind != null && !EncounterConverter.CONTACT_KINDS.containsKey(kind))
            issue(issues, input, "Kontaktart", "Unbekannte Kontaktart: " + kind);
        if (kind != null && !places)
            issue(issues, input, "Kontaktart", "Kontaktart benötigt mindestens Station, Zimmer oder Bett");
        for (String old : List.of("Kontakt-ID", "Kontaktebene", "Übergeordneter Kontakt"))
            if (input.get(old) != null) issue(issues, input, old, "Veraltete Kontaktspalte: Fall auf die implizite Eingabe umstellen");
        String reason = input.get(AdmissionReasonValues.COLUMN);
        if (reason != null) {
            try { AdmissionReasonValues.extension(reason); }
            catch (RuntimeException e) { issue(issues, input, AdmissionReasonValues.COLUMN, e.getMessage()); }
        }
        if (input.patient() == null || input.patient().isBlank()) return issues; // Caller reports missing patient.
        String patient = input.patient().toUpperCase(Locale.ROOT);
        State state = patients.get(patient);
        String number = input.get("Fall-Nr");
        boolean newRoot = state == null || (number != null && !number.equals(state.number));
        if (newRoot) {
            state = new State();
            state.number = number;
            patients.put(patient, state);
            if (secondary || number == null)
                issue(issues, input, "Fall-Nr/Kontaktart", "Einrichtungskontakt muss vor seinen Aufenthalten stehen");
        }
        if (start == null || !issues.isEmpty()) return invalidate(state, issues);
        // After a broken row do not guess its hierarchy or produce cascading errors.
        // Independently checkable fields above are still checked on every following row.
        if (state.invalid) return issues;
        Span span = new Span(start, end);
        String classification = input.get("Einrichtungskontaktklasse");
        String classCode = classification == null ? null : EncounterConverter.ENCOUNTER_LEVEL1_CLASS_RESOURCES.get(classification);
        if (newRoot) {
            state.classification = classCode;
            state.facility = span;
            state.bounded = department == null && !places;
        } else {
            if (state.bounded) inside(input, span, state.facility, issues);
            if (classification != null && !Objects.equals(classCode, state.classification))
                issue(issues, input, "Einrichtungskontaktklasse", "Widersprüchliche Einrichtungskontaktklasse im selben Fall");
            if (reason != null)
                issue(issues, input, AdmissionReasonValues.COLUMN, "Aufnahmegrund nur in der ersten Fallzeile angeben");
        }
        if (secondary) {
            if (state.primary == null)
                issue(issues, input, "Kontaktart", "Sekundärkontakt benötigt einen vorhergehenden primären Versorgungsstellenkontakt");
            else {
                inside(input, span, state.primary, issues);
                if (state.departmentPeriod != null) inside(input, span, state.departmentPeriod, issues);
            }
            if (!issues.isEmpty()) return invalidate(state, issues);
            state.secondary.add(span);
            return issues;
        }
        if (!newRoot && state.primary != null && (places || department != null)) {
            if (start.before(state.primary.start()) || (!state.derived && state.primary.end() != null && start.before(state.primary.end())))
                issue(issues, input, "Start/Ende", "Überlappende oder unsortierte primäre Aufenthalte; Zuordnung ist nicht eindeutig");
            if (state.derived || state.primary.end() == null) {
                if (state.secondary.stream().anyMatch(s -> s.start().after(start) || (s.end() != null && s.end().after(start))))
                    issue(issues, input, "Start", "Der neue primäre Aufenthalt würde einen vorherigen Sekundärkontakt abschneiden");
            }
            if (!issues.isEmpty()) return invalidate(state, issues);
            state.secondary.clear();
        }
        if (!newRoot && !state.bounded) state.facility = extend(state.facility, span);
        if (department != null && !department.equals(state.department)) {
            state.department = department;
            state.departmentPeriod = span;
        } else if (state.departmentPeriod != null && places) state.departmentPeriod = extend(state.departmentPeriod, span);
        if (places) {
            state.derived = end == null;
            state.primary = new Span(start, state.derived ? state.facility.end() : end);
            if (state.departmentPeriod != null) state.departmentPeriod = extend(state.departmentPeriod, state.primary);
        } else if (department != null) state.primary = null;
        return issues;
    }

    private static Span extend(Span parent, Span child) {
        Date end = parent.end();
        if (end == null || child.end() == null || child.end().after(end)) end = child.end();
        return new Span(parent.start(), end);
    }
    private static void inside(Input input, Span child, Span parent, List<Issue> issues) {
        if (child.start().before(parent.start()) || (parent.end() != null &&
                (!child.start().before(parent.end()) || (child.end() != null && child.end().after(parent.end())))))
            issue(issues, input, "Start/Ende", "Kontaktzeitraum liegt außerhalb des übergeordneten Aufenthalts");
    }
    private static Date date(Input input, String field, boolean required, List<Issue> issues) {
        String value = input.get(field);
        try {
            var date = ClinicalValues.date(value);
            if ((required && date == null) || (date != null && !date.hasValue()))
                throw new IllegalArgumentException("Konkreter Kontaktzeitpunkt erforderlich");
            return date == null ? null : date.getValue();
        } catch (Exception e) {
            issue(issues, input, field, "Nicht verarbeitbarer Zeitpunkt: " + Objects.toString(value, "leer"));
            return null;
        }
    }
    private static void issue(List<Issue> issues, Input input, String field, String message) {
        Issue issue = new Issue(input.row(), field, message);
        if (issues.stream().noneMatch(i -> i.row() == input.row() && i.field().equals(field) && Objects.equals(i.message(), message))) issues.add(issue);
    }
    private static List<Issue> invalidate(State state, List<Issue> issues) {
        state.invalid = true;
        Issue first = issues.get(0);
        issues.set(0, new Issue(first.row(), first.field(), first.message()
                + ". Abhängige Kontaktzuordnungen dieses Falls werden bis zur Korrektur nicht weiter geprüft; einzelne Eingabefelder weiterhin."));
        return issues;
    }
}
