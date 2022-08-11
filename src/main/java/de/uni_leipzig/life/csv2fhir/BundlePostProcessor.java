package de.uni_leipzig.life.csv2fhir;

import static de.uni_leipzig.life.csv2fhir.BundleFunctions.getResource;
import static de.uni_leipzig.life.csv2fhir.Converter.DATA_ABSENT_REASON_UNKNOWN;
import static de.uni_leipzig.life.csv2fhir.ConverterOptions.BooleanOption.ADD_MISSING_CLASS_FROM_SUPER_ENCOUNTER;
import static de.uni_leipzig.life.csv2fhir.ConverterOptions.BooleanOption.ADD_MISSING_DIAGNOSES_FROM_SUPER_ENCOUNTER;

import java.util.List;

import org.apache.commons.lang3.tuple.MutablePair;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Encounter.DiagnosisComponent;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;

import com.google.common.base.Objects;

import de.uni_leipzig.life.csv2fhir.converter.EncounterLevel1Converter;

/**
 * Post-processing of a bundle.
 *
 * @author AXS (23.11.2021)
 */
public class BundlePostProcessor {

    /** The processed bundle */
    private final Bundle bundle;

    /** The options for the conversion */
    private final ConverterOptions converterOptions;

    /**
     * @param bundle the processed bundle
     * @param converterOptions The options for the conversion
     */
    private BundlePostProcessor(Bundle bundle, ConverterOptions converterOptions) {
        this.bundle = bundle;
        this.converterOptions = converterOptions;
    }

    /**
     * Adds all sub encounters at least one diagnose from the super encounter if
     * exists.
     *
     * @param bundle the processed bundle
     * @param converterOptions The options for the conversion
     */
    public static void convert(Bundle bundle, ConverterOptions converterOptions) {
        BundlePostProcessor postProcessor = new BundlePostProcessor(bundle, converterOptions);
        postProcessor.addMissingDiagnosesAndClassToLevel2Encounters();
    }

    /**
     *
     */
    private void addMissingDiagnosesAndClassToLevel2Encounters() {
        for (BundleEntryComponent entry : bundle.getEntry()) {
            Resource resource = entry.getResource();
            if (resource instanceof Encounter) {
                Encounter encounter = (Encounter) resource;
                addMissingDiagnosesFromSuperEncounter(encounter);
                addMissingClassCodingFromSuperencounter(encounter);
            }
        }
    }

    /**
     * Add missing diagnoses from sub encounter to super encounter
     *
     * @param encounter
     */
    private void addMissingDiagnosesFromSuperEncounter(Encounter encounter) {
        if (converterOptions.is(ADD_MISSING_DIAGNOSES_FROM_SUPER_ENCOUNTER)) {
            List<DiagnosisComponent> diagnoses = encounter.getDiagnosis();
            if (diagnoses.isEmpty()) {
                Encounter superEncounter = getSuperEncounter(encounter);
                if (superEncounter != null) {
                    //copy one diagnosis from super encounter to sub encounter
                    diagnoses = superEncounter.getDiagnosis();
                    //Also the parent Encounter has no diagnosis -> nothing to do
                    if (!diagnoses.isEmpty()) {
                        //a reference to the first diagnose which has a coded diagnose use from the follwing
                        //iterable will be added to the child encuonter
                        Iterable<String> diagnosisUseCodesInAddingOrder = EncounterLevel1Converter.DIAGNOSIS_ROLE_RESOURCES.getValuesInAddingOrder();
                        for (String preferedDiagnosisUseCode : diagnosisUseCodesInAddingOrder) {
                            for (DiagnosisComponent diagnosis : diagnoses) {
                                CodeableConcept use = diagnosis.getUse();
                                if (use != null) {
                                    List<Coding> codings = use.getCoding();
                                    for (Coding coding : codings) {
                                        String code = coding.getCode();
                                        if (Objects.equal(preferedDiagnosisUseCode, code)) {
                                            encounter.addDiagnosis(diagnosis);
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                        //no diagnosis has an valid use -> simply add the first to the sub encounter
                        DiagnosisComponent diagnosisComponent = diagnoses.get(0); //must exists because we check empty above
                        encounter.addDiagnosis(diagnosisComponent);
                    }
                }
            }
        }
        //the Encounter still has no diagnosis -> add "unknown" Data Absent Reason
        List<DiagnosisComponent> diagnoses = encounter.getDiagnosis();
        if (diagnoses.isEmpty()) {
            DiagnosisComponent diagnosisComponent = new DiagnosisComponent();
            Reference condition = diagnosisComponent.getCondition();
            StringType referenceElement_ = condition.getReferenceElement_();
            referenceElement_.addExtension(DATA_ABSENT_REASON_UNKNOWN);
            diagnoses.add(diagnosisComponent);
            encounter.setDiagnosis(diagnoses);
        }

    }

    /**
     * Add encounter class coding from super encounter or add data absent reason
     * if not exists.
     *
     * @param encounter
     */
    private void addMissingClassCodingFromSuperencounter(Encounter encounter) {
        if (converterOptions.is(ADD_MISSING_CLASS_FROM_SUPER_ENCOUNTER)) {
            Coding class_ = encounter.getClass_();
            if (class_.isEmpty()) {
                //copy encounter class from super encounter to sub encounter
                Encounter superEncounter = getSuperEncounter(encounter);
                class_ = superEncounter.getClass_();
                encounter.setClass_(class_);
            }
        }
        //the Encounter still has no class coding -> add "unknown" Data Absent Reason
        Coding class_ = encounter.getClass_();
        if (class_.isEmpty()) {
            Coding coding = new Coding();
            coding.addExtension(DATA_ABSENT_REASON_UNKNOWN);
            encounter.setClass_(coding);
        }

    }

    /**
     * Caches the super encounter for an encounter. The key (first element) is
     * the encounter and the value (second element) is the
     */
    private final MutablePair<Encounter, Encounter> lastEncounterToSuoerEncounter = MutablePair.of(null, null);

    /**
     * @param encounter
     * @param bundle
     * @return
     */
    private Encounter getSuperEncounter(Encounter encounter) {
        if (encounter == lastEncounterToSuoerEncounter.left) {
            return lastEncounterToSuoerEncounter.right;
        }
        //Get the Encounter of which this Encounter is a part
        Encounter superEncounter = null;
        Reference partOfReference = encounter.getPartOf();
        if (partOfReference != null) {
            String superEncounterID = partOfReference.getReference();
            //encounter found from which we can copy a diagnosis reference
            if (superEncounterID != null) {
                superEncounter = getResource(bundle, Encounter.class, superEncounterID);
            }
        }
        lastEncounterToSuoerEncounter.left = encounter;
        lastEncounterToSuoerEncounter.right = superEncounter;
        return null;
    }

}
