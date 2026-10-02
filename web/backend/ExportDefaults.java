import de.uni_leipzig.life.csv2fhir.ConverterOptionSet;
import java.nio.file.Path;

/** Build-time adapter: obtain effective defaults directly from the converter. */
class ExportDefaults {
    public static void main(String[] args) throws Exception {
        new ConverterOptionSet("default", "").snapshot(Path.of(args[0]));
    }
}
