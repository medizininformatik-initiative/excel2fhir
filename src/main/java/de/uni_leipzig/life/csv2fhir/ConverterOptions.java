package de.uni_leipzig.life.csv2fhir;

import de.uni_leipzig.life.csv2fhir.utils.ResourceMapper;

/**
 * @author AXS (24.06.2022)
 */
public enum ConverterOptions {
    /**
     * If <code>true</code>, then Sub Encounters will have a diagnosis of the
     * Super Encounter attached instead of a Data Absent Reason. If the Super
     * Encounter has a main diagnosis (chief complaint), it is preferred.</br>
     * If <code>false</code>, the non-existing diagnoses are supplemented by an
     * "unknown" Data Absent Reason.
     */
    ADD_MISSING_DIAGNOSES_FROM_SUPER_ENCOUNTER,
    /**
     * If true, then Sub Encounters will have a diagnosis of the Super Encounter
     * attached instead of a Data Absent Reason. If false, the non-existing
     * classes are supplemented by an "unknown" Data Absent Reason.
     */
    ADD_MISSING_CLASS_FROM_SUPER_ENCOUNTER;

    /**
     * Caches the boolean value from the resource map
     */
    public Boolean is = null;

    /** Map with all options for the converting process */
    private static final ResourceMapper CONVERTER_OPTIONS = ResourceMapper.of("Converter_Options.config");

    /**
     * @param optionResourceName
     * @return
     */
    public boolean is() {
        if (is == null) {
            is = CONVERTER_OPTIONS.getBoolean(name());
        }
        return is;
    }

    /**
     * @param converterOptions
     * @return <code>true</code> if at least one of the parameter returns
     *         {@link ConverterOptions#is()} == <code>true</code>.
     */
    public static boolean is(ConverterOptions... converterOptions) {
        for (ConverterOptions converterOption : converterOptions) {
            if (converterOption.is()) {
                return true;
            }
        }
        return false;
    }

}
