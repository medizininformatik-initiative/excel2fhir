package de.uni_leipzig.life.csv2fhir;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.r4.model.*;

/** Rebuilds clinical encounter references from original input identity and timestamps. */
public final class ClinicalEncounterAssignment {
    private static final java.util.Set<String> TYPES = java.util.Set.of("Condition", "Procedure",
            "Observation.laboratory", "Observation.vitalSigns", "MedicationRequest", "MedicationAdministration",
            "MedicationStatement", "Immunization", "DiagnosticReport", "CarePlan", "DocumentReference");
    static boolean supports(String option) {
        return option.equals("reference.DocumentReference.assignmentStrategy")
                || TYPES.stream().anyMatch(type -> option.equals("reference." + type + ".encounter"));
    }
    private final ConverterResult input;
    private final ContractConfiguration configuration;
    private final ContactOutputPolicy contacts;
    private final List<Map<String, String>> issues = new ArrayList<>();

    public ClinicalEncounterAssignment(ConverterResult input, ContactOutputPolicy contacts) {
        this.input = input;
        this.configuration = input.getConverterOptions().configuration();
        this.contacts = contacts;
    }

    static String optionResource(Resource resource) {
        if (resource instanceof Observation) {
            boolean vital = ((Observation)resource).getCategory().stream().flatMap(c -> c.getCoding().stream())
                    .anyMatch(c -> "http://terminology.hl7.org/CodeSystem/observation-category".equals(c.getSystem())
                            && "vital-signs".equals(c.getCode()));
            return vital ? "Observation.vitalSigns" : "Observation.laboratory";
        }
        if (resource instanceof Condition || resource instanceof Procedure || resource instanceof MedicationRequest
                || resource instanceof MedicationAdministration || resource instanceof MedicationStatement
                || resource instanceof Immunization || resource instanceof DiagnosticReport
                || resource instanceof CarePlan || resource instanceof DocumentReference) return resource.fhirType();
        return null;
    }

    public Resource output(Resource source) {
        String type = optionResource(source);
        if (configuration == null || type == null) return source;
        Resource output = source.copy();
        setReferences(output, List.of());
        String level = configuration.effective("reference." + type + ".encounter")
                .map(value -> value.asText()).orElse("none");
        if (level.equals("none")) return output;
        var context = input.inputContext(source);
        if (context == null) { issue(source, level, "missing-input-context"); return output; }
        List<DateTimeType> times = timestamps(source, context);
        if (source instanceof DocumentReference) {
            String strategy = configuration.effective("reference.DocumentReference.assignmentStrategy")
                    .map(value -> value.asText()).orElse("explicit-only");
            if (!strategy.equals("timestamp-only") && !context.encounterIds().isEmpty()) {
                List<Reference> references = new ArrayList<>();
                for (String id : context.encounterIds()) {
                    var supplied = input.contacts().get(context.patientId(), id);
                    if (supplied.isEmpty()) { issue(source, level, "unresolved-input-contact:" + id); continue; }
                    if (times.stream().anyMatch(time -> !within(supplied.get().encounter(), time)))
                        issue(source, level, "explicit-contact-time-conflict:" + id);
                    if (!contacts.emits(supplied.get().encounter())) {
                        issue(source, level, "input-contact-not-output:" + id); continue;
                    }
                    references.add(reference(supplied.get()));
                }
                setReferences(output, references);
                return output;
            }
            if (strategy.equals("explicit-only")) return output;
        }
        if (times.isEmpty()) { issue(source, level, "missing-timestamp"); return output; }
        var target = input.contacts().match(context.patientId(), ContactOutputPolicy.level(level), times)
                .filter(entry -> contacts.emits(entry.encounter()));
        if (target.isPresent()) setReferences(output, List.of(reference(target.get())));
        else issue(source, level, "no-matching-contact");
        return output;
    }

    public List<Map<String, String>> issues() { return List.copyOf(issues); }
    private void issue(Resource source, String level, String reason) {
        var issue = new java.util.LinkedHashMap<>(Map.of("resourceType", source.fhirType(), "resourceId", source.getId(),
                "targetLevel", level, "reason", reason));
        var context = input.inputContext(source);
        if (context != null) issue.put("inputRow", Long.toString(context.inputRow()));
        issues.add(issue);
    }
    private static Reference reference(ContactIndex.Entry entry) {
        return new Reference("Encounter/" + entry.encounter().getIdElement().getIdPart());
    }
    private static boolean within(Encounter contact, DateTimeType time) {
        return contact.hasPeriod() && contact.getPeriod().hasStart()
                && !time.getValue().before(contact.getPeriod().getStart())
                && (!contact.getPeriod().hasEnd() || !time.getValue().after(contact.getPeriod().getEnd()));
    }
    private static void add(List<DateTimeType> values, DateTimeType value) {
        if (value != null && value.hasValue()) values.add(value);
    }
    private static void effective(List<DateTimeType> values, Type value) {
        if (value instanceof Period) add(values, ((Period)value).getStartElement());
        else if (value instanceof DateTimeType) add(values, (DateTimeType)value);
    }
    private static List<DateTimeType> timestamps(Resource source, ConverterResult.InputContext context) {
        List<DateTimeType> values = new ArrayList<>();
        if (source instanceof Condition && ((Condition)source).hasRecordedDate()) add(values, ((Condition)source).getRecordedDateElement());
        else if (source instanceof Procedure) effective(values, ((Procedure)source).getPerformed());
        else if (source instanceof Observation && ((Observation)source).getEffective() instanceof DateTimeType)
            add(values, (DateTimeType)((Observation)source).getEffective());
        else if (source instanceof MedicationRequest && ((MedicationRequest)source).hasAuthoredOn()) add(values, ((MedicationRequest)source).getAuthoredOnElement());
        else if (source instanceof MedicationAdministration) effective(values, ((MedicationAdministration)source).getEffective());
        else if (source instanceof MedicationStatement) effective(values, ((MedicationStatement)source).getEffective());
        else if (source instanceof Immunization) effective(values, ((Immunization)source).getOccurrence());
        else if (source instanceof CarePlan && ((CarePlan)source).hasPeriod()) add(values, ((CarePlan)source).getPeriod().getStartElement());
        else if (source instanceof DiagnosticReport) {
            var report = (DiagnosticReport)source;
            if (report.getEffective() instanceof DateTimeType) add(values, (DateTimeType)report.getEffective());
            if (report.hasIssued()) add(values, new DateTimeType(report.getIssuedElement().getValueAsString()));
        } else if (source instanceof DocumentReference && context.explicitDocumentTimestamp()) {
            var document = (DocumentReference)source;
            if (document.hasDate()) add(values, new DateTimeType(document.getDateElement().getValueAsString()));
        }
        return values;
    }
    private static void setReferences(Resource resource, List<Reference> references) {
        Reference single = references.isEmpty() ? null : references.get(0);
        if (resource instanceof Condition) ((Condition)resource).setEncounter(single);
        else if (resource instanceof Procedure) ((Procedure)resource).setEncounter(single);
        else if (resource instanceof Observation) ((Observation)resource).setEncounter(single);
        else if (resource instanceof MedicationRequest) ((MedicationRequest)resource).setEncounter(single);
        else if (resource instanceof MedicationAdministration) ((MedicationAdministration)resource).setContext(single);
        else if (resource instanceof MedicationStatement) ((MedicationStatement)resource).setContext(single);
        else if (resource instanceof Immunization) ((Immunization)resource).setEncounter(single);
        else if (resource instanceof DiagnosticReport) ((DiagnosticReport)resource).setEncounter(single);
        else if (resource instanceof CarePlan) ((CarePlan)resource).setEncounter(single);
        else if (resource instanceof DocumentReference) {
            var document = (DocumentReference)resource;
            if (!references.isEmpty() || document.hasContext()) document.getContext().setEncounter(references);
            if (document.hasContext() && document.getContext().isEmpty()) document.setContext(null);
        }
    }
}
