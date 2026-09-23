package de.uni_leipzig.life.csv2fhir;

import static de.uni_leipzig.life.csv2fhir.BundleFunctions.getResource;
import static de.uni_leipzig.life.csv2fhir.ConverterOptions.BooleanOption.ADD_MISSING_DIAGNOSES_FROM_SUPER_ENCOUNTER;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Encounter.DiagnosisComponent;
import org.hl7.fhir.r4.model.Resource;

import com.google.common.base.Objects;

import de.uni_leipzig.life.csv2fhir.converter.EncounterConverter;
import de.uni_leipzig.life.csv2fhir.converter.EncounterConverter.EncounterLevel1;

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
     * @param bundle           the processed bundle
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
     * @param bundle           the processed bundle
     * @param converterOptions The options for the conversion
     */
    public static void convert(Bundle bundle, ConverterOptions converterOptions) {
        BundlePostProcessor postProcessor = new BundlePostProcessor(bundle, converterOptions);
        postProcessor.addMissingDiagnosesToSubEncounters();
    }

    /**
     *
     */
    private void addMissingDiagnosesToSubEncounters() {
        for (BundleEntryComponent entry : bundle.getEntry()) {
            Resource resource = entry.getResource();
            if (resource instanceof Encounter) {
                if (!(resource instanceof EncounterLevel1)) {
                    Encounter encounter = (Encounter) resource;
                    addMissingDiagnosesFromSuperEncounter(encounter);
                }
            }
        }
    }

    /**
     * Inherit a missing diagnosis from the parent encounter when enabled.
     *
     * @param encounter
     */
    private void addMissingDiagnosesFromSuperEncounter(Encounter encounter) {
        addMissingDiagnosesFromSuperEncounter(encounter, new HashSet<>());
    }

    private void addMissingDiagnosesFromSuperEncounter(Encounter encounter, Set<Encounter> visited) {
        if (!visited.add(encounter)) return;
        if (converterOptions.is(ADD_MISSING_DIAGNOSES_FROM_SUPER_ENCOUNTER)) {
            List<DiagnosisComponent> diagnoses = encounter.getDiagnosis();
            if (diagnoses.isEmpty()) {
                Encounter superEncounter = getSuperEncounter(encounter);
                if (superEncounter != null) {
                    // Resolve ancestors first, independently of the bundle entry order.
                    addMissingDiagnosesFromSuperEncounter(superEncounter, visited);
                    // copy one diagnosis from super encounter to sub encounter
                    diagnoses = superEncounter.getDiagnosis();
                    // Also the parent Encounter has no diagnosis -> nothing to do
                    if (!diagnoses.isEmpty()) {
                        // a reference to the first diagnose which has a coded diagnose use from the
                        // follwing
                        // iterable will be added to the child encuonter
                        Iterable<String> diagnosisUseCodesInAddingOrder = EncounterConverter.DIAGNOSIS_ROLE_RESOURCES
                                .getValuesInAddingOrder();
                        for (String preferedDiagnosisUseCode : diagnosisUseCodesInAddingOrder) {
                            for (DiagnosisComponent diagnosis : diagnoses) {
                                CodeableConcept use = diagnosis.getUse();
                                if (use != null) {
                                    List<Coding> codings = use.getCoding();
                                    for (Coding coding : codings) {
                                        String code = coding.getCode();
                                        if (Objects.equal(preferedDiagnosisUseCode, code)) {
                                            encounter.addDiagnosis(diagnosis.copy());
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                        // no diagnosis has an valid use -> simply add the first to the sub encounter
                        DiagnosisComponent diagnosisComponent = diagnoses.get(0); // must exists because we check empty
                                                                                  // above
                        encounter.addDiagnosis(diagnosisComponent.copy());
                    }
                }
            }
        }
    }

    private Encounter getSuperEncounter(Encounter encounter) {
        if (!encounter.hasPartOf() || !encounter.getPartOf().hasReference()) return null;
        return getResource(bundle, Encounter.class, encounter.getPartOf().getReference());
    }

}
