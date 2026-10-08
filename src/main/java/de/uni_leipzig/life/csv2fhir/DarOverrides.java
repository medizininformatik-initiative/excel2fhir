package de.uni_leipzig.life.csv2fhir;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import org.hl7.fhir.r4.model.*;

/** Applies catalogue replacements to output copies, after clinical derivations. */
public final class DarOverrides {
    public static final String URL = "http://hl7.org/fhir/StructureDefinition/data-absent-reason";
    private final java.util.Map<String, String> overrides;
    private final java.util.Set<String> missingOnly;
    private final List<JsonNode> fields = new ArrayList<>();
    public DarOverrides(ContractConfiguration configuration) {
        if (configuration == null) {
            missingOnly = java.util.Set.of();
            overrides = java.util.Map.of();
            return;
        }
        missingOnly = configuration.darMissingOnly();
        overrides = configuration.darOverrides();
        if (!overrides.isEmpty()) for (JsonNode field : configuration.darFields())
            if (overrides.containsKey(field.path("id").asText()) || overrides.keySet().stream()
                    .anyMatch(id -> id.replace("Encounter.ambulatory.", "Encounter.")
                            .replace("Encounter.inpatient.", "Encounter.").equals(field.path("id").asText()))) fields.add(field);
    }

    public Resource output(Resource source) {
        if (overrides.isEmpty()) return source;
        Resource output = source.copy();
        for (JsonNode field : fields) {
            String id = field.path("id").asText();
            String ruleId = id;
            String code = overrides.get(id);
            if (source instanceof Encounter && EncounterOutputPolicy.scope((Encounter)source) != null)
                code = overrides.getOrDefault(id.replace("Encounter.", "Encounter."
                        + EncounterOutputPolicy.scope((Encounter)source) + "."), code);
            if (source instanceof Encounter && EncounterOutputPolicy.scope((Encounter)source) != null) {
                String scoped = id.replace("Encounter.", "Encounter." + EncounterOutputPolicy.scope((Encounter)source) + ".");
                if (overrides.containsKey(scoped)) ruleId = scoped;
            }
            boolean onlyMissing = missingOnly.contains(ruleId);
            if (code == null || !field.path("resourceType").asText().equals(source.fhirType())) continue;
            if (source instanceof Observation) {
                boolean vital = ((Observation)source).getCategory().stream().flatMap(c -> c.getCoding().stream())
                        .anyMatch(c -> "http://terminology.hl7.org/CodeSystem/observation-category".equals(c.getSystem())
                                && "vital-signs".equals(c.getCode()));
                if (id.startsWith("VitalSigns.") != vital) continue;
            }
            String path = field.path("field").asText();
            if (field.path("representation").asText().equals("dataAbsentReason")) {
                observation((Observation)source, (Observation)output, field, code, onlyMissing);
                continue;
            }
            List<Base> targets;
            if (id.equals("Encounter.admissionReasonFourthDigit")) {
                targets = new ArrayList<>();
                for (Extension e : ((Encounter)output).getExtensionsByUrl("http://fhir.de/StructureDefinition/Aufnahmegrund"))
                    for (Extension digit : e.getExtensionsByUrl("VierteStelle"))
                        if (digit.getValue() instanceof Coding) targets.add(((Coding)digit.getValue()).getCodeElement());
            } else {
                targets = targets(output, path.split("\\."), 0, field);
            }
            if (targets.isEmpty()) continue;
            for (Base target : targets) {
                if (onlyMissing && hasContent(target)) continue;
                if (field.has("supportedChoices")) {
                    boolean supported = false;
                    for (JsonNode choice : field.get("supportedChoices")) supported |= choice.asText().equals(target.fhirType());
                    if (!supported) throw new IllegalArgumentException("Unsupported DAR choice type: " + id + " / " + target.fhirType());
                }
                replace((Element)target, code);
            }
            if (id.equals("DocumentReference.content.attachment.data")) {
                for (DocumentReference.DocumentReferenceContentComponent content : ((DocumentReference)output).getContent()) {
                    if (content.getAttachment().hasDataElement() && !content.getAttachment().getDataElement().hasValue()) {
                        content.getAttachment().setSizeElement(null).setHashElement(null);
                        if (content.getAttachment().hasUrl() && content.getAttachment().getUrl().regionMatches(true, 0, "data:", 0, 5))
                            content.getAttachment().setUrlElement(null);
                    }
                }
            }
        }
        return output;
    }

    /** A value or an existing DAR is retained by missing-only rules. */
    private static boolean hasContent(Base value) {
        if (value instanceof Element && ((Element)value).hasExtension(URL)) return true;
        if (value instanceof PrimitiveType<?>) return ((PrimitiveType<?>)value).hasValue();
        for (Property property : value.children()) {
            if (property.getName().equals("extension") || property.getName().equals("id")) continue;
            for (Base child : property.getValues()) if (hasContent(child)) return true;
        }
        return false;
    }

    private static List<Base> targets(Base parent, String[] parts, int index, JsonNode field) {
        String name = parts[index];
        List<Base> values = new ArrayList<>();
        Property property = null;
        for (Property p : parent.children()) if (p.getName().equals(name)) { property = p; values.addAll(p.getValues()); break; }
        boolean scalar = field.path("applyTo").asText().equals("existing-or-missing-scalar");
        boolean leaf = index == parts.length - 1;
        // Do not invent repeated names, codings, ingredients, components or dosage instructions.
        if (values.isEmpty() && property != null && property.getMaxCardinality() == 1 && (scalar || leaf)) {
            String concrete = name;
            if (name.endsWith("[x]")) {
                JsonNode choices = field.path("supportedChoices");
                if (!choices.isArray() || choices.isEmpty()) return values;
                // All scalar converter choices in this catalogue use dateTime when no value exists.
                if (!choices.get(0).asText().equals("dateTime"))
                    throw new IllegalArgumentException("Unknown missing DAR choice type: " + field.path("id").asText());
                Base value = new DateTimeType();
                parent.setProperty(name, value);
                values.add(value);
                return values;
            }
            values.add(parent.makeProperty(concrete.hashCode(), concrete));
        }
        if (leaf) return values;
        List<Base> leaves = new ArrayList<>();
        for (Base value : values) leaves.addAll(targets(value, parts, index + 1, field));
        return leaves;
    }

    private static void replace(Element element, String code) {
        if (element instanceof PrimitiveType<?>) ((PrimitiveType<?>)element).setValueAsString(null);
        else for (Property property : element.children()) {
            if (property.getName().equals("extension") || property.getName().equals("id")) continue;
            for (Base value : new ArrayList<>(property.getValues())) element.removeChild(property.getName(), value);
        }
        element.getExtension().removeIf(e -> URL.equals(e.getUrl()));
        element.addExtension(URL, new CodeType(code));
    }

    private static void observation(Observation source, Observation output, JsonNode field, String code, boolean onlyMissing) {
        boolean numeric = field.path("semanticGroup").asText().equals("numeric-measurement");
        if (field.path("field").asText().startsWith("component.")) {
            for (int i = 0; i < source.getComponent().size(); i++) {
                Type value = source.getComponent().get(i).getValue();
                if (onlyMissing) {
                    if (value != null && hasContent(value)) continue;
                    if (output.getComponent().get(i).hasDataAbsentReason()) continue;
                } else if (value == null ? !source.getComponent().get(i).hasDataAbsentReason() : (value instanceof Quantity) != numeric) continue;
                output.getComponent().get(i).setValue(null).setDataAbsentReason(reason(code));
            }
        } else if (onlyMissing ? (!source.hasValue() || !hasContent(source.getValue()))
                && !output.hasDataAbsentReason()
                : source.hasValue() ? (source.getValue() instanceof Quantity) == numeric : source.hasDataAbsentReason()) {
            output.setValue(null).setDataAbsentReason(reason(code));
        }
    }
    private static CodeableConcept reason(String code) {
        return new CodeableConcept().addCoding(new Coding("http://terminology.hl7.org/CodeSystem/data-absent-reason", code, null));
    }
}
