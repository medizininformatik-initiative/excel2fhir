package de.uni_leipzig.life.csv2fhir;

import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Encounter.DiagnosisComponent;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

/**
 * Post-processing of a bundle. All references that have a procedure or a
 * diagnostic on the Encounter are removed and instead a list with references to
 * these objects is created in the Encounter. Concretely this means that for
 * Diagnosis and Procedure the entry encounter(Encounter::id) is removed and
 * instead in the Encounter references to these diagnoses and procedures are
 * added to the list diagnosis in condition(Condition::id | Procedure::id).
 *
 * @author AXS (16.11.2021)
 */
public class EncounterReferenceReplacer {

    /**
     * @param bundle
     */
    public static void convert(Bundle bundle) {
        List<BundleEntryComponent> bundleEntries = bundle.getEntry();
        for (BundleEntryComponent entry : bundleEntries) {
            Resource resource = entry.getResource();
            Reference encounterReference = null;
            if (resource instanceof Condition) {
                Condition condition = (Condition) resource;
                encounterReference = condition.getEncounter();
                condition.setEncounter(null);
                condition.setEncounterTarget(null);
            } else if (resource instanceof Procedure) {
                Procedure procedure = (Procedure) resource;
                encounterReference = procedure.getEncounter();
                procedure.setEncounter(null);
                procedure.setEncounterTarget(null);
            }
            if (encounterReference != null) {
                String encounterId = encounterReference.getReference();
                Encounter encounter = getResource(bundle, Encounter.class, encounterId); //should be only null in error cases
                Reference conditionReference = new Reference(resource);
                DiagnosisComponent diagnosisComponent = new DiagnosisComponent(conditionReference);
                encounter.addDiagnosis(diagnosisComponent);
            }
        }
    }

    /**
     * @param <T>
     * @param bundle
     * @param resourceClass
     * @param id
     * @return a resource with the given id and type
     */
    @SuppressWarnings("unchecked")
    public static <T extends Resource> T getResource(Bundle bundle, Class<? extends T> resourceClass, String id) {
        String baseId = getBaseId(id);
        List<BundleEntryComponent> bundleEntries = bundle.getEntry();
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

    /**
     * @param id
     * @return
     */
    private static String getBaseId(String id) {
        int index = id.indexOf('/');
        if (index > 1) {
            return id.substring(index + 1);
        }
        return id;
    }

}
