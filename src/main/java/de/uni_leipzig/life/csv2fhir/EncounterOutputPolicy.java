package de.uni_leipzig.life.csv2fhir;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Resource;

/** Applies end policies to output copies after internal derivations and matching. */
public final class EncounterOutputPolicy {
    private final ContractConfiguration configuration;
    private final ContactIndex contacts;
    private final List<Map<String, String>> changes = new ArrayList<>();

    public EncounterOutputPolicy(ContractConfiguration configuration, ContactIndex contacts) {
        this.configuration = configuration;
        this.contacts = contacts;
    }

    static String scope(Encounter encounter) {
        String code = encounter.getClass_().getCode();
        if ("AMB".equals(code)) return "ambulatory";
        if ("IMP".equals(code)) return "inpatient";
        return null;
    }

    public Resource output(Resource source) {
        if (configuration == null || !(source instanceof Encounter)) return source;
        Encounter encounter = (Encounter)source;
        String scope = scope(encounter);
        if (scope == null) return source;
        String prefix = "resource.Encounter." + scope;
        String policy = configuration.effective(prefix + ".endPolicy").map(v -> v.asText()).orElse("preserve");
        if (policy.equals("preserve")) return source;
        var entry = contacts.get(encounter).orElseThrow();
        String application = configuration.effective(prefix + ".endApplication").map(v -> v.asText()).orElse("always");
        if (application.equals("missing-input-end") && !entry.inputEndMissing()) return source;
        Encounter output = encounter.copy();
        try {
            output.getPeriod().setEndElement(end(encounter.getPeriod().getStartElement(), policy));
        } catch (IllegalArgumentException | java.time.DateTimeException e) {
            throw new IllegalArgumentException("Encounter " + encounter.getId() + " (" + encounter.getClass_().getCode()
                    + ", " + ContactOutputPolicy.name(entry.level()) + "): " + e.getMessage(), e);
        }
        boolean ended = output.getPeriod().getEndElement().hasValue();
        output.setStatus(ended ? Encounter.EncounterStatus.FINISHED : Encounter.EncounterStatus.INPROGRESS);
        for (var location : output.getLocation())
            location.setStatus(ended ? Encounter.EncounterLocationStatus.COMPLETED : Encounter.EncounterLocationStatus.ACTIVE);
        changes.add(Map.of("encounter", encounter.getId(), "class", encounter.getClass_().getCode(),
                "level", ContactOutputPolicy.name(entry.level()), "policy", policy,
                "inputEndMissing", Boolean.toString(entry.inputEndMissing()),
                "end", output.getPeriod().hasEnd() ? output.getPeriod().getEndElement().getValueAsString() : "open"));
        return output;
    }

    public List<Map<String, String>> changes() { return List.copyOf(changes); }

    static DateTimeType end(DateTimeType start, String policy) {
        if (policy.equals("open")) return null;
        if (!start.hasValue()) throw new IllegalArgumentException("End policy " + policy + " requires a start value");
        if (policy.equals("start")) return start.copy();
        String value = start.getValueAsString();
        if (value.length() < 10)
            throw new IllegalArgumentException("End policy " + policy + " requires a complete start date");
        LocalDate date = LocalDate.parse(value.substring(0, 10));
        boolean dateOnly = value.length() == 10;
        if (policy.equals("start-plus-second")) {
            OffsetDateTime timestamp = dateOnly ? date.atStartOfDay().atOffset(ZoneOffset.UTC) : OffsetDateTime.parse(value);
            int fractionalDigits = 0;
            int dot = value.indexOf('.');
            if (dot >= 0) while (dot + 1 + fractionalDigits < value.length()
                    && Character.isDigit(value.charAt(dot + 1 + fractionalDigits))) fractionalDigits++;
            var formatter = new java.time.format.DateTimeFormatterBuilder()
                    .appendPattern("uuuu-MM-dd'T'HH:mm:ss");
            if (fractionalDigits > 0) formatter.appendFraction(java.time.temporal.ChronoField.NANO_OF_SECOND,
                    fractionalDigits, fractionalDigits, true);
            formatter.appendOffsetId();
            return new DateTimeType(timestamp.plusSeconds(1).format(formatter.toFormatter()));
        }
        if (policy.equals("quarter-end")) date = date.withMonth(((date.getMonthValue() - 1) / 3 + 1) * 3);
        else if (policy.equals("year-end")) date = date.withMonth(12);
        else throw new IllegalArgumentException("Unknown encounter end policy: " + policy);
        date = date.withDayOfMonth(date.lengthOfMonth());
        if (dateOnly) return new DateTimeType(date.toString());
        // Keep the explicit UTC offset and fractional precision; offsets do not encode regional DST rules.
        OffsetDateTime timestamp = OffsetDateTime.parse(value);
        String fraction = "";
        int point = value.indexOf('.');
        if (point >= 0) {
            int digits = 0;
            while (point + 1 + digits < value.length() && Character.isDigit(value.charAt(point + 1 + digits))) digits++;
            fraction = "." + "9".repeat(digits);
        }
        return new DateTimeType(date + "T23:59:59" + fraction + timestamp.getOffset().getId());
    }

    /** Mirror the DAR-adjusted period without changing the pre-DAR clinical status. */
    public Resource finish(Resource source) {
        if (configuration == null || !(source instanceof Encounter)) return source;
        Encounter output = ((Encounter)source).copy();
        for (var location : output.getLocation()) location.setPeriod(output.getPeriod().copy());
        return output;
    }
}
