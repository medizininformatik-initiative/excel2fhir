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
