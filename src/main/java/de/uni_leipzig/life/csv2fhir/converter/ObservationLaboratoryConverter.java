package de.uni_leipzig.life.csv2fhir.converter;

import static com.google.common.base.Strings.isNullOrEmpty;
import static de.uni_leipzig.life.csv2fhir.ConverterOptions.IntOption.START_ID_OBSERVATION_LABORATORY;
import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Laborbefund;
import static de.uni_leipzig.life.csv2fhir.converter.ObservationLaboratoryConverter.ObservationLaboratory_Columns.Einheit;
import static de.uni_leipzig.life.csv2fhir.converter.ObservationLaboratoryConverter.ObservationLaboratory_Columns.LOINC;
import static de.uni_leipzig.life.csv2fhir.converter.ObservationLaboratoryConverter.ObservationLaboratory_Columns.Messwert;
import static de.uni_leipzig.life.csv2fhir.converter.ObservationLaboratoryConverter.ObservationLaboratory_Columns.Parameter;
import static de.uni_leipzig.life.csv2fhir.converter.ObservationLaboratoryConverter.ObservationLaboratory_Columns.Zeitstempel_Abnahme;
import static de.uni_leipzig.life.csv2fhir.utils.DecimalUtil.parseDecimal;
import static org.hl7.fhir.r4.model.Observation.ObservationStatus.FINAL;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

import com.google.common.collect.ImmutableList;

import de.uni_leipzig.imise.validate.FHIRValidator;
import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterResult;
import de.uni_leipzig.life.csv2fhir.TableColumnIdentifier;
import de.uni_leipzig.life.csv2fhir.utils.DateUtil;

/**
 * @author jheuschkel (19.10.2020), AXS (05.11.2021)
 */
public class ObservationLaboratoryConverter extends Converter {

    /**
     *
     */
    public static enum ObservationLaboratory_Columns implements TableColumnIdentifier {
        LOINC,
        Parameter,
        Messwert,
        Einheit,
        Zeitstempel_Abnahme {
            @Override
            public String toString() {
                return "Zeitstempel (Abnahme)";
            }
        },
        //Methode //not used
    }

    /**  */
    static final String PROFILE = "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab";
    // https://simplifier.net/medizininformatikinitiative-modullabor/observationlab

    /**
     * The always identical category for all observations. We can use always the
     * dame object.
     */
    static final List<CodeableConcept> LABORYTORY_OBSERVATION_FIXED_CATEGORY = getLaborytoryObservationFixedCategory();

    /**
     * @param record
     * @param result
     * @param validator
     * @throws Exception
     */
    public ObservationLaboratoryConverter(CSVRecord record, ConverterResult result, FHIRValidator validator) throws Exception {
        super(record, result, validator);
    }

    @Override
    protected List<Resource> convertInternal() throws Exception {
        Observation observation = new Observation();
        int nextId = result.getNextId(Laborbefund, Observation.class, START_ID_OBSERVATION_LABORATORY);
        Reference encounterReference = getEncounterReference();
        // If the encounter is defined for this Observation so we can use it for the id. If not we can only use the PID
        String id = (encounterReference != null ? getEncounterId() : getPatientId()) + "-OL-" + nextId;
        observation.setId(id);
        observation.setMeta(new Meta().addProfile(PROFILE));
        observation.setStatus(FINAL);
        observation.setSubject(getPatientReference()); // if null then observation is invalid
        observation.setEncounter(encounterReference);
        setEffective(observation, this, Zeitstempel_Abnahme);
        observation.setCode(parseObservationCode());
        observation.setCode(parseLoincCodeableConcept(LOINC, Parameter));
        observation.setValue(parseObservationValue(Messwert, Einheit));
        observation.setIdentifier(getIdentifier(id, getDIZId()));
        observation.setCategory(LABORYTORY_OBSERVATION_FIXED_CATEGORY);
        return Collections.singletonList(observation);
    }

    /**
     * @return
     * @throws Exception
     */
    protected CodeableConcept parseObservationCode() throws Exception {
        String loincCodeSystem = "http://loinc.org";
        Coding loincCoding = createCoding(loincCodeSystem, LOINC);
        if (isDataAbsentReason(loincCoding)) {
            warning(LOINC + " empty for Record -> creating \"unknown\" Data Absent Reason");
        }
        return createCodeableConcept(loincCoding, Parameter);
    }

    /**
     * @param loincCodeColumnIdentifier
     * @param loincTextColumnIdentifier
     * @return
     */
    protected CodeableConcept parseLoincCodeableConcept(Enum<?> loincCodeColumnIdentifier, Enum<?> loincTextColumnIdentifier) {
        return createCodeableConcept("http://loinc.org", loincCodeColumnIdentifier, loincTextColumnIdentifier);
    }

    /**
     * @param observation
     * @param converter
     * @param timestampColumnIdentifier
     */
    public static void setEffective(Observation observation, Converter converter, Enum<?> timestampColumnIdentifier) {
        try {
            String timestamp = converter.get(timestampColumnIdentifier);
            observation.setEffective(DateUtil.parseDateTimeType(timestamp));
        } catch (Exception e) {
            converter.warning("Can not parse " + timestampColumnIdentifier + " for Record -> set \"unknown\" Data Absent Reason");
            DateTimeType effectiveDateTimeType = observation.getEffectiveDateTimeType();
            effectiveDateTimeType.addExtension(DATA_ABSENT_REASON_UNKNOWN);
        }
    }

    /**
     * @param converter
     * @param valueColumnIdentifier
     * @param unitColumnIdentifier
     * @return
     * @throws Exception
     */
    public Quantity parseObservationValue(Enum<?> valueColumnIdentifier, Enum<?> unitColumnIdentifier) throws Exception {
        BigDecimal value = null;
        try {
            String valueString = get(valueColumnIdentifier);
            value = parseDecimal(valueString);
        } catch (Exception e) {
            warning(valueColumnIdentifier + " is not a numerical value for Record");
        }
        String unit = get(unitColumnIdentifier);
        if (isNullOrEmpty(unit)) {
            warning(unitColumnIdentifier + " is empty for Record");
        }
        return getUcumQuantity(value, unit);
    }

    /**
     * @param observationID
     * @param dizID
     * @return
     */
    public static List<Identifier> getIdentifier(String observationID, String dizID) {
        CodeableConcept obiCode = createCodeableConcept("http://terminology.hl7.org/CodeSystem/v2-0203", "OBI");
        Reference assigner = new Reference()
                .setIdentifier(
                        new Identifier()
                                .setValue(dizID)
                                .setSystem("https://www.medizininformatik-initiative.de/fhir/core/NamingSystem/org-identifier"));
        return Arrays.asList(
                new Identifier()
                        .setValue(observationID)
                        .setSystem("https://" + dizID + ".de/befund")
                        .setAssigner(assigner)
                        .setType(obiCode));
    }

    /**
     * @return the always same category for laboratory observations
     */
    private static List<CodeableConcept> getLaborytoryObservationFixedCategory() {
        Coding laboratoryCategory1 = new Coding()
                .setSystem("http://terminology.hl7.org/CodeSystem/observation-category")
                .setCode("laboratory")
                .setDisplay("Laboratory");
        Coding laboratoryCatgeory2 = new Coding()
                .setSystem("http://loinc.org")
                .setCode("26436-6")
                .setDisplay("Laboratory studies (set)");
        CodeableConcept laboratoryCategories = new CodeableConcept()
                .addCoding(laboratoryCategory1)
                .addCoding(laboratoryCatgeory2);
        return ImmutableList.of(laboratoryCategories);
    }

}
