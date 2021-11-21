package de.uni_leipzig.life.csv2fhir.converter;

import static de.uni_leipzig.life.csv2fhir.Converter.EmptyRecordValueErrorLevel.IGNORE;
import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Laborbefund;
import static de.uni_leipzig.life.csv2fhir.converterFactory.LaborbefundConverterFactory.NeededColumns.Einheit;
import static de.uni_leipzig.life.csv2fhir.converterFactory.LaborbefundConverterFactory.NeededColumns.LOINC;
import static de.uni_leipzig.life.csv2fhir.converterFactory.LaborbefundConverterFactory.NeededColumns.Messwert;
import static de.uni_leipzig.life.csv2fhir.converterFactory.LaborbefundConverterFactory.NeededColumns.Parameter;
import static de.uni_leipzig.life.csv2fhir.converterFactory.LaborbefundConverterFactory.NeededColumns.Zeitstempel_Abnahme;

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

        CodeableConcept obiCode = createCodeableConcept("http://terminology.hl7.org/CodeSystem/v2-0203", "OBI");
        Reference r = new Reference().setIdentifier(new Identifier().setValue(diz).setSystem(
                "https://www.medizininformatik-initiative.de/fhir/core/NamingSystem/org-identifier"));
        observation.setIdentifier(Arrays.asList(new Identifier().setValue(id).setSystem("https://" + diz + ".de/befund")
                .setAssigner(r).setType(obiCode)));

        CodeableConcept laboratoryCode = new CodeableConcept();
        Coding laboratoryCoding1 = createCoding("http://terminology.hl7.org/CodeSystem/observation-category", "laboratory", "Laboratory");
        Coding laboratoryCoding2 = createCoding("http://loinc.org", "26436-6", "Laboratory studies");
        laboratoryCode.addCoding(laboratoryCoding1);
        laboratoryCode.addCoding(laboratoryCoding2);

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
        String timestamp = record.get(Zeitstempel_Abnahme);
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
            messwert = DecimalUtil.parseDecimal(record.get(Messwert));
        } catch (Exception e) {
            error("Messwert is not a numerical value for Record");
            return null;
        }
        String unit = record.get(Einheit);
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
            warning("ucum empty, check \"" + unit + "\"");
            throw new Exception("Ignore Ressource");
            //            return new Quantity().setValue(messwert).setUnit(synonym);
        }
        return new Quantity().setValue(messwert).setSystem("http://unitsofmeasure.org").setCode(ucum).setUnit(synonym);
    }
}
