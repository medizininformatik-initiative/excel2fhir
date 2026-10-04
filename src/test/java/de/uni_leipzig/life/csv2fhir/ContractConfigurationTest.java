package de.uni_leipzig.life.csv2fhir;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;

public class ContractConfigurationTest {
    private static final String VERSION = "CONFIGURATION_VERSION = 1\n";

    @Test public void storedSelectionsStayIndependentFromEffectiveDependencies() {
        var config = ContractConfiguration.parse(VERSION + "CONTACT_FACILITY_ENABLED = false\n"
                + "# REFERENCE_CONDITION_ENCOUNTER = facility\n");
        assertEquals("facility", config.stored("reference.Condition.encounter").asText());
        // The available default department must not be substituted.
        assertTrue(config.effective("reference.Condition.encounter").isEmpty());
        assertTrue(config.effective("contact.department.enabled").get().asBoolean());
        var enabled = ContractConfiguration.parse(VERSION + "# REFERENCE_CONDITION_ENCOUNTER = facility\n");
        assertEquals("facility", enabled.effective("reference.Condition.encounter").get().asText());
        var inactive = ContractConfiguration.parse(VERSION + "ENCOUNTER_ENABLED=false\n");
        assertTrue(inactive.effective("contact.facility.enabled").isEmpty());
        assertTrue(inactive.effective("contact.diagnoses.levels").isEmpty());
    }

    @Test public void versionedTextOverridesWorkflowDefaultsAndPreservesUnicodeEscapes() {
        var options = ConverterOptions.fromText(VERSION + "PATIENT_MODE = neither\nPID_PREFIX = \\ ü\\n\\t\\\\\n",
                Map.of("PID_SUFFIX", "workflow-suffix", "START_ID_CONDITION", "99"));
        assertEquals(PatientOutputPolicy.NEITHER, options.patientOutputPolicy());
        assertEquals(" ü\n\t\\", options.getValue(ConverterOptions.StringOption.PID_PREFIX));
        assertEquals("", options.getValue(ConverterOptions.StringOption.PID_SUFFIX));
        assertEquals(1, options.getValue(ConverterOptions.IntOption.START_ID_CONDITION));
        var second = ContractConfiguration.parse(VERSION);
        assertEquals("generate-reference", second.stored("resource.Patient.mode").asText());
    }

    @Test public void unsupportedDefaultsAndEffectiveDarAreReportedInsteadOfIgnored() {
        var options = ConverterOptions.fromText(VERSION);
        assertTrue(options.getErrors().stream().anyMatch(e -> e.contains("OUTPUT_FORMATS")));
        assertFalse(options.getErrors().stream().anyMatch(e -> e.contains("REFERENCE_CONDITION_ENCOUNTER")));
        assertFalse(options.getErrors().stream().anyMatch(e -> e.contains("CONTACT_DEPARTMENT_PART_OF")));
        var config = ContractConfiguration.parse(VERSION + "PATIENT_MODE=neither\n# DAR_PATIENT_NAME_FAMILY=masked\n");
        assertFalse(config.unsupportedSettings().stream().anyMatch(e -> e.contains("Patient.name.family")));
        config = ContractConfiguration.parse(VERSION + "DAR_PATIENT_NAME_FAMILY=masked\n");
        assertTrue(config.unsupportedSettings().stream().anyMatch(e -> e.contains("Patient.name.family")));
    }

    @Test public void strictFormatRejectsUnknownDuplicateAndInvalidInactiveValues() {
        for (String text : List.of("", "CONFIGURATION_VERSION=2", "# CONFIGURATION_VERSION=1",
                VERSION + "PATIENT_MODE=bad", VERSION + "# PATIENT_MODE=bad",
                VERSION + "PATIENT_MODE=neither\n# PATIENT_MODE=neither",
                VERSION + "OTHER=true", VERSION + "CHECK_INPUT_CONSISTENCY=yes",
                VERSION + "PID_PREFIX=broken\\", VERSION + "PID_PREFIX=\\uXYZW",
                VERSION + "DAR_PATIENT_NAME_FAMILY=invalid", VERSION + "OUTPUT_FORMATS=JSON,JSON",
                VERSION + "OUTPUT_PATIENTS_PER_FILE=0", VERSION + "START_ID_CONDITION=1.2",
                VERSION + "IDENTIFIER_RULE_1_ENABLED=true")) {
            assertThrows(text, IllegalArgumentException.class, () -> ContractConfiguration.parse(text));
        }
        assertFalse(ConverterOptions.fromText("PATIENT_MODE=neither").getErrors().isEmpty());
        assertFalse(ConverterOptions.fromText("CONFIGURATION_VERSION:1").getErrors().isEmpty());
        assertFalse(ConverterOptions.fromText(VERSION + "START_ID_CONDITION=9007199254740991").getErrors().isEmpty());
    }

    @Test public void identifierRulesValidateEvenWhenInactive() {
        String rule = "IDENTIFIER_RULE_1_ID=a152e771-3d5a-4cb1-9866-35fa6d91fd83\n"
                + "IDENTIFIER_RULE_1_ENABLED=false\n# IDENTIFIER_RULE_1_RESOURCES=Patient,Observation\n"
                + "# IDENTIFIER_RULE_1_SYSTEM=urn:test\n# IDENTIFIER_RULE_1_PATTERN={{id}}-{count:08}-{hash}\n";
        var config = ContractConfiguration.parse(VERSION + rule);
        assertFalse(config.unsupportedSettings().stream().anyMatch(e -> e.contains("Identifier execution")));
        var active = ContractConfiguration.parse(VERSION + rule.replace("ENABLED=false", "ENABLED=true"));
        assertTrue(active.unsupportedSettings().stream().anyMatch(e -> e.contains("Identifier execution")));
        for (String bad : List.of("{bad}", "{count:8}", "{count:00}", "{", "}", "{count:0999999999999999999}")) {
            assertThrows(IllegalArgumentException.class, () -> ContractConfiguration.parse(VERSION + rule.replace("{{id}}-{count:08}-{hash}", bad)));
        }
        assertThrows(IllegalArgumentException.class, () -> ContractConfiguration.parse(VERSION + rule + rule.replace("RULE_1_", "RULE_2_")));
    }

    @Test public void workbookCsvAndExternalFilesShareParserAndPreserveSnapshot() throws Exception {
        String text = VERSION + "# PATIENT_MODE=reference-only\nPID_PREFIX=ä-\n";
        Path root = Files.createTempDirectory("contract-sources");
        try {
            Path external = root.resolve("options.config");
            Files.writeString(external, text);
            Files.writeString(root.resolve("case_Konvertierungsoptionen.csv"), text);
            Path workbook = root.resolve("case.xlsx");
            try (var book = new XSSFWorkbook()) {
                var sheet = book.createSheet("Konvertierungsoptionen");
                int row = 0;
                for (String line : text.split("\n")) sheet.createRow(row++).createCell(0).setCellValue(line);
                try (var out = Files.newOutputStream(workbook)) { book.write(out); }
            }
            var sets = List.of(ConverterOptionSet.external(List.of(external.toFile())).get(0),
                    ConverterOptionSet.csv(root.toFile(), "case_").get(0),
                    ConverterOptionSet.workbook(workbook.toFile()).get(0));
            for (var set : sets) {
                var options = set.options();
                assertEquals(PatientOutputPolicy.REFERENCE_ONLY, options.patientOutputPolicy());
                assertEquals("ä-", options.getValue(ConverterOptions.StringOption.PID_PREFIX));
                assertEquals(sets.get(0).options().getErrors(), options.getErrors());
                set.snapshot(root.resolve("snapshot"));
                assertEquals(text, Files.readString(root.resolve("snapshot/converter-options.config")));
            }
        } finally {
            try (var files = Files.walk(root)) {
                for (var path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
}
