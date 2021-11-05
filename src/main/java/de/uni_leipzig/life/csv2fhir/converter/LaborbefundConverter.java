package de.uni_leipzig.life.csv2fhir.converter;

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

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.Ucum;
import de.uni_leipzig.life.csv2fhir.utils.DateUtil;
import de.uni_leipzig.life.csv2fhir.utils.DecimalUtil;

public class LaborbefundConverter extends Converter {

    /**  */
    String PROFILE = "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab";
    // https://simplifier.net/medizininformatikinitiative-modullabor/observationlab

    /**  */
    static int n = 10000;

    /**
     * @param record
     * @throws Exception
     */
    public LaborbefundConverter(CSVRecord record) throws Exception {
        super(record);
    }

    @Override
    public List<Resource> convert() throws Exception {
        // generierte Labornummer
        String id = getEncounterId() + "-O" + n++;

        Observation observation = new Observation();
        observation.setId(id);
        observation.setMeta(new Meta().addProfile(PROFILE));
        observation.setStatus(Observation.ObservationStatus.FINAL);
        observation.setCode(parseObservationCode());
        observation.setSubject(getPatientReference());
        observation.setEncounter(getEncounterReference());
        observation.setEffective(parseObservationTimestamp());
        observation.setValue(parseObservationValue());

        // geratenes DIZ Kürzel
        String diz = getDIZId();

        CodeableConcept d = new CodeableConcept();
        d.addCoding().setCode("OBI").setSystem("http://terminology.hl7.org/CodeSystem/v2-0203");
        Reference r = new Reference().setIdentifier(new Identifier().setValue(diz).setSystem(
                "https://www.medizininformatik-initiative.de/fhir/core/NamingSystem/org-identifier"));
        observation.setIdentifier(Arrays.asList(new Identifier().setValue(id).setSystem("https://" + diz + ".de/befund")
                .setAssigner(r).setType(d)));

        CodeableConcept c = new CodeableConcept();
        c.addCoding().setCode("laboratory").setSystem("http://terminology.hl7.org/CodeSystem/observation-category").setDisplay(
                "Laboratory");
        c.addCoding().setCode("26436-6").setSystem("http://loinc.org").setDisplay("Laboratory studies");
        observation.setCategory(Arrays.asList(c));
        return Collections.singletonList(observation);
    }

    /**
     * @return
     * @throws Exception
     */
    private CodeableConcept parseObservationCode() throws Exception {
        String code = record.get("LOINC");
        if (code != null) {
            return new CodeableConcept().addCoding(new Coding().setSystem("http://loinc.org").setCode(code)).setText(record.get(
                    "Parameter"));
        }
        warning("LOINC empty for Record; creating empty code");
        return new CodeableConcept().addCoding(new Coding().setSystem("http://loinc.org")).setText(record.get("Parameter"));
        //			return null;
    }

    /**
     * @return
     * @throws Exception
     */
    private DateTimeType parseObservationTimestamp() throws Exception {
        String timestamp = record.get("Zeitstempel (Abnahme)");
        if (timestamp != null) {
            try {
                return DateUtil.parseDateTimeType(timestamp);
            } catch (Exception eYear) {
                error("Can not parse Zeitstempel for Record");
                return null;
            }
        }
        error("Zeitstempel (Abnahme) empty for Record");
        return null;
    }

    /**
     * @return
     * @throws Exception
     */
    private Quantity parseObservationValue() throws Exception {
        BigDecimal messwert;
        try {
            messwert = DecimalUtil.parseDecimal(record.get("Messwert"));
        } catch (Exception e) {
            error("Messwert is not a numerical value for Record");
            return null;
        }
        String unit = record.get("Einheit");
        if (unit == null || unit.isEmpty()) {
            warning("Einheit is empty for Record");
            return null;
        }

        String ucum, synonym;
        if (Ucum.isUcum(unit)) {
            ucum = unit;
            synonym = Ucum.ucum2human(unit);
        } else {
            ucum = Ucum.human2ucum(unit);
            synonym = unit;
        }
        if (ucum.isEmpty()) {
            return new Quantity().setValue(messwert).setUnit(synonym);
        }
        return new Quantity().setValue(messwert).setSystem("http://unitsofmeasure.org").setCode(ucum).setUnit(synonym);
    }
}
