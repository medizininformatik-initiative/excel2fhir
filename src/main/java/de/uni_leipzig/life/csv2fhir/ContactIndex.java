package de.uni_leipzig.life.csv2fhir;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Encounter;

import de.uni_leipzig.life.csv2fhir.converter.ResourceIdSuffix;

/** Input contact identity and hierarchy, independent of selected output and partOf. */
public final class ContactIndex {
    public enum Level { FACILITY, DEPARTMENT, WARD_SERVICE }

    public static final class Entry {
        private final Encounter encounter;
        private final String patientId, facilityId, parentId;
        private final Level level;
        private final long inputRow;
        private final boolean secondary;

        private Entry(Encounter encounter, String patientId, String facilityId, Level level,
                String parentId, long inputRow, boolean secondary) {
            this.encounter = encounter;
            this.patientId = patientId;
            this.facilityId = facilityId;
            this.level = level;
            this.parentId = parentId;
            this.inputRow = inputRow;
            this.secondary = secondary;
        }
        public Encounter encounter() { return encounter; }
        public String patientId() { return patientId; }
        public String facilityId() { return facilityId; }
        public String parentId() { return parentId; }
        public Level level() { return level; }
        public long inputRow() { return inputRow; }
        public boolean secondary() { return secondary; }
    }

    private final Map<String, Map<String, Entry>> byPatient = new LinkedHashMap<>();

    public void add(Encounter encounter, String patientId, String facilityId, Level level,
            String parentId, long inputRow, boolean secondary) {
        byPatient.computeIfAbsent(patientId, key -> new LinkedHashMap<>()).put(encounter.getId(),
                new Entry(encounter, patientId, facilityId, level, parentId, inputRow, secondary));
    }

    public List<Entry> entries(String patientId) {
        return List.copyOf(byPatient.getOrDefault(patientId, Map.of()).values());
    }

    public Optional<Entry> get(String patientId, String encounterId) {
        return Optional.ofNullable(byPatient.getOrDefault(patientId, Map.of()).get(encounterId));
    }

    /** Resource IDs retain the converted patient identity, including prefixes and offsets. */
    public Optional<Entry> get(Encounter encounter) {
        return byPatient.values().stream().map(entries -> entries.get(encounter.getId()))
                .filter(java.util.Objects::nonNull).findFirst();
    }

    /** Case numbers are scoped to the patient, including when another patient has that number. */
    public Optional<Entry> resolveInput(String patientId, String caseNumber) {
        if (patientId == null || caseNumber == null || caseNumber.isBlank()) return Optional.empty();
        return get(patientId, patientId + ResourceIdSuffix.ENCOUNTER_LEVEL_1 + caseNumber)
                .filter(entry -> entry.level == Level.FACILITY);
    }

    /** Original parentage is retained even when output removes or redirects partOf. */
    public Optional<Entry> ancestor(String patientId, String encounterId, Level level) {
        Optional<Entry> current = get(patientId, encounterId);
        var visited = new java.util.HashSet<String>();
        while (current.isPresent() && visited.add(current.get().encounter.getId())) {
            Entry entry = current.get();
            if (entry.level == level) return current;
            if (entry.parentId == null) break;
            current = get(patientId, entry.parentId);
        }
        return Optional.empty();
    }

    /** Candidates are tried in order; no match at one level ever selects another level. */
    public Optional<Entry> match(String patientId, Level level, List<DateTimeType> candidates) {
        return match(patientId, level, candidates, entry -> true);
    }

    /** Only descendants of the original source contact are eligible. */
    public Optional<Entry> matchDescendant(Entry source, Level level, DateTimeType timestamp) {
        return match(source.patientId, level, java.util.Collections.singletonList(timestamp), entry ->
                java.util.Objects.equals(source.facilityId, entry.facilityId)
                && ancestor(entry.patientId, entry.encounter.getId(), source.level)
                        .map(parent -> parent.encounter.getId().equals(source.encounter.getId())).orElse(false));
    }

    private Optional<Entry> match(String patientId, Level level, List<DateTimeType> candidates,
            java.util.function.Predicate<Entry> eligible) {
        for (DateTimeType candidate : candidates) {
            if (candidate == null || !candidate.hasValue()) continue;
            Entry best = null;
            for (Entry entry : byPatient.getOrDefault(patientId, Map.of()).values()) {
                if (entry.level != level || (level == Level.WARD_SERVICE && entry.secondary)) continue;
                if (!eligible.test(entry)) continue;
                var period = entry.encounter.getPeriod();
                if (!period.hasStart() || !period.getStartElement().hasValue()) continue;
                var time = candidate.getValue();
                if (time.before(period.getStart()) || (period.hasEnd() && time.after(period.getEnd()))) continue;
                if (best == null || period.getStart().after(best.encounter.getPeriod().getStart())
                        || (period.getStart().equals(best.encounter.getPeriod().getStart()) && entry.inputRow < best.inputRow)) best = entry;
            }
            if (best != null) return Optional.of(best);
        }
        return Optional.empty();
    }
}
