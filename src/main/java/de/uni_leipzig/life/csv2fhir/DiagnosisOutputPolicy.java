package de.uni_leipzig.life.csv2fhir;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Encounter.DiagnosisComponent;
import org.hl7.fhir.r4.model.Resource;

/** Assigns diagnosis references using original contacts before projecting output. */
public final class DiagnosisOutputPolicy {
    private static final String ROLE_SYSTEM = "http://terminology.hl7.org/CodeSystem/diagnosis-role";
    private final ContractConfiguration configuration;
    private final Map<String, Map<String, DiagnosisComponent>> assigned = new LinkedHashMap<>();
    private final List<Map<String, String>> issues = new ArrayList<>();

    public DiagnosisOutputPolicy(ContractConfiguration configuration, ContactIndex contacts,
            List<Resource> resources) {
        this.configuration = configuration;
        if (configuration == null || !configuration.stored("resource.Condition.enabled").asBoolean()
                || !configuration.effective("contact.diagnoses.enabled").map(v -> v.asBoolean()).orElse(false)) return;
        Set<String> roles = selected(configuration, "contact.diagnoses.roles");
        Set<String> levels = selected(configuration, "contact.diagnoses.levels");
        Map<String, Condition> conditions = new LinkedHashMap<>();
        for (Resource resource : resources)
            if (resource instanceof Condition) conditions.put(resource.getIdElement().getIdPart(), (Condition)resource);
        Set<String> processed = new HashSet<>();
        for (Resource resource : resources) {
            if (!(resource instanceof Encounter) || !processed.add(resource.getId())) continue;
            Encounter encounter = (Encounter)resource;
            for (DiagnosisComponent diagnosis : encounter.getDiagnosis()) {
                if (!isCondition(diagnosis) || !diagnosis.hasUse() || diagnosis.getUse().getCoding().stream()
                        .noneMatch(c -> ROLE_SYSTEM.equals(c.getSystem()) && roles.contains(c.getCode()))) continue;
                String id = diagnosis.getCondition().getReferenceElement().getIdPart();
                Condition condition = conditions.get(id);
                for (ContactIndex.Level level : ContactIndex.Level.values()) {
                    String name = levelName(level);
                    if (!levels.contains(name) || !configuration.effective("contact." + name + ".enabled")
                            .map(v -> v.asBoolean()).orElse(false)) continue;
                    if (condition == null) { issue(id, encounter, name, "missing-condition"); continue; }
                    var source = contacts.get(encounter);
                    if (source.isEmpty()) { issue(id, encounter, name, "missing-source-contact"); continue; }
                    Optional<ContactIndex.Entry> target;
                    if (level.ordinal() <= source.get().level().ordinal()) {
                        target = contacts.ancestor(source.get().patientId(), encounter.getId(), level);
                    } else {
                        if (!condition.hasRecordedDate()) {
                            issue(id, encounter, name, "missing-documentation-time");
                            continue;
                        }
                        target = contacts.matchDescendant(source.get(), level, condition.getRecordedDateElement());
                    }
                    if (target.isEmpty()) { issue(id, encounter, name, "no-matching-contact"); continue; }
                    Map<String, DiagnosisComponent> references = assigned.computeIfAbsent(
                            target.get().encounter().getId(), key -> new LinkedHashMap<>());
                    // A target's own role takes precedence over an automatically transferred role.
                    if (target.get().encounter().getId().equals(encounter.getId())) references.put(id, diagnosis);
                    else references.putIfAbsent(id, diagnosis);
                }
            }
        }
    }

    public Resource output(Resource source) {
        if (configuration == null) return source;
        if (source instanceof Condition)
            return configuration.stored("resource.Condition.enabled").asBoolean() ? source : null;
        if (!(source instanceof Encounter)) return source;
        Encounter output = ((Encounter)source).copy();
        // Procedure references are configured separately.
        output.getDiagnosis().removeIf(DiagnosisOutputPolicy::isCondition);
        assigned.getOrDefault(source.getId(), Map.of()).values().forEach(d -> output.addDiagnosis(d.copy()));
        return output;
    }

    public List<Map<String, String>> issues() { return List.copyOf(issues); }

    private void issue(String condition, Encounter source, String level, String reason) {
        issues.add(Map.of("conditionId", condition == null ? "" : condition, "sourceContact", source.getId(),
                "targetLevel", level, "reason", reason));
    }

    static boolean isCondition(DiagnosisComponent diagnosis) {
        var reference = diagnosis.getCondition();
        return "Condition".equals(reference.getType())
                || "Condition".equals(reference.getReferenceElement().getResourceType())
                || reference.getResource() instanceof Condition;
    }

    private static Set<String> selected(ContractConfiguration configuration, String id) {
        Set<String> values = new HashSet<>();
        configuration.effective(id).ifPresent(value -> value.forEach(item -> values.add(item.asText())));
        return values;
    }
    private static String levelName(ContactIndex.Level level) {
        switch (level) {
        case FACILITY: return "facility";
        case DEPARTMENT: return "department";
        default: return "ward-service";
        }
    }
}
