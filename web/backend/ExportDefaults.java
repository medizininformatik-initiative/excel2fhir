import de.uni_leipzig.life.csv2fhir.ConverterOptionSet;
import java.nio.file.Path;

/** Build-time adapter: obtain effective defaults directly from the converter. */
class ExportDefaults {
    public static void main(String[] args) throws Exception {
        java.nio.file.Files.createDirectories(Path.of(args[0]));
        java.nio.file.Files.writeString(Path.of(args[0], "converter-options.config"),
                de.uni_leipzig.life.csv2fhir.ContractConfiguration.defaultProperties());
    }
}
