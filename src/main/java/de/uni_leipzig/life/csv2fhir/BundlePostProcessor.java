package de.uni_leipzig.life.csv2fhir;

import static de.uni_leipzig.life.csv2fhir.BundleFunctions.getResource;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Encounter.DiagnosisComponent;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

import com.google.common.base.Objects;

import de.uni_leipzig.life.csv2fhir.converter.VersorgungsfallConverter;

/**
 * Post-processing of a bundle.
 *
 * @author AXS (23.11.2021)
 */
public class BundlePostProcessor {

    /**
     * Adds all sub encounters at least one diagnose from the super encounter if
     * exists.
     *
     * @param bundle
     */
    public static void convert(Bundle bundle) {
        List<BundleEntryComponent> invalidResources = addMissingDiagnosesToEncounters(bundle);
    }

    /**
     * @param bundle
     * @return
     */
    private static List<BundleEntryComponent> addMissingDiagnosesToEncounters(Bundle bundle) {
        List<BundleEntryComponent> notValidableResources = new ArrayList<>();
        nextEntry: for (BundleEntryComponent entry : bundle.getEntry()) {
            Resource resource = entry.getResource();
            if (resource instanceof Encounter) {
                Encounter encounter = (Encounter) resource;
                List<DiagnosisComponent> diagnoses = encounter.getDiagnosis();
                //There is already a diagnosis reference in this encounter -> nothing to do
                if (diagnoses.isEmpty()) {
                    //Get the Encounter of which this Encounter is a part
                    Reference partOfReference = encounter.getPartOf();
                    String superEncounterID = partOfReference.getReference();
                    //encounter found from which we can copy a diagnosis reference
                    if (superEncounterID != null) {
                        Encounter superEncounter = getResource(bundle, Encounter.class, superEncounterID);
                        if (superEncounter != null) {
                            diagnoses = superEncounter.getDiagnosis();
                            //Also the parent Encounter has no diagnosis -> nothing to do
                            if (!diagnoses.isEmpty()) {
                                //a reference to the first diagnose which has a coded diagnose use from the follwing
                                //iterable will be added to the child encuonter
                                Iterable<String> diagnosisUseCodesInAddingOrder = VersorgungsfallConverter.diagnosisRoleKeyMapper.getValuesInAddingOrder();
                                for (String preferedDiagnosisUseCode : diagnosisUseCodesInAddingOrder) {
                                    for (DiagnosisComponent diagnosis : diagnoses) {
                                        CodeableConcept use = diagnosis.getUse();
                                        if (use != null) {
                                            List<Coding> codings = use.getCoding();
                                            for (Coding coding : codings) {
                                                String code = coding.getCode();
                                                if (Objects.equal(preferedDiagnosisUseCode, code)) {
                                                    encounter.addDiagnosis(diagnosis);
                                                    continue nextEntry;
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
                diagnoses = encounter.getDiagnosis();
                if (diagnoses.isEmpty()) { //Encounter still has no diagnosis -> should be removed
                    notValidableResources.add(entry);
                }
            }
        }
        return notValidableResources;
    }

}
