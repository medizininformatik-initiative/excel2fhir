package de.uni_leipzig.life.csv2fhir.converter;

import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Klinische_Dokumentation;
import static de.uni_leipzig.life.csv2fhir.converterFactory.ObservationVitalSignsConverterFactory.ObservationVitalSigns_Columns.Bezeichner;
import static de.uni_leipzig.life.csv2fhir.converterFactory.ObservationVitalSignsConverterFactory.ObservationVitalSigns_Columns.Einheit;
import static de.uni_leipzig.life.csv2fhir.converterFactory.ObservationVitalSignsConverterFactory.ObservationVitalSigns_Columns.LOINC;
import static de.uni_leipzig.life.csv2fhir.converterFactory.ObservationVitalSignsConverterFactory.ObservationVitalSigns_Columns.Wert;
import static de.uni_leipzig.life.csv2fhir.converterFactory.ObservationVitalSignsConverterFactory.ObservationVitalSigns_Columns.Zeitstempel;
import static org.hl7.fhir.r4.model.Observation.ObservationStatus.FINAL;

import java.util.Collections;
import java.util.List;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Resource;

import com.google.common.collect.ImmutableList;

import de.uni_leipzig.imise.FHIRValidator;
import de.uni_leipzig.life.csv2fhir.ConverterResult;
import de.uni_leipzig.life.csv2fhir.TableColumnIdentifier;
import de.uni_leipzig.life.csv2fhir.converterFactory.ObservationVitalSignsConverterFactory.ObservationVitalSigns_Columns;

public class ObservationVitalSignsConverter extends ObservationLaboratoryConverter {

    /**
     * Category for all Observation of this type. These Observations have no
     * proper code system (like laboratory observations) so we set here data
     * absent reasons.
     */
    private static List<CodeableConcept> VITAL_SIGNS_OBSERVATION_FIXED_CATEGORY = getVitalObservationFixedCategory();

    /**
     * @param record
     * @param result
     * @param validator
     * @throws Exception
     */
    public ObservationVitalSignsConverter(CSVRecord record, ConverterResult result, FHIRValidator validator) throws Exception {
        super(record, result, validator);
    }

    @Override
    public List<Resource> convert() throws Exception {
        Observation observation = new Observation();
        int nextId = result.getNextId(Klinische_Dokumentation, Observation.class);
        String id = getEncounterId() + "-OV-" + nextId;
        observation.setId(id);
        observation.setMeta(new Meta().addProfile(PROFILE));
        observation.setStatus(FINAL);
        observation.setSubject(getPatientReference()); // if null then observation is invalid
        observation.setEncounter(getEncounterReference()); // if null then observation is invalid
        setEffective(observation, this, Zeitstempel);
        observation.setCode(parseLoincCodeableConcept(LOINC, Bezeichner));
        observation.setValue(parseObservationValue(Wert, Einheit));
        observation.setIdentifier(getIdentifier(id, getDIZId()));
        observation.setCategory(getVitalObservationFixedCategory());
        //String resourceAsJson = OutputFileType.JSON.getParser().setPrettyPrint(true).encodeResourceToString(observation); // for debug
        return Collections.singletonList(observation);
    }

    /**
     * Codes from file
     * hl7.fhir.r4.core-4.0.1/package/ValueSet-observation-vitalsignresult.json
     * are the same from https://loinc.org/85353-1/
     *
     * @return the always same category for vital signs observations
     */
    private static List<CodeableConcept> getVitalObservationFixedCategory() {
        Coding vitalCategory1 = new Coding()
                .setSystem("http://terminology.hl7.org/CodeSystem/observation-category")
                .setCode("vital-signs")
                .setDisplay("Vital Signs");
        Coding vitalCatgeory2 = new Coding()
                .setSystem("http://loinc.org")
                .setCode("85353-1")
                .setDisplay("Vital signs, weight, height, head circumference, oxygen saturation and BMI panel");
        CodeableConcept vitalCategories = new CodeableConcept()
                .addCoding(vitalCategory1)
                .addCoding(vitalCatgeory2);
        return ImmutableList.of(vitalCategories);
    }

    @Override
    protected Enum<?> getPatientIDColumnIdentifier() {
        return Klinische_Dokumentation.getPIDColumnIdentifier();
    }

    @Override
    protected TableColumnIdentifier getMainEncounterNumberColumnIdentifier() {
        return ObservationVitalSigns_Columns.Versorgungsfall_Nr;
    }

}
