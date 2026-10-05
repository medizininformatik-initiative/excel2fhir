package de.uni_leipzig.life.csv2fhir;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.BaseDateTimeType;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Resource;

/** Shift clinical values together before assignment and output end policies. */
public final class ClinicalTimeShift {
    private ClinicalTimeShift() {}

    static boolean supports(String id) {
        return Set.of("timeShift.enabled", "timeShift.baseDays", "timeShift.iterationDays").contains(id);
    }

    public static long days(ConverterOptions options) {
        var config = options.configuration();
        if (config == null || !config.stored("timeShift.enabled").asBoolean()) return 0;
        return Math.addExact(config.stored("timeShift.baseDays").asLong(),
                Math.multiplyExact(config.stored("timeShift.iterationDays").asLong(), (long)options.loopCounter));
    }

    public static void apply(List<Resource> resources, long days) {
        if (days == 0) return;
        Set<Base> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<BaseDateTimeType, String> replacements = new IdentityHashMap<>();
        for (Resource resource : resources)
            collect(resource, resource.fhirType() + "/" + resource.getIdElement().getIdPart(), days, visited, replacements);
        // Validate every value before changing the shared input graph.
        replacements.forEach(BaseDateTimeType::setValueAsString);
    }

    private static void collect(Base value, String path, long days, Set<Base> visited,
            Map<BaseDateTimeType, String> replacements) {
        if (!visited.add(value) || value instanceof Meta) return;
        if (value instanceof BaseDateTimeType && ((BaseDateTimeType)value).hasValue()) {
            var date = (BaseDateTimeType)value;
            try {
                String shifted = shift(date.getValueAsString(), days);
                var checked = (BaseDateTimeType)date.copy();
                checked.setValueAsString(shifted);
                replacements.put(date, shifted);
            } catch (IllegalArgumentException | java.time.DateTimeException e) {
                throw new IllegalArgumentException("Cannot shift " + path + ": " + e.getMessage(), e);
            }
        }
        for (var property : value.children())
            for (Base child : property.getValues()) collect(child, path + "." + property.getName(), days, visited, replacements);
    }

    static String shift(String value, long days) {
        if (value.length() < 10) throw new IllegalArgumentException("A nonzero day shift requires a complete date: " + value);
        LocalDate date = LocalDate.parse(value.substring(0, 10)).plusDays(days);
        if (date.getYear() < 1 || date.getYear() > 9999) throw new IllegalArgumentException("Shifted date is outside FHIR's year range");
        if (value.length() > 10) OffsetDateTime.parse(value); // Require an explicit offset for timestamps.
        return date + value.substring(10); // Preserve time, offset and fractional precision exactly.
    }
}
