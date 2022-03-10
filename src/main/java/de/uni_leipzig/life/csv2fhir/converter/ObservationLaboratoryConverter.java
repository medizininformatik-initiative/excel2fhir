package de.uni_leipzig.life.csv2fhir.converter;

import static com.google.common.base.Strings.isNullOrEmpty;
import static de.uni_leipzig.life.csv2fhir.Converter.EmptyRecordValueErrorLevel.IGNORE;
import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Laborbefund;
import static de.uni_leipzig.life.csv2fhir.converterFactory.ObservationLaboratoryConverterFactory.ObservationLaboratory_Columns.Einheit;
import static de.uni_leipzig.life.csv2fhir.converterFactory.ObservationLaboratoryConverterFactory.ObservationLaboratory_Columns.LOINC;
import static de.uni_leipzig.life.csv2fhir.converterFactory.ObservationLaboratoryConverterFactory.ObservationLaboratory_Columns.Messwert;
import static de.uni_leipzig.life.csv2fhir.converterFactory.ObservationLaboratoryConverterFactory.ObservationLaboratory_Columns.Parameter;
import static de.uni_leipzig.life.csv2fhir.converterFactory.ObservationLaboratoryConverterFactory.ObservationLaboratory_Columns.Zeitstempel_Abnahme;
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

import de.uni_leipzig.imise.FHIRValidator;
import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterResult;
import de.uni_leipzig.life.csv2fhir.converterFactory.ObservationLaboratoryConverterFactory.ObservationLaboratory_Columns;
import de.uni_leipzig.life.csv2fhir.utils.DateUtil;

public class ObservationLaboratoryConverter extends Converter {

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
    public List<Resource> convert() throws Exception {
        Observation observation = new Observation();
        int nextId = result.getNextId(Laborbefund, Observation.class);
        String id = getEncounterId() + "-OL-" + nextId;
        observation.setId(id);
        observation.setMeta(new Meta().addProfile(PROFILE));
        observation.setStatus(FINAL);
        observation.setSubject(getPatientReference()); // if null then observation is invalid
        observation.setEncounter(getEncounterReference()); // if null then observation is invalid
        observation.setEffective(parseObservationTimestamp(this, Zeitstempel_Abnahme));
        observation.setCode(parseObservationCode());
        //set value or value absent reason
        Quantity observationValue = parseObservationValue(this, Messwert, Einheit);
        setValueOrAbsentReason(observation, observationValue);
        observation.setIdentifier(getIdentifier(id, getDIZId()));
        observation.setCategory(LABORYTORY_OBSERVATION_FIXED_CATEGORY);
        return Collections.singletonList(observation);
    }

    @Override
    protected Enum<?> getPatientIDColumnIdentifier() {
        return Laborbefund.getPIDColumnIdentifier();
    }

    /**
     * @return
     * @throws Exception
     */
    private CodeableConcept parseObservationCode() throws Exception {
        String loincCodeSystem = "http://loinc.org";
        Coding loincCoding = createCoding(loincCodeSystem, LOINC, IGNORE);
        if (loincCoding == null) {
            warning(LOINC + " empty for Record -> creating empty code");
            loincCoding = createCoding(loincCodeSystem, null);
        }
        return createCodeableConcept(loincCoding, Parameter);
    }

    /**
     * @param converter
     * @param timestampColumnIdentifier
     * @return
     * @throws Exception
     */
    public static DateTimeType parseObservationTimestamp(Converter converter, Enum<?> timestampColumnIdentifier) throws Exception {
        String timestamp = converter.get(timestampColumnIdentifier);
        if (!isNullOrEmpty(timestamp)) {
            try {
                return DateUtil.parseDateTimeType(timestamp);
            } catch (Exception eYear) {
                converter.error("Can not parse " + timestampColumnIdentifier + " for Record");
                return null;
            }
        }
        converter.error(timestampColumnIdentifier + " empty for Record");
        return null;
    }

    /**
     * @param converter
     * @param valueColumnIdentifier
     * @param unitColumnIdentifier
     * @return
     * @throws Exception
     */
    public static Quantity parseObservationValue(Converter converter, Enum<?> valueColumnIdentifier, Enum<?> unitColumnIdentifier) throws Exception {
        BigDecimal value;
        try {
            String valueString = converter.get(valueColumnIdentifier);
            value = parseDecimal(valueString);
        } catch (Exception e) {
            converter.error(valueColumnIdentifier + " is not a numerical value for Record");
            return null;
        }
        String unit = converter.get(unitColumnIdentifier);
        if (isNullOrEmpty(unit)) {
            converter.error(unitColumnIdentifier + " is empty for Record");
            return null;
        }
        return getUcumQuantity(value, unit);
    }

    /**
     * @param observation
     * @param observationValue
     */
    public static void setValueOrAbsentReason(Observation observation, Quantity observationValue) {
        if (observationValue != null) {
            observation.setValue(observationValue);
        } else {
            observation.setDataAbsentReason(createUnknownDataAbsentReason());
        }
    }

    /**
     * @return a {@link CodeableConcept} that represents a valid unknown data
     *         absent reason for {@link Observation} values.
     */
    public static CodeableConcept createUnknownDataAbsentReason() {
        //needed to be a valid in KDS validating
        //copied from examples of extracted validation package de.medizininformatikinitiative.kerndatensatz.laborbefund-1.0.6.tgz
        //    "dataAbsentReason":
        //    {
        //        "coding": [
        //          {
        //            "system": "http://terminology.hl7.org/CodeSystem/data-absent-reason",
        //            "code": "unknown"
        //          }
        //       ]
        //    },
        return createCodeableConcept("http://terminology.hl7.org/CodeSystem/data-absent-reason", "unknown");
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
    public static List<CodeableConcept> getLaborytoryObservationFixedCategory() {
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

    @Override
    protected Enum<?> getMainEncounterNumberColumnIdentifier() {
        return ObservationLaboratory_Columns.Versorgungsfall_Nr;
    }

}
