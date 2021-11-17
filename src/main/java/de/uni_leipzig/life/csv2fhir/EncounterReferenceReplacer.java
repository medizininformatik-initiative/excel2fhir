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
 * condition on the Encounter are removed and instead a list with references to
 * these objects is created in the Encounter. Concretely this means that for
 * Conditions and Procedure the entry encounter(Encounter::id) is removed and
 * instead in the Encounter references to these conditions and procedures are
 * added to the list diagnosis in condition(Condition::id | Procedure::id).
 *
 * @author AXS (16.11.2021)
 */
public class EncounterReferenceReplacer {

    /**
     * Adds the all references to conditions and procedures to the diagnoses
     * list of the encounter.<br>
     * <br>
     * The Vonk server does not tolerate circular references, so we remove the
     * reference from condition and procedure to encounter and then add the
     * reference from encounter to condition and procedure.
     *
     * @param bundle
     */
    public static void convert(Bundle bundle) {
        List<BundleEntryComponent> bundleEntries = bundle.getEntry();
        //for every bundle entry
        for (BundleEntryComponent entry : bundleEntries) {
            Resource resource = entry.getResource();
            Reference encounterReference = null;
            //if the entry is a condition or procedure then store the encounter
            //reference and remove it from the entry
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
            //entry was a condition or procedure -> we found an encounter
            //reference
            if (encounterReference != null) {
                String encounterId = encounterReference.getReference();
                //encounter should be only null in error cases, but mybe we
                //should catch and log
                Encounter encounter = getResource(bundle, Encounter.class, encounterId);
                //construct a valid DiagnosisComponent from condition or
                //procedure to add it as reference to the encounter
                Reference conditionReference = new Reference(resource);
                DiagnosisComponent diagnosisComponent = new DiagnosisComponent(conditionReference);
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

    /**
     * Returns the substring of the specified ID string after the first slash,
     * or the string itself if it does not contain a slash.
     *
     * @param id
     * @return
     */
    private static String getBaseId(String id) {
        int index = id.indexOf('/');
        //if index == -1 (= slash not found) then substring returns this
        return id.substring(index + 1);
    }

}
