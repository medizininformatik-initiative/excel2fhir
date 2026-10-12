package de.uni_leipzig.life.csv2fhir;

import static org.junit.Assert.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.csv.*;
import org.hl7.fhir.r4.model.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

/** Verify complete, repeatable clinical output independent of file partitioning and encoding. */
public class OutputFormatsWorkflowTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private Path input;

    @Before public void setup() throws Exception {
        input = temp.newFolder().toPath();
        List<Map<String, String>> patients = new ArrayList<>(), contacts = new ArrayList<>(), medications = new ArrayList<>();
        for (String id : List.of("p1", "p2", "p3")) {
            patients.add(Map.of("Patient-ID", id, "Vorname", "Jörg", "Nachname", "Groß", "Geburtsdatum", "2000-01-01", "Geschlecht", "weiblich"));
            contacts.add(Map.of("Patient-ID", id, "Fall-Nr", "1", "Start", "2026-01-01", "Ende", "2026-01-03",
                    "Einrichtungskontaktklasse", "stationaer", "Fachabteilung", "0100"));
            medications.add(Map.of("Patient-ID", id, "Fall-Nr", "1", "Medikationstyp", "Verabreichung",
                    "Präparatcode", "00000000", "Präparatcodesystem", "PZN", "Beginn", "2026-01-02",
                    "Einzeldosis", "0.125", "Dosiereinheit", "mg"));
        }
        table(TableIdentifier.Person, patients);
        table(TableIdentifier.Fall, contacts);
        table(TableIdentifier.Medikation, medications);
    }

    private void table(TableIdentifier type, List<Map<String, String>> rows) throws Exception {
        Set<String> headers = new LinkedHashSet<>(type.getMandatoryColumnNames());
        rows.forEach(row -> headers.addAll(row.keySet()));
        try (var writer = Files.newBufferedWriter(input.resolve("case_" + type + ".csv"));
                var csv = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
            csv.printRecord(headers);
            for (var row : rows) csv.printRecord(headers.stream().map(h -> row.getOrDefault(h, "")).toList());
        }
    }

    private Path run(int patientsPerFile, OutputFileType... formats) throws Exception {
        return run(patientsPerFile, 1, formats);
    }

    private Path run(int patientsPerFile, int repetitions, OutputFileType... formats) throws Exception {
        Path output = temp.newFolder().toPath();
        var options = ConverterOptions.fromText("CONFIGURATION_VERSION=1\nOUTPUT_PATIENTS_PER_FILE=" + patientsPerFile
                + "\nOUTPUT_FORMATS=" + String.join(",", Arrays.stream(formats).map(Enum::name).toList())
                + "\nPID_PREFIX=site-\nPID_LAST_NUMBER_INCREASE_LOOP_COUNT=" + repetitions + "\n"
                + "TIME_SHIFT_ENABLED=true\nTIME_SHIFT_BASE_DAYS=5\nTIME_SHIFT_ITERATION_DAYS=-2\n"
                + "MEDICATION_ADMINISTRATION_TREATMENT=add-statement\n");
        var converter = new Csv2Fhir(input.toFile(), output.toFile(), "case_", null, options);
        converter.convertFiles(patientsPerFile, formats);
        assertFalse(new com.google.gson.Gson().toJson(converter.getImportReport().issues), converter.getImportReport().hasErrors());
        return output;
    }

    private List<Bundle> bundles(Path output, String suffix) throws Exception {
        List<Bundle> result = new ArrayList<>();
        try (var paths = Files.list(output)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(suffix) && !p.toString().endsWith(".import.json")).sorted().toList()) {
                if (suffix.equals(".json.zip")) {
                    // ZipFile also verifies that the writer closed the archive's central directory.
                    try (var archive = new ZipFile(path.toFile())) {
                        for (var entry : Collections.list(archive.entries())) {
                            try (var stream = archive.getInputStream(entry)) {
                                result.add(parse(stream, false));
                            }
                        }
                    }
                } else if (suffix.equals(".ndjson")) {
                    for (String line : Files.readAllLines(path)) result.add(OutputFileType.JSON.getParser().parseResource(Bundle.class, line));
                } else {
                    try (InputStream stream = suffix.equals(".json.gz") ? new GZIPInputStream(Files.newInputStream(path))
                            : suffix.equals(".json.bz2") ? new BZip2CompressorInputStream(Files.newInputStream(path)) : Files.newInputStream(path)) {
                        result.add(parse(stream, suffix.equals(".xml")));
                    }
                }
            }
        }
        assertFalse("Missing output " + suffix, result.isEmpty());
        return result;
    }

    private Bundle parse(InputStream stream, boolean xml) throws Exception {
        return (xml ? OutputFileType.XML : OutputFileType.JSON).getParser().parseResource(Bundle.class,
                new String(stream.readAllBytes(), StandardCharsets.UTF_8));
    }

    private Map<String, Bundle.BundleEntryComponent> entries(List<Bundle> bundles, int maximumPatients) {
        return entries(bundles, maximumPatients, 1);
    }

    private Map<String, Bundle.BundleEntryComponent> entries(List<Bundle> bundles, int maximumPatients, int repetitions) {
        Map<String, Bundle.BundleEntryComponent> result = new TreeMap<>();
        Set<String> patients = new TreeSet<>();
        for (Bundle bundle : bundles) {
            assertEquals(Bundle.BundleType.TRANSACTION, bundle.getType());
            long count = bundle.getEntry().stream().filter(e -> e.getResource() instanceof Patient).count();
            assertTrue("Patient count per bundle: " + count, count > 0 && count <= maximumPatients);
            for (var entry : bundle.getEntry()) {
                Resource resource = entry.getResource();
                String key = resource.fhirType() + "/" + resource.getIdElement().getIdPart();
                if (resource instanceof Patient) assertTrue("Duplicate patient " + key, patients.add(resource.getIdElement().getIdPart()));
                var previous = result.put(key, entry);
                if (previous != null) {
                    assertTrue("Duplicate clinical resource " + key, resource instanceof Medication || resource instanceof Location);
                    assertTrue("Conflicting shared resource " + key, previous.equalsDeep(entry));
                }
            }
        }
        Set<String> expectedPatients = new TreeSet<>();
        for (int iteration = 0; iteration <= repetitions; iteration++)
            for (String patient : List.of("site-p1", "site-p2", "site-p3"))
                expectedPatients.add(patient + (iteration == 0 ? "" : "-rep-" + iteration));
        assertEquals(expectedPatients, patients);
        return result;
    }

    private void equalEntries(Map<String, Bundle.BundleEntryComponent> expected, Map<String, Bundle.BundleEntryComponent> actual) {
        assertEquals(expected.keySet(), actual.keySet());
        for (String key : expected.keySet()) assertTrue("Changed resource/request " + key, expected.get(key).equalsDeep(actual.get(key)));
    }

    @Test public void allFormatsRetainPartialFinalGroupsAndRepetitions() throws Exception {
        Map<String, Bundle.BundleEntryComponent> baseline = null;
        for (int size : List.of(1, 4, 2, 1000)) {
            Path output = run(size, OutputFileType.values());
            for (String suffix : List.of(".json", ".xml", ".ndjson", ".json.gz", ".json.bz2", ".json.zip")) {
                var actual = entries(bundles(output, suffix), suffix.equals(".ndjson") || suffix.equals(".json.zip") ? 1 : size);
                if (baseline == null) baseline = actual;
                else equalEntries(baseline, actual);
            }
        }
        // A fresh conversion must preserve all resource fields, IDs and transaction requests.
        equalEntries(baseline, entries(bundles(run(4, OutputFileType.JSON), ".json"), 4));
    }

    @Test public void compressedOnlyOutputsKeepEveryPatientWithoutPlainJson() throws Exception {
        Path output = run(4, OutputFileType.JSONGZIP, OutputFileType.JSONBZ2);
        var gzip = entries(bundles(output, ".json.gz"), 4);
        equalEntries(gzip, entries(bundles(output, ".json.bz2"), 4));
        try (var paths = Files.list(output)) {
            assertFalse(paths.anyMatch(p -> p.toString().endsWith(".json") && !p.toString().endsWith(".import.json")));
        }
    }

    @Test public void finalPartialGroupWithoutRepetitionsIsClosedInEveryFormat() throws Exception {
        Path output = run(2, 0, OutputFileType.values());
        var baseline = entries(bundles(output, ".json"), 2, 0);
        for (String suffix : List.of(".xml", ".ndjson", ".json.gz", ".json.bz2", ".json.zip"))
            equalEntries(baseline, entries(bundles(output, suffix),
                    suffix.equals(".ndjson") || suffix.equals(".json.zip") ? 1 : 2, 0));
    }
}
