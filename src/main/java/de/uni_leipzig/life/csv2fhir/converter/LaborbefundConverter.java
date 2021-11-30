package de.uni_leipzig.life.csv2fhir.converter;

import static com.google.common.base.Strings.isNullOrEmpty;
import static de.uni_leipzig.life.csv2fhir.Converter.EmptyRecordValueErrorLevel.IGNORE;
import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Laborbefund;
import static de.uni_leipzig.life.csv2fhir.Ucum.human2ucum;
import static de.uni_leipzig.life.csv2fhir.Ucum.ucum2human;
import static de.uni_leipzig.life.csv2fhir.converterFactory.LaborbefundConverterFactory.Laborbefund_Columns.Einheit;
import static de.uni_leipzig.life.csv2fhir.converterFactory.LaborbefundConverterFactory.Laborbefund_Columns.LOINC;
import static de.uni_leipzig.life.csv2fhir.converterFactory.LaborbefundConverterFactory.Laborbefund_Columns.Messwert;
import static de.uni_leipzig.life.csv2fhir.converterFactory.LaborbefundConverterFactory.Laborbefund_Columns.Parameter;
import static de.uni_leipzig.life.csv2fhir.converterFactory.LaborbefundConverterFactory.Laborbefund_Columns.Zeitstempel_Abnahme;
import static de.uni_leipzig.life.csv2fhir.utils.DecimalUtil.parseDecimal;

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

import de.uni_leipzig.imise.FHIRValidator;
import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterResult;
import de.uni_leipzig.life.csv2fhir.Ucum;
import de.uni_leipzig.life.csv2fhir.utils.DateUtil;

public class LaborbefundConverter extends Converter {

    /** Simple counter to generate unique identifier */
    static int n = 1;

    /**  */
    String PROFILE = "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab";
    // https://simplifier.net/medizininformatikinitiative-modullabor/observationlab

    /**
     * @param record
     * @param result
     * @param validator
     * @throws Exception
     */
    public LaborbefundConverter(CSVRecord record, ConverterResult result, FHIRValidator validator) throws Exception {
        super(record, result, validator);
    }

    /**
     * Resets the static index counter
     */
    public static void reset() {
        n = 1;
    }

    @Override
    public List<Resource> convert() throws Exception {
        // generierte Labornummer
        String id = getEncounterId() + "-OL-" + n++;

        Observation observation = new Observation();
        observation.setId(id);
        observation.setMeta(new Meta().addProfile(PROFILE));
        observation.setStatus(Observation.ObservationStatus.FINAL);
        observation.setCode(parseObservationCode());
        observation.setSubject(getPatientReference());
        observation.setEncounter(getEncounterReference());
        observation.setEffective(parseObservationTimestamp());
        //set value or value absent reason
        Quantity observationValue = parseObservationValue();
        if (observationValue != null) {
            observation.setValue(observationValue);
        } else {
            observation.setDataAbsentReason(createUnknownDataAbsentReason());
        }

        // geratenes DIZ Kürzel
        String diz = getDIZId();

        CodeableConcept obiCode = createCodeableConcept("http://terminology.hl7.org/CodeSystem/v2-0203", "OBI");
        Reference assigner = new Reference()
                .setIdentifier(
                        new Identifier()
                                .setValue(diz)
                                .setSystem("https://www.medizininformatik-initiative.de/fhir/core/NamingSystem/org-identifier"));
        observation.setIdentifier(Arrays.asList(
                new Identifier()
                        .setValue(id)
                        .setSystem("https://" + diz + ".de/befund")
                        .setAssigner(assigner)
                        .setType(obiCode)));
        Coding laboratoryCoding1 = new Coding()
                .setSystem("http://terminology.hl7.org/CodeSystem/observation-category")
                .setCode("laboratory")
                .setDisplay("Laboratory");
        Coding laboratoryCoding2 = new Coding()
                .setSystem("http://loinc.org")
                .setCode("26436-6")
                .setDisplay("Laboratory studies (set)");
        CodeableConcept laboratoryCode = new CodeableConcept()
                .addCoding(laboratoryCoding1)
                .addCoding(laboratoryCoding2);

        observation.setCategory(Arrays.asList(laboratoryCode));
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
     * @return
     * @throws Exception
     */
    private DateTimeType parseObservationTimestamp() throws Exception {
        String timestamp = get(Zeitstempel_Abnahme);
        if (!isNullOrEmpty(timestamp)) {
            try {
                return DateUtil.parseDateTimeType(timestamp);
            } catch (Exception eYear) {
                error("Can not parse " + Zeitstempel_Abnahme + " for Record");
                return null;
            }
        }
        error(Zeitstempel_Abnahme + " empty for Record");
        return null;
    }

    /**
     * @return
     * @throws Exception
     */
    private Quantity parseObservationValue() throws Exception {
        BigDecimal messwert;
        try {
            messwert = parseDecimal(get(Messwert));
        } catch (Exception e) {
            error(Messwert + " is not a numerical value for Record");
            return null;
        }
        String unit = get(Einheit);
        if (unit == null || unit.isEmpty()) {
            warning(Einheit + " is empty for Record");
            return null;
        }

        boolean isUcum = Ucum.isUcum(unit);
        String ucum = isUcum ? unit : human2ucum(unit);
        String synonym = isUcum ? ucum2human(unit) : unit;

        if (ucum.isEmpty()) {
            warning("ucum empty, check \"" + unit + "\"");
            throw new Exception("Ignore Ressource");
        }
        return new Quantity()
                .setSystem("http://unitsofmeasure.org")
                .setCode(ucum)
                .setValue(messwert)
                .setUnit(synonym);
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

}
