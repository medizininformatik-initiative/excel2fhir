package de.uni_leipzig.life.csv2fhir;

import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Fall;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

/**
 * @author AXS (24.11.2021)
 */
public class BundleFunctions {

    /**
     * Returns the substring of the specified ID string after the first slash,
     * or the string itself if it does not contain a slash.
     *
     * @param id
     * @return
     */
    public static String getBaseId(String id) {
        int index = id.indexOf('/');
        //if index == -1 (= slash not found) then substring returns this
        return id.substring(index + 1);
    }

    /**
     * @param resourceClass
     * @param idBase
     */
    public static Reference createReference(Class<? extends Resource> resourceClass, String idBase) {
        return new Reference(resourceClass.getSimpleName() + "/" + idBase);
    }

    /**
     * @param result
     * @param pid
     * @return all encounters with the pid from the alrady parsed csv tables
     */
    public static Collection<Encounter> getEncounters(ConverterResult result, String pid) {
        Collection<Encounter> encounters = getEncountersForPatient(result, Fall, pid);
        return encounters;
    }

    /**
     * @param result
     * @param identifier
     * @param pid
     * @return all encounters with the pid from the alrady parsed csv tables
     */
    public static Collection<Encounter> getEncountersForPatient(ConverterResult result, TableIdentifier identifier, String pid) {
        Collection<Encounter> encounters = new ArrayList<>();
        if (pid != null) {
            for (Encounter encounter : result.getResources(identifier, Encounter.class)) {
                Reference patientReference = encounter.getSubject();
                String encounterPID = patientReference.getReference();
                if (encounterPID == null) {
                    continue;
                }
                String basePID = getBaseId(pid);
                String baseEncounterPID = getBaseId(encounterPID);
                if (baseEncounterPID.equals(basePID)) {
                    encounters.add(encounter);
                }
            }
        }
        return encounters;
    }

    /**
     * Extract a date from one encounter of the corresponding patient.
     *
     * @param result
     * @param pid
     * @return
     */
    public static DateTimeType getEncounterDate(ConverterResult result, String pid) {
        Collection<Encounter> encounters = getEncountersForPatient(result, Fall, pid);
        DateTimeType encounterDate = getEncounterDate(encounters, true);
        if (encounterDate == null) {
            encounterDate = getEncounterDate(encounters, false);
        }
        return encounterDate;
    }

    /**
     * @param encounters
     * @param checkStartDates
     * @return the first date entry found in one of the encounters
     */
    public static DateTimeType getEncounterDate(Collection<Encounter> encounters, boolean checkStartDates) {
        for (Encounter encounter : encounters) {
            Period period = encounter.getPeriod();
            if (period != null) {
                DateTimeType date = checkStartDates ? period.getStartElement() : period.getEndElement();
                if (date != null) {
                    return date;
                }
            }
        }
        return null;
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
