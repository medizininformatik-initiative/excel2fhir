import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import com.google.gson.*;
import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.Bundle;

/** Decode converter XML output for the source roundtrip audit. */
public class ReadXmlBundles {
    public static void main(String[] args) throws Exception {
        var context = FhirContext.forR4();
        var result = new JsonArray();
        var paths = JsonParser.parseString(new String(System.in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonArray();
        for (var path : paths) {
            var bundle = context.newXmlParser().parseResource(Bundle.class, Files.readString(Path.of(path.getAsString())));
            result.add(JsonParser.parseString(context.newJsonParser().encodeResourceToString(bundle)));
        }
        Files.writeString(Path.of(args[0]), result.toString());
    }
}
