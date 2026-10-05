package de.uni_leipzig.life.csv2fhir;

import java.nio.file.Files;
import java.nio.file.Path;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Internal workbench adapter: validate submitted Properties with the converter itself. */
public final class ConfigurationPreflight {
    private ConfigurationPreflight() {}

    public static ObjectNode validate(String text) {
        if (!ContractConfiguration.isContractText(text))
            throw new IllegalArgumentException("A versioned converter configuration is required");
        var options = ConverterOptions.fromText(text);
        if (!options.getErrors().isEmpty()) throw new IllegalArgumentException(String.join("\n", options.getErrors()));
        var result = new ObjectMapper().createObjectNode();
        result.set("formats", options.configuration().stored("output.formats"));
        result.put("validation", options.validationEnabled(false));
        result.put("patientsPerFile", options.patientsPerFile(1));
        return result;
    }

    public static void main(String[] args) throws Exception {
        try {
            var validated = validate(Files.readString(Path.of(args[0])));
            Files.writeString(Path.of(args[1]), validated.toString());
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
