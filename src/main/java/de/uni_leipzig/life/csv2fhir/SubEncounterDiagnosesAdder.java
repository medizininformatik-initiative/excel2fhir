package de.uni_leipzig.life.csv2fhir;

import static de.uni_leipzig.life.csv2fhir.Converter.getBaseId;

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
public class SubEncounterDiagnosesAdder {

    /**
     * Adds all sub encounters at least one diagnose from the super encounter if
     * exists.
     *
     * @param bundle
     */
    public static void convert(Bundle bundle) {
        List<BundleEntryComponent> bundleEntries = bundle.getEntry();

        // for every bundle entry
        nextEntry: for (BundleEntryComponent entry : bundleEntries) {
            Resource resource = entry.getResource();
            if (resource instanceof Encounter) {
                Encounter encounter = (Encounter) resource;
                List<DiagnosisComponent> diagnoses = encounter.getDiagnosis();
                //There is already a diagnosis reference in this encounter -> nothing to do
                if (!diagnoses.isEmpty()) {
                    continue;
                }
                //Get the Encounter of which this Encounter is a part
                Reference partOfReference = encounter.getPartOf();
                String superEncounterID = partOfReference.getReference();
                //no encounter found from which we can copy a diagnosis reference
                if (superEncounterID == null) {
                    continue;
                }
                Encounter superEncounter = getResource(bundle, Encounter.class, superEncounterID);
                if (superEncounter == null) {
                    continue;
                }
                diagnoses = superEncounter.getDiagnosis();
                //Also the parent Encounter has no diagnosis -> nothing to do
                if (diagnoses.isEmpty()) {
                    continue;
                }
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

    /**
     * Finds a resource in a bundle by its type and ID.
     *
     * @param <T> return value of the found element
     * @param bundle the bundle to be searched
     * @param resourceClass the subclass of the resource to be returned
     * @param id The ID string that the element must have. If this is not a
     *            simple ID like "X0001" but a full reference ID like
     *            "Encounter/X0001", then the ID is converted to a simple ID
     *            before comparison (everything before the slash is removed).
     * @return a resource with the given ID and type from the bundle. If not
     *         found <code>null</code> is returned.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Resource> T getResource(Bundle bundle, Class<? extends T> resourceClass, String id) {
        //if the id is a reference -> we extract the real id
        String baseId = getBaseId(id);
        List<BundleEntryComponent> bundleEntries = bundle.getEntry();
        //check every entry if it has the correct class and id
        for (BundleEntryComponent entry : bundleEntries) {
            Resource entryResource = entry.getResource();
            Class<? extends Resource> entryResourceClass = entryResource.getClass();
            if (entryResourceClass.isAssignableFrom(resourceClass)) {
                String resourceID = entryResource.getId();
                if (baseId.equals(resourceID)) {
                    return (T) entryResource;
                }
            }
        }
        return null;
    }

}
