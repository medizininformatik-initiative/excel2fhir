package de.uni_leipzig.life.csv2fhir;

import static de.uni_leipzig.life.csv2fhir.ConverterOptions.IntOption.PID_LAST_NUMBER_INCREASE_INITIAL_OFFSET;
import static de.uni_leipzig.life.csv2fhir.ConverterOptions.IntOption.PID_LAST_NUMBER_INCREASE_LOOP_OFFSET;

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

import org.apache.commons.lang3.StringUtils;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;

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
            errors.add("Optionsdatei nicht gefunden: " + fileName);
            return;
        }
        try {
            readValues(Files.readString(path));
        } catch (IOException e) {
            errors.add("Optionsdatei nicht lesbar: " + fileName + ": " + e.getMessage());
        }
    }

    public static ConverterOptions fromText(String text) {
        ConverterOptions result = new ConverterOptions("");
        result.readValues(text);
        return result;
    }

    private void readValues(String text) {
        Properties values = new Properties() {
            @Override public synchronized Object put(Object key, Object value) {
                Object previous = super.put(key, value);
                if (previous != null && !previous.equals(value))
                    errors.add(key + ": widersprüchliche mehrfache Angabe");
                return previous;
            }
        };
        try {
            values.load(new StringReader(text));
        } catch (IOException | IllegalArgumentException e) {
            errors.add("Ungültige Konvertierungsoptionen: " + e.getMessage());
        }
        options.putAll(values);
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
                        throw new IllegalArgumentException("Wert muss mindestens 0 sein");
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
         * If true, the Excel input template is validated strictly before the
         * conversion starts. Strict validation aborts the conversion on inconsistent
         * input data instead of generating fallback resources.
         */
        VALIDATE_STRICT;

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
                VALIDATE_STRICT);

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
            throw new IllegalArgumentException("Ungültiger Wahrheitswert: " + value + "; true oder false erwartet");
        }

    }

    /**
     * Integer Options
     */
    public static enum IntOption {
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

        /**
         * The last number in all patient IDs of an data set will be increased by this
         * value. At the beginning this number will be added to all patient IDs.</br>
         * Default value is 0.
         */
        PID_LAST_NUMBER_INCREASE_INITIAL_OFFSET(0),
        /**
         * The last number in all patient IDs of an data set will be increased by this
         * value on every loop. For a contiguous numeric ID range this number must be
         * greater than the difference between the highest and lowest number. If
         * not then some patients can be generated with the same ID.</br>
         * Default value is 0.
         */
        PID_LAST_NUMBER_INCREASE_LOOP_OFFSET(0),
        /**
         * Count of repetitions of increasings of the patient ID. If you want to expand
         * a data set n times then set this value to n and the
         * PID_LAST_NUMBER_INCREASE_LOOP_OFFSET in the described way.</br>
         * Default value is 0.
         */
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

    /**
     * @param s
     * @return the substring positions of the last integer number in this string
     */
    private static Range<Integer> getLastNumberStringBounds(String s) {
        // this is probably still faster than using a RegExp
        int start = -1;
        int end = -1;
        for (int i = s.length() - 1; i >= 0; i--) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) {
                if (end == -1) {
                    end = i + 1;
                }
                start = i;
            } else if (end != -1) {
                break;
            }
        }
        return Range.closedOpen(start, end);
    }

    /**
     * @param pid
     * @return
     */
    public String getIncreasedLastPidNumber(String pid, int value) {
        Range<Integer> lastNumberStringBounds = getLastNumberStringBounds(pid);
        int start = lastNumberStringBounds.lowerEndpoint();
        int end = lastNumberStringBounds.upperEndpoint();
        if (start < 0 || end < 0) {
            throw new IllegalArgumentException("PID does not contain a number to increase: " + pid);
        }
        String preNumberString = pid.substring(0, start);
        String numberSubString = pid.substring(start, end);
        String afterNumberString = pid.substring(end);
        int numberLength = numberSubString.length();
        int number = parsePidNumber(pid, numberSubString);
        number = Math.addExact(number, value);
        numberSubString = Integer.toString(number);
        if (numberSubString.length() < numberLength) {
            numberSubString = StringUtils.leftPad(numberSubString, numberLength, "0");
        }
        return preNumberString + numberSubString + afterNumberString;
    }

    private static int parsePidNumber(String pid, String numberSubString) {
        try {
            return Integer.parseInt(numberSubString);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("PID contains an invalid number to increase: " + pid, e);
        }
    }

    /**
     * @param pid
     * @return
     */
    public String getFullPID(String pid) {
        return getFullPID(pid, loopCounter);
    }

    public String getFullPID(String pid, int iteration) {
        int loopOffset = Math.multiplyExact(iteration, getValue(PID_LAST_NUMBER_INCREASE_LOOP_OFFSET));
        int pidOffset = Math.addExact(getValue(PID_LAST_NUMBER_INCREASE_INITIAL_OFFSET), loopOffset);
        if (pidOffset > 0) {
            pid = getIncreasedLastPidNumber(pid, pidOffset);
        }
        pid = getValue(StringOption.PID_PREFIX) + pid + getValue(StringOption.PID_SUFFIX);
        return pid.replace('_', '-'); // AXS: (Some) FHIR Server will not accept IDs with an underscore!
    }

    /**
     * @return
     */
    public String getPrefixWithSuffix() {
        return getValue(StringOption.PID_PREFIX) + getValue(StringOption.PID_SUFFIX);
    }

}
