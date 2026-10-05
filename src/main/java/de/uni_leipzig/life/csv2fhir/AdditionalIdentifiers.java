package de.uni_leipzig.life.csv2fhir;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.hl7.fhir.r4.model.*;

/** One instance per option set, shared by patients, repetitions and output formats. */
public final class AdditionalIdentifiers {
    private final List<Map<String, String>> rules;
    private final Map<String, Long> counters = new HashMap<>();
    private final Map<String, List<Long>> allocated = new HashMap<>();
    private final Map<String, String> patientIds = new HashMap<>();
    private final Map<String, Set<String>> owners = new HashMap<>();
    private final Map<String, String> ownerRules = new HashMap<>();
    public AdditionalIdentifiers(ContractConfiguration configuration) {
        rules = configuration == null ? List.of() : configuration.identifierRules().stream()
                .filter(rule -> rule.get("ENABLED").equals("true")).collect(java.util.stream.Collectors.toList());
    }
    boolean enabled() { return !rules.isEmpty(); }
    private static String identity(Resource resource, int iteration) {
        if (!resource.hasIdElement() || resource.getIdElement().getIdPart() == null)
            throw new IllegalArgumentException("Identifier generation requires a resource ID: " + resource.fhirType());
        return resource.fhirType() + "/" + resource.getIdElement().getIdPart() + " (iteration " + iteration + ")";
    }
    /** Reserve counts before filtering; original and potentially derived resources occur only once. */
    public void reserve(List<Resource> potential, ConverterResult context, int iteration) {
        if (rules.isEmpty()) return;
        for (Resource resource : potential) {
            String identity = identity(resource, iteration);
            if (allocated.containsKey(identity)) continue;
            List<Long> counts = new ArrayList<>();
            for (Map<String, String> rule : rules) {
                if (!rule.get("ENABLED").equals("true") || !selects(rule, resource)) continue;
                long count = Math.addExact(counters.getOrDefault(rule.get("ID"), 0L), 1);
                counters.put(rule.get("ID"), count);
                counts.add(count);
            }
            ConverterResult.InputContext input = context.inputContext(resource);
            patientIds.put(identity, input == null ? "" : input.patientId());
            allocated.put(identity, counts);
        }
    }
    public Resource output(Resource resource, int iteration) {
        if (rules.isEmpty()) return resource;
        String identity = identity(resource, iteration);
        List<Long> generated = allocated.get(identity);
        if (generated == null) throw new IllegalStateException("Identifier counts not reserved: " + identity);
        Resource output = resource.copy();
        List<Identifier> existing = new ArrayList<>();
        for (Property property : output.children()) if (property.getName().equals("identifier"))
            for (Base value : property.getValues()) existing.add((Identifier)value);
        for (Identifier identifier : existing) register(identifier, identity, "existing identifier");
        int index = 0;
        for (Map<String, String> rule : rules) {
            if (!rule.get("ENABLED").equals("true") || !selects(rule, resource)) continue;
            Identifier identifier = new Identifier().setSystem(rule.get("SYSTEM")).setValue(expand(rule.get("PATTERN"),
                    generated.get(index++), patientIds.get(identity), resource, iteration, rule.get("ID")));
            register(identifier, identity, rule.get("ID"));
            if (existing.stream().noneMatch(e -> Objects.equals(e.getSystem(), identifier.getSystem()) && Objects.equals(e.getValue(), identifier.getValue()))) {
                output.setProperty("identifier", identifier.copy());
                existing.add(identifier);
            }
        }
        return output;
    }
    private static boolean selects(Map<String, String> rule, Resource resource) {
        var selectors = Arrays.asList(rule.get("RESOURCES").split(","));
        if (selectors.contains(resource.fhirType())) return true;
        return resource instanceof Encounter && EncounterOutputPolicy.scope((Encounter)resource) != null
                && selectors.contains("Encounter." + EncounterOutputPolicy.scope((Encounter)resource));
    }
    private void register(Identifier identifier, String identity, String rule) {
        if (!identifier.hasSystem() || !identifier.hasValue()) return;
        String key = identifier.getSystem().length() + ":" + identifier.getSystem() + identifier.getValue();
        Set<String> resourceOwners = owners.computeIfAbsent(key, unused -> new LinkedHashSet<>());
        resourceOwners.add(identity);
        String previousRule = ownerRules.getOrDefault(key, "existing identifier");
        if (!rule.equals("existing identifier")) ownerRules.put(key, rule);
        if (resourceOwners.size() > 1 && (!rule.equals("existing identifier") || !previousRule.equals("existing identifier")))
            throw new IllegalArgumentException("Identifier collision for rule " + rule + " / " + previousRule
                    + ": " + identifier.getSystem() + " | " + identifier.getValue() + " on " + resourceOwners);

    }
    static String expand(String pattern, long count, String patientId, Resource resource, int iteration, String ruleId) {
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < pattern.length();) {
            if (pattern.startsWith("{{", i) || pattern.startsWith("}}", i)) { value.append(pattern.charAt(i)); i += 2; continue; }
            char c = pattern.charAt(i++);
            if (c != '{') { value.append(c); continue; }
            int end = pattern.indexOf('}', i);
            String token = pattern.substring(i, end); i = end + 1;
            if (token.equals("count") || token.startsWith("count:")) {
                String number = Long.toString(count);
                long width = token.equals("count") ? 0 : Long.parseLong(token.substring(7));
                if (width > 1_000_000) throw new IllegalArgumentException("Identifier padding exceeds runtime limit of 1000000 characters for rule " + ruleId);
                value.append("0".repeat((int)Math.max(0, width - number.length()))).append(number);
            } else if (token.equals("patientId")) {
                if (patientId.isEmpty()) throw new IllegalArgumentException("Identifier rule " + ruleId + " requires patient context");
                value.append(patientId);
            }
            else if (token.equals("resourceId")) value.append(resource.getIdElement().getIdPart());
            else if (token.equals("resourceType")) value.append(resource.fhirType());
            else if (token.equals("iteration")) value.append(iteration);
            else if (token.equals("hash")) value.append(hash(ruleId, resource, iteration));
            else throw new IllegalArgumentException("Unknown identifier token: " + token);
        }
        return value.toString();
    }
    static String hash(String ruleId, Resource resource, int iteration) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : List.of(ruleId.toLowerCase(Locale.ROOT), resource.fhirType(), resource.getIdElement().getIdPart(), Integer.toString(iteration))) {
                byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
                digest.update((bytes.length + ":").getBytes(StandardCharsets.UTF_8)); digest.update(bytes);
            }
            byte[] bytes = digest.digest(); StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 16; i++) hex.append(String.format(Locale.ROOT, "%02x", bytes[i] & 0xff));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
