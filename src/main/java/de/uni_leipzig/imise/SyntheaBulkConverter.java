package de.uni_leipzig.imise;

import java.nio.file.Files;
import java.nio.file.Path;
import com.google.gson.JsonParser;
import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import de.uni_leipzig.life.csv2fhir.AdditionalIdentifiers;
import de.uni_leipzig.life.csv2fhir.ConverterOptions;
import de.uni_leipzig.life.csv2fhir.Csv2Fhir;

/** Internal gzip JSON writer for freshly generated tables, never for uploaded input. */
public final class SyntheaBulkConverter {
    public static void main(String[] args) throws Exception {
        org.apache.logging.log4j.core.config.Configurator.setRootLevel(org.apache.logging.log4j.Level.ERROR);
        String configuration = Files.readString(Path.of(args[1]));
        try (var reader = Files.newBufferedReader(Path.of(args[0]))) {
            int completed = 0;
            var jobs = JsonParser.parseReader(reader).getAsJsonArray();
            Map<String, Long> counters = new HashMap<>();
            var first = jobs.get(0).getAsJsonObject();
            if (first.has("identifierCounters"))
                first.getAsJsonObject("identifierCounters").entrySet()
                        .forEach(entry -> counters.put(entry.getKey(), entry.getValue().getAsLong()));
            for (var element : jobs) {
                var job = element.getAsJsonObject();
                var destination = Path.of(job.get("output").getAsString());
                Files.createDirectories(destination);
                var options = ConverterOptions.fromConfigurationText(configuration).withCommandLineExecution();
                if (!options.getErrors().isEmpty()) throw new IllegalArgumentException(options.getErrors().toString());
                var identifiers = new AdditionalIdentifiers(options.configuration(), counters);
                new Csv2Fhir(Path.of(job.get("input").getAsString()).toFile(), destination.toFile(),
                        job.get("prefix").getAsString() + "-", null, options, identifiers).convertGeneratedTables();
                counters.clear();
                counters.putAll(identifiers.counterSnapshot());
                de.uni_leipzig.UcumMapper.invalidUcumCodes.clear();
                System.out.println("WORKBENCH_CONVERSION " + (++completed));
            }
            if (first.has("identifierCountersOutput"))
                Files.writeString(Path.of(first.get("identifierCountersOutput").getAsString()), new Gson().toJson(counters));
        }
    }
}
