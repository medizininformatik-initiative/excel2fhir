import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.uni_leipzig.life.csv2fhir.ConverterOptions;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** Check coverage against real Java enums; do not reimplement their defaults. */
class CheckJavaBindings {
    public static void main(String[] args) throws Exception {
        JsonNode contract = new ObjectMapper().readTree(
                Path.of("web/catalog/options/contract.json").toFile());
        Set<String> bindings = new HashSet<>();
        ConverterOptions defaults = new ConverterOptions("");
        for (JsonNode option : contract.get("options")) {
            JsonNode binding = option.path("binding");
            if (!binding.path("kind").asText().equals("java-property")) continue;
            String name = binding.get("name").asText();
            if (!bindings.add(name)) throw new AssertionError("Duplicate binding: " + name);
            Object actual = null;
            for (var key : ConverterOptions.BooleanOption.values())
                if (key.name().equals(name)) actual = defaults.is(key);
            for (var key : ConverterOptions.IntOption.values())
                if (key.name().equals(name)) actual = defaults.getValue(key);
            for (var key : ConverterOptions.StringOption.values())
                if (key.name().equals(name)) actual = defaults.getValue(key);
            if (actual == null) throw new AssertionError("Unknown Java property: " + name);
            // Translated controls have explicit mappings; their future defaults may
            // intentionally differ from the current runtime until #77 implements them.
            if (!binding.has("values")) {
                JsonNode expected = new ObjectMapper().valueToTree(actual);
                if (!expected.equals(option.get("default")))
                    throw new AssertionError("Default drift: " + name);
            }
        }
        Set<String> expected = new HashSet<>();
        for (var key : ConverterOptions.BooleanOption.values()) expected.add(key.name());
        for (var key : ConverterOptions.IntOption.values()) expected.add(key.name());
        for (var key : ConverterOptions.StringOption.values()) expected.add(key.name());
        Set<String> legacyOnly = new HashSet<>();
        for (JsonNode entry : contract.path("legacyOnlyJavaProperties")) {
            String name = entry.path("name").asText();
            if (!expected.contains(name) || bindings.contains(name) || !legacyOnly.add(name)
                    || entry.path("reason").asText().isBlank())
                throw new AssertionError("Invalid legacy-only Java option: " + name);
        }
        expected.removeAll(legacyOnly);
        if (!expected.equals(bindings)) {
            expected.removeAll(bindings);
            throw new AssertionError("Missing Java options: " + expected);
        }
        System.out.println("All " + bindings.size() + " versioned Java options covered; " + legacyOnly.size() + " legacy-only options documented.");
    }
}
