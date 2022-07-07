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
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Resource;

import de.uni_leipzig.imise.FHIRValidator;
import de.uni_leipzig.life.csv2fhir.ConverterResult;
import de.uni_leipzig.life.csv2fhir.TableColumnIdentifier;
import de.uni_leipzig.life.csv2fhir.converterFactory.ObservationVitalSignsConverterFactory.ObservationVitalSigns_Columns;

public class ObservationVitalSignsConverter extends ObservationLaboratoryConverter {

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
        observation.setCategory(LABORYTORY_OBSERVATION_FIXED_CATEGORY);
        //        String resourceAsJson = OutputFileType.JSON.getParser().setPrettyPrint(true).encodeResourceToString(observation);
        return Collections.singletonList(observation);
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
