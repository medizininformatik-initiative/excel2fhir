package de.uni_leipzig.life.csv2fhir;


import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;


import com.google.common.collect.ImmutableSet;

import de.uni_leipzig.life.csv2fhir.utils.ResourceMapper;

/**
 * @author AXS (24.06.2022)
 */
public class ConverterOptions {

    /** Counter for the loops through the patient IDs */
    public int loopCounter = 0;

    /**
     * Default file extension for files with converter options. If an option file
     * could not be found with the given name it will
     */
    public static final String CONVERTER_OPTIONS_FILE_EXTENSION = ".config";

    /** Map with all default options for the converting process */
    private final ResourceMapper options = ResourceMapper.of("Converter_Options.config");

    private final List<String> errors = new ArrayList<>();
    private ContractConfiguration configuration;
    private boolean executionFromCommandLine;

    public ConverterOptions withCommandLineExecution() {
        executionFromCommandLine = true;
        return this;
    }

    public ContractConfiguration configuration() { return configuration; }

    public boolean validationEnabled(boolean fallback) {
        return configuration == null || executionFromCommandLine ? fallback : configuration.stored("checks.fhirValidation").asBoolean();
    }

    public int patientsPerFile(int fallback) {
        return configuration == null || executionFromCommandLine ? fallback : configuration.stored("output.patientsPerFile").asInt();
    }

    public OutputFileType[] outputFormats(OutputFileType[] fallback) {
        if (configuration == null || executionFromCommandLine) return fallback;
        List<OutputFileType> formats = new ArrayList<>();
        configuration.stored("output.formats").forEach(value -> formats.add(OutputFileType.valueOf(value.asText())));
        return formats.toArray(OutputFileType[]::new);
    }

    public PatientOutputPolicy patientOutputPolicy() {
        return configuration == null ? PatientOutputPolicy.GENERATE_REFERENCE
                : PatientOutputPolicy.fromValue(configuration.stored("resource.Patient.mode").asText());
    }

    /** Cache for the boolean values */
    private final Map<BooleanOption, Boolean> booleanValues = new HashMap<>();

    /** Cache for the int values */
    private final Map<IntOption, Integer> intValues = new HashMap<>();

    /** Cache for the string values */
    private final Map<StringOption, String> stringValues = new HashMap<>();

    /**
     * @param optionsAbsoluteFileName options file to load
     */
    public ConverterOptions(String optionsAbsoluteFileName) {
        putValues(optionsAbsoluteFileName);
    }

    /** Read the optional file using the same Properties syntax as the Excel text. */
    private void putValues(String fileName) {
        if (fileName == null || fileName.isBlank()) return;
        Path path = Path.of(fileName);
        if (!Files.isRegularFile(path)) path = Path.of(fileName + CONVERTER_OPTIONS_FILE_EXTENSION);
        if (!Files.isRegularFile(path)) {
            errors.add("Options file not found: " + fileName);
            return;
        }
        try {
            readValues(Files.readString(path));
        } catch (IOException e) {
            errors.add("Cannot read options file: " + fileName + ": " + e.getMessage());
        }
    }

    public static ConverterOptions fromText(String text) {
        return fromText(text, Map.of());
    }

    /** Layer explicit values over the caller's defaults without treating overrides as duplicates. */
    public static ConverterOptions fromText(String text, Map<String, String> defaults) {
        ConverterOptions result = new ConverterOptions("");
        result.options.putAll(defaults);
        result.readValues(text);
        return result;
    }

    private void readValues(String text) {
        if (ContractConfiguration.isContractText(text)) {
            // A complete contract configuration replaces workflow/workbook defaults.
            options.clear();
            try {
                configuration = ContractConfiguration.parse(text);
                readLegacyValues("", configuration.javaProperties());
                errors.addAll(configuration.unsupportedSettings());
            } catch (IllegalArgumentException e) {
                errors.add("Invalid configuration: " + e.getMessage());
            }
            return;
        }
        readLegacyValues(text, Map.of());
    }

    private void readLegacyValues(String text, Map<String, String> defaults) {
        options.putAll(defaults);
        Properties values = new Properties() {
            @Override public synchronized Object put(Object key, Object value) {
                Object previous = super.put(key, value);
                if (previous != null && !previous.equals(value))
                    errors.add(key + ": conflicting duplicate values");
                return previous;
            }
        };
        try {
            values.load(new StringReader(text));
        } catch (IOException | IllegalArgumentException e) {
            errors.add("Invalid converter options: " + e.getMessage());
        }
        for (String retired : List.of("PID_LAST_NUMBER_INCREASE_INITIAL_OFFSET", "PID_LAST_NUMBER_INCREASE_LOOP_OFFSET")) {
            Object value = values.remove(retired);
            if (value != null && !value.toString().trim().equals("0"))
                errors.add(retired + ": numeric patient offsets are unsupported; use a prefix or suffix");
        }
        options.putAll(values);
        String versionOutput = getValue(StringOption.SYNTHEA_VERSION_OUTPUT);
        if (!versionOutput.isEmpty() && !versionOutput.equals("Jahr")) {
            try {
                if (de.uni_leipzig.life.csv2fhir.converter.DiagnosisValues.absentReason(versionOutput) == null)
                    errors.add("SYNTHEA_VERSION_OUTPUT: choose Jahr, a Data Absent Reason or an empty value");
            } catch (RuntimeException e) {
                errors.add("SYNTHEA_VERSION_OUTPUT: invalid Data Absent Reason");
            }
        }
        for (BooleanOption option : BooleanOption.values()) {
            if (options.containsKey(option.name())) {
                try { booleanValues.put(option, BooleanOption.isTrue(options.get(option.name()))); }
                catch (IllegalArgumentException e) {
                    errors.add(option + ": " + e.getMessage());
                    booleanValues.put(option, option.getDefault());
                }
            }
        }
        for (IntOption option : IntOption.values()) {
            if (options.containsKey(option.name())) {
                try {
                    int value = parseIntOption(option, options.get(option.name()));
                    if (option.name().startsWith("PID_LAST_NUMBER_") && value < 0)
                        throw new IllegalArgumentException("Value must be at least 0");
                    if (option == IntOption.SYNTHEA_MAPPING_YEAR && value != 2025 && value != 2026)
                        throw new IllegalArgumentException("Choose 2025 or 2026");
                    intValues.put(option, value);
                } catch (IllegalArgumentException e) {
                    errors.add(option + ": " + e.getMessage());
                    intValues.put(option, option.getDefault());
                }
            }
        }
    }

    public List<String> getErrors() {
        return List.copyOf(errors);
    }

    /**
     * @param booleanOption
     * @return
     */
    public boolean is(BooleanOption booleanOption) {
        Boolean value = booleanValues.get(booleanOption);
        if (value == null) {
            Object mapValueContent = options.get(booleanOption.toString());
            if (mapValueContent == null) {
                value = booleanOption.getDefault();
            } else {
                value = BooleanOption.isTrue(mapValueContent);
            }
            booleanValues.put(booleanOption, value);
        }
        return value;
    }

    /**
     * @param intOption
     * @return the value of this option
     * @throws NumberFormatException if the found string in the properties cannot
     *                               be parsed as an integer.
     */
    public int getValue(IntOption intOption) {
        Integer value = intValues.get(intOption);
        if (value == null) {
            Object mapValueContent = options.get(intOption.toString());
            if (mapValueContent == null) {
                value = intOption.getDefault();
            } else {
                value = parseIntOption(intOption, mapValueContent);
            }
            intValues.put(intOption, value);
        }
        return value;
    }

    private static Integer parseIntOption(IntOption intOption, Object mapValueContent) {
        String value = mapValueContent.toString().trim();
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer value for converter option " + intOption + ": " + value,
                    e);
        }
    }

    /**
     * @param stringOption
     * @return the value of this option
     * @throws NumberFormatException if the found string in the properties cannot
     *                               be parsed as an integer.
     */
    public String getValue(StringOption stringOption) {
        String value = stringValues.get(stringOption);
        if (value == null) {
            Object mapValueContent = options.get(stringOption.toString());
            if (mapValueContent == null) {
                value = stringOption.getDefault();
            } else {
                value = String.valueOf(mapValueContent.toString());
            }
            stringValues.put(stringOption, value);
        }
        return value;
    }

    /**
     * Boolean Options
     */
    public enum BooleanOption {
        /**
         * Enable to set a the optional reference from diagnoses (conditions) to
         * encounters. </br>
         * If <code>true</code> then circle references in the data are possible, if the
         * encounters have a reference to all diagnoses (conditions). Some FHIR-Servers
         * don't accept such circle references. In this case the corresponding option
         * {@link BooleanOption#SET_REFERENCE_FROM_ENCOUNTER_TO_CONDITION} must be set
         * to <code>false</code>.</br>
         * The Default is <code>false</code>.
         */
        SET_REFERENCE_FROM_CONDITION_TO_ENCOUNTER,
        /**
         * Enable to set the references from the encounters to the diagnoses
         * (conditions). </br>
         * If <code>true</code> then circle references in the data are possible, if the
         * diagnoses (conditions) have a reference to their encounter. Some
         * FHIR-Servers don't accept such circle references. In this case the
         * corresponding option
         * {@link BooleanOption#SET_REFERENCE_FROM_CONDITION_TO_ENCOUNTER} must be set
         * to <code>false</code>.</br>
         * The Default is <code>true</code>.
         */
        SET_REFERENCE_FROM_ENCOUNTER_TO_CONDITION,

        /**
         * Enable to set a the optional reference from procedures (conditions) to
         * encounters. </br>
         * If <code>true</code> then circle references in the data are possible, if the
         * encounters have a reference to all procedures (conditions). Some
         * FHIR-Servers don't accept such circle references. In this case the
         * corresponding option
         * {@link BooleanOption#SET_REFERENCE_FROM_ENCOUNTER_TO_PROCEDURE_CONDITION}
         * must be set to <code>false</code>.</br>
         * The Default is <code>false</code>.
         */
        SET_REFERENCE_FROM_PROCEDURE_CONDITION_TO_ENCOUNTER,
        /**
         * Enable to set the references from the encounters to the procedures
         * (conditions). </br>
         * If <code>true</code> then circle references in the data are possible, if the
         * procedures (conditions) have a reference to their encounters. Some
         * FHIR-Servers don't accept such circle references. In this case the
         * corresponding option
         * {@link BooleanOption#SET_REFERENCE_FROM_PROCEDURE_CONDITION_TO_ENCOUNTER}
         * must be set to <code>false</code>.</br>
         * The Default is <code>true</code>.
         */
        SET_REFERENCE_FROM_ENCOUNTER_TO_PROCEDURE_CONDITION,

        /**
         * If <code>true</code>, then Sub Encounters will have a diagnosis of the Super
         * Encounter attached. If the Super Encounter
         * has a main diagnosis (chief complaint), it is preferred.</br>
         * If <code>false</code>, no diagnosis is inherited. Missing diagnoses remain
         * absent; this option does not generate Data Absent Reasons.
         */
        ADD_MISSING_DIAGNOSES_FROM_SUPER_ENCOUNTER,
        /**
         * If true, Excel input data is checked for consistency before conversion.
         * Inconsistent input data aborts the conversion. Template structure and
         * converter options are checked even if false. This does not control FHIR
         * bundle validation. The default is true.
         */
        CHECK_INPUT_CONSISTENCY;

        /**
         * Set of String values which can be interpreted as booleans with value
         * <code>true</code>.
         */
        private static final Set<String> trueValues = ImmutableSet.of("true", "t", "wahr", "w", "yes", "y", "ja", "j",
                "1");

        /** All BooleanOptions whose default value is <code>true</code>. */
        private static final Set<BooleanOption> DEFAULT_TRUE_PROERTIES = ImmutableSet.of(
                SET_REFERENCE_FROM_ENCOUNTER_TO_CONDITION,
                SET_REFERENCE_FROM_ENCOUNTER_TO_PROCEDURE_CONDITION,
                CHECK_INPUT_CONSISTENCY);

        /**
         * @return Default-Wert dieser Property
         */
        public boolean getDefault() {
            return DEFAULT_TRUE_PROERTIES.contains(this);
        }

        private static final Set<String> falseValues = ImmutableSet.of("false", "f", "falsch", "no", "n", "nein", "0");

        public static boolean isTrue(Object value) {
            String text = value == null ? "" : value.toString().trim().toLowerCase(Locale.ROOT);
            if (trueValues.contains(text)) return true;
            if (falseValues.contains(text)) return false;
            throw new IllegalArgumentException("Invalid boolean value: " + value + "; expected true or false");
        }

    }

    /**
     * Integer Options
     */
    public static enum IntOption {
        /** German annual target catalogue used by the Synthea import. */
        SYNTHEA_MAPPING_YEAR(2026),

        /**
         * Start index counter for the number that will be added on the first element
         * of this type. The only resource type that will not get such an index counter
         * is Person. If the value is missing in this map then the default is 1.
         */
        START_ID_CONSENT,
        START_ID_CONDITION,
        START_ID_ENCOUNTER_LEVEL_2,
        START_ID_ENCOUNTER_LEVEL_3,
        START_ID_MEDICATION_REQUEST,
        START_ID_MEDICATION_ADMINISTRATION,
        START_ID_MEDICATION_STATEMENT,
        START_ID_OBSERVATION_LABORATORY,
        START_ID_OBSERVATION_VITAL_SIGNS,
        START_ID_PROCEDURE,
        START_ID_DOCUMENT_REFERENCE,

        /** Additional copies of each patient, identified by a -rep-N suffix. */
        PID_LAST_NUMBER_INCREASE_LOOP_COUNT(0);

        /** Default value of the int option */
        private final int defaultValue;

        /**
         * Creates an int option with the default value 1
         */
        private IntOption() {
            this(1);
        }

        /**
         * Creates an int option with the passed value.
         *
         * @param defaultValue
         */
        private IntOption(int defaultValue) {
            this.defaultValue = defaultValue;
        }

        /**
         * @return the default value of this option
         */
        private int getDefault() {
            return defaultValue;
        }

    }

    /**
     * String options
     */
    public static enum StringOption {
        /** Synthea version representation: Jahr, a DAR label/token, or omission. */
        SYNTHEA_VERSION_OUTPUT("Jahr"),

        /**
         * This prefix will be added to all patient IDs.</br>
         * The default is an empty string.
         */
        PID_PREFIX,
        /**
         * This suffix will be added to all patient IDs.</br>
         * The default is an empty string.
         */
        PID_SUFFIX;

        private final String defaultValue;

        private String getDefault() {
            return defaultValue;
        }

        private StringOption() {
            this("");
        }

        private StringOption(String defaultValue) {
            this.defaultValue = defaultValue;
        }

    }

    public String getFullPID(String pid) {
        return getFullPID(pid, loopCounter);
    }

    public String getFullPID(String pid, int iteration) {
        return getFullPIDForFileName(pid, iteration).replace('_', '-');
    }

    public String getFullPIDForFileName(String pid) {
        return getFullPIDForFileName(pid, loopCounter);
    }

    private String getFullPIDForFileName(String pid, int iteration) {
        if (iteration < 0) throw new IllegalArgumentException("Repetition must be at least 0");
        return getValue(StringOption.PID_PREFIX) + pid + getValue(StringOption.PID_SUFFIX)
                + (iteration == 0 ? "" : "-rep-" + iteration);
    }

    public String getPrefixWithSuffix() {
        return getValue(StringOption.PID_PREFIX) + getValue(StringOption.PID_SUFFIX);
    }

}
