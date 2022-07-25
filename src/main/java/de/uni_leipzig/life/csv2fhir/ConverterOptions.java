package de.uni_leipzig.life.csv2fhir;

import java.util.Set;

import com.google.common.collect.ImmutableSet;

import de.uni_leipzig.life.csv2fhir.utils.ResourceMapper;

/**
 * @author AXS (24.06.2022)
 */
public class ConverterOptions {

    /**
     * Default file extension for files with converter options. If an option
     * file could not be found with the given name it will
     */
    public static final String CONVERTER_OPTIONS_FILE_EXTENSION = ".config";

    /** Map with all default options for the converting process */
    private static final ResourceMapper CONVERTER_OPTIONS = ResourceMapper.of("Converter_Options.config");

    /**
     * @param optionsAbsoluteFileName
     */
    public static void putValues(String optionsAbsoluteFileName) {
        //add or owerwrite the defaults with the project specific option values
        if (!CONVERTER_OPTIONS.load(optionsAbsoluteFileName)) {
            CONVERTER_OPTIONS.load(optionsAbsoluteFileName + CONVERTER_OPTIONS_FILE_EXTENSION);
        }
    }

    /**
     * Boolean Options
     */
    public static enum BooleanOption {
        /**
         * If <code>true</code>, then Sub Encounters will have a diagnosis of
         * the Super Encounter attached instead of a Data Absent Reason. If the
         * Super Encounter has a main diagnosis (chief complaint), it is
         * preferred.</br>
         * If <code>false</code>, the non-existing diagnoses are supplemented by
         * an "unknown" Data Absent Reason.
         */
        ADD_MISSING_DIAGNOSES_FROM_SUPER_ENCOUNTER,
        /**
         * If <code>true</code>, then Sub Encounters will have the same class
         * coding like the Super Encounter attached instead of a Data Absent
         * Reason.</br>
         * If <code>false</code>, the non-existing class codings are
         * supplemented by an "unknown" Data Absent Reason.</br>
         * Every Encounter needs at least one class coding to be valid.
         */
        ADD_MISSING_CLASS_FROM_SUPER_ENCOUNTER;

        /**
         * Caches the boolean value from the resource map
         */
        public Boolean is = null;

        /**
         * Set of String values that can be interpreted as booleans with value
         * "true".
         */
        private static final Set<String> trueValues = ImmutableSet.of("true", "t", "wahr", "w", "yes", "y", "ja", "j", "1");

        /**
         * @param optionResourceName
         * @return
         */
        public boolean is() {
            if (is == null) {
                Object mapValueContent = CONVERTER_OPTIONS.get(toString());
                if (mapValueContent == null) {
                    is = getDefault();
                } else {
                    String value = mapValueContent.toString().trim().toLowerCase();
                    is = trueValues.contains(value);
                }
            }
            return is;
        }

        /** All BooleanOptions whose default value is <code>true</code>. */
        private static final Set<BooleanOption> DEFAULT_TRUE_PROERTIES = ImmutableSet.of();

        /**
         * @return Default-Wert dieser Property
         */
        public boolean getDefault() {
            return DEFAULT_TRUE_PROERTIES.contains(this);
        }

        /**
         * @param converterOptions
         * @return <code>true</code> if at least one of the parameter returns
         *         {@link ConverterOptions#is()} == <code>true</code>.
         */
        public static boolean is(BooleanOption... booleanOptions) {
            for (BooleanOption booleanOption : booleanOptions) {
                if (booleanOption.is()) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Integer Options
     */
    public static enum IntOption {
        /**
         * Start index counter for the number that will be added on the first
         * element of this type. The only resource type that will not get such
         * an index counter is Person. If the value is missing in this map then
         * the default is 1.
         */
        START_ID_CONSENT,
        START_ID_DIAGNOSIS,
        START_ID_ENCOUNTER_LEVEL_2,
        START_ID_MEDICATION_ADMINISTRATION,
        START_ID_MEDICATION_STATEMENT,
        START_ID_OBSERVATION_LABORATORY,
        START_ID_OBSERVATION_VITAL_SIGNS,
        START_ID_PROCEDURE;

        /** Default value of the int option */
        private final int defaultValue;

        /** Value of the int option */
        private Integer value;

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
         * @return the value of this option
         * @throws NumberFormatException if the found string in the properties
         *             cannot be parsed as an integer.
         */
        public int getValue() {
            if (value == null) {
                Object mapValueContent = CONVERTER_OPTIONS.get(toString());
                if (mapValueContent == null) {
                    value = getDefault();
                } else {
                    value = Integer.valueOf(mapValueContent.toString().trim());
                }
            }
            return value;
        }

        /**
         * @return the default value of this option
         */
        public int getDefault() {
            return defaultValue;
        }

    }

}
