import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.gson.*;
import de.uni_leipzig.life.csv2fhir.ConverterOptions;
import de.uni_leipzig.life.csv2fhir.ConverterOptionSet;

/** Use the converter's own parser and ID rules in the Python workflow. */
public class WorkflowOptions {
    public static void main(String[] args) throws Exception {
        JsonObject input = JsonParser.parseString(new String(System.in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String, String> defaults = new LinkedHashMap<>();
        input.getAsJsonObject("defaults").entrySet().forEach(e -> defaults.put(e.getKey(), e.getValue().getAsString()));
        ConverterOptions options = ConverterOptions.fromText(input.get("text").getAsString(), defaults);
        List<String> errors = new ArrayList<>(options.getErrors());
        Map<String, String> values = new LinkedHashMap<>();
        for (var key : ConverterOptions.BooleanOption.values()) values.put(key.name(), Boolean.toString(options.is(key)));
        for (var key : ConverterOptions.IntOption.values()) values.put(key.name(), Integer.toString(options.getValue(key)));
        for (var key : ConverterOptions.StringOption.values()) values.put(key.name(), options.getValue(key));
        if (options.configuration() != null) {
            // Use the packaged contract so the checker receives the same effective
            // transformation settings as the converter, including dependencies.
            try (var stream = WorkflowOptions.class.getResourceAsStream("/configuration/options/contract.json")) {
                JsonObject contract = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                for (var entry : contract.getAsJsonArray("options")) {
                    JsonObject definition = entry.getAsJsonObject();
                    String id = definition.get("id").getAsString();
                    if (!id.startsWith("resource.") && !id.startsWith("contact.") && !id.startsWith("reference.")
                            && !id.startsWith("timeShift.") && !id.startsWith("medication.")) continue;
                    String fallback = definition.get("type").getAsString().equals("boolean") ? "false"
                            : id.startsWith("medication.") ? "retain" : id.startsWith("timeShift.") ? "0"
                            : id.endsWith(".endPolicy") ? "preserve" : id.endsWith(".endApplication") ? "always" : "none";
                    String value = options.configuration().effective(id).map(node -> {
                        if (!node.isArray()) return node.asText();
                        List<String> members = new ArrayList<>();
                        node.forEach(member -> members.add(member.asText()));
                        return String.join(",", members);
                    }).orElse(fallback);
                    values.put(definition.get("propertyName").getAsString(), value);
                }
                var overrides = options.configuration().darOverrides();
                for (var entry : contract.getAsJsonObject("propertiesFormat").getAsJsonObject("darProperties").entrySet()) {
                    if (overrides.containsKey(entry.getKey())) values.put(entry.getValue().getAsString(), overrides.get(entry.getKey()));
                }
            }
        }
        Map<String, List<String>> patients = new LinkedHashMap<>();
        if (errors.isEmpty() && input.has("patients")) {
            int count = options.getValue(ConverterOptions.IntOption.PID_LAST_NUMBER_INCREASE_LOOP_COUNT);
            try {
                Math.multiplyExact(input.getAsJsonArray("patients").size(), Math.addExact(count, 1));
                Set<String> ids = new HashSet<>();
                for (var source : input.getAsJsonArray("patients")) {
                    List<String> copies = new ArrayList<>();
                    for (int iteration = 0; iteration <= count; iteration++) {
                        try {
                            String id = options.getFullPID(source.getAsString(), iteration);
                            if (!ids.add(id)) errors.add("Duplicate generated patient ID: " + id);
                            copies.add(id);
                        } catch (IllegalArgumentException | ArithmeticException e) {
                            errors.add(source.getAsString() + ": " + e.getMessage());
                        }
                    }
                    patients.put(source.getAsString(), copies);
                }
            } catch (ArithmeticException e) {
                errors.add("Patient count including repetitions exceeds the supported numeric range");
            }
        }
        String name = input.has("name") ? new ConverterOptionSet(input.get("name").getAsString(), "").directoryName()
                : "Konvertierungsoptionen";
        System.out.println(new Gson().toJson(Map.of("values", values, "patients", patients, "errors", errors, "name", name)));
    }
}
