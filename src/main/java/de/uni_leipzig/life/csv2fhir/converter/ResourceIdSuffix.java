package de.uni_leipzig.life.csv2fhir.converter;

/**
 * For all resources except patient and medication, a unique string must be
 * specified to expand the respective encounter or patient ID to generate an ID
 * for the resource. Unique means that 2 different resource types must not have
 * the same ID and so not the same suffix string here.
 *
 * @author AXS (06.09.2023)
 */
public enum ResourceIdSuffix {

    // If a resource type is added, then the suffix must be different from all others.
    CONSENT("CO"),
    CONDITION("C"),
    DOCUMENT_REFERENCE("DR"),
    ENCOUNTER_LEVEL_1("E"),
    ENCOUNTER_LEVEL_2("A"),
    ENCOUNTER_LEVEL_3("V"),
    MEDICATION_STATEMENT("MS"),
    MEDICATION_ADMINISTRATION("MA"),
    MEDICATION_REQUEST("MR"),
    OBSERVATION_LABORATORY("OL"),
    OBSERVATION_VITALSIGNS("OV"),
    PROCEDURE("P");

    /** Resource ID suffix appended to the Encounter or Patient ID. */
    private final String suffix;

    /**
     * @param suffix Resource ID suffix appended to the Encounter or Patient ID.
     */
    private ResourceIdSuffix(String suffix) {
        this.suffix = "-" + suffix + "-";
    }

    @Override
    public String toString() {
        return suffix;
    }
}
