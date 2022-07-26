package de.uni_leipzig.life.csv2fhir.converter;

import static de.uni_leipzig.life.csv2fhir.ConverterOptions.IntOption.START_ID_OBSERVATION_VITAL_SIGNS;
import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Klinische_Dokumentation;
import static de.uni_leipzig.life.csv2fhir.converter.ObservationVitalSignsConverter.ObservationVitalSigns_Columns.Bezeichner;
import static de.uni_leipzig.life.csv2fhir.converter.ObservationVitalSignsConverter.ObservationVitalSigns_Columns.Einheit;
import static de.uni_leipzig.life.csv2fhir.converter.ObservationVitalSignsConverter.ObservationVitalSigns_Columns.LOINC;
import static de.uni_leipzig.life.csv2fhir.converter.ObservationVitalSignsConverter.ObservationVitalSigns_Columns.Wert;
import static de.uni_leipzig.life.csv2fhir.converter.ObservationVitalSignsConverter.ObservationVitalSigns_Columns.Zeitstempel;
import static org.hl7.fhir.r4.model.Observation.ObservationStatus.FINAL;

import java.util.Collections;
import java.util.List;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

import com.google.common.collect.ImmutableList;

import de.uni_leipzig.imise.validate.FHIRValidator;
import de.uni_leipzig.life.csv2fhir.ConverterResult;
import de.uni_leipzig.life.csv2fhir.TableColumnIdentifier;

/**
 * @author jheuschkel (19.10.2020), AXS (05.11.2021)
 */
public class ObservationVitalSignsConverter extends ObservationLaboratoryConverter {

    /**
     *
     */
    public static enum ObservationVitalSigns_Columns implements TableColumnIdentifier {
        Bezeichner,
        LOINC,
        Wert,
        Einheit,
        Zeitstempel
    }

    /**
     * Category for all Observation of this type.<br>
     * Unfortunately, observations with this actually correct category are not
     * recognized as valid, which is why the vital sign observations continue to
     * be assigned the category of laboratory observations.
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
    protected List<Resource> convertInternal() throws Exception {
        Observation observation = new Observation();
        int nextId = result.getNextId(Klinische_Dokumentation, Observation.class, START_ID_OBSERVATION_VITAL_SIGNS);
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
        observation.setCategory(LABORYTORY_OBSERVATION_FIXED_CATEGORY); //TODO: add the correct category if validator can accept it
        //String resourceAsJson = OutputFileType.JSON.getParser().setPrettyPrint(true).encodeResourceToString(observation); // for debug
        return Collections.singletonList(observation);
    }

    /**
     * TODO: Change the ObservationVital Category if the ICU Package can be used
     * This here is the correct Category if all packages could be used to
     * validate the resources. But until now (08.07.22) there is no valid ICU
     * Package, which should contain the Codes from file
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

}
