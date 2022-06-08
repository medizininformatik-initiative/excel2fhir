package de.uni_leipzig.life.csv2fhir;

import static de.uni_leipzig.life.csv2fhir.BundleFunctions.getBaseId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationAdministration;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Person;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Resource;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import de.uni_leipzig.imise.utils.Alphabetical;

/**
 * @author AXS (29.11.2021)
 */
public class ConverterResult {

    /**
     * Maps from the Resource type and the id to the resource created by this
     * converter.<br>
     * Must be a {@link Multimap} because from the
     * {@link TableIdentifier#Medikation} we generate 3 different types of
     * resources. <br>
     * <br>
     * A valid convertion result with all resource types has the structure:<br>
     * {@link TableIdentifier#Person} ->
     * {{@link ConvertedResources}<{@link Person}>},<br>
     * {@link TableIdentifier#Versorgungsfall} ->
     * {{@link ConvertedResources}<{@link Encounter}>},<br>
     * {@link TableIdentifier#Abteilungsfall} ->
     * {{@link ConvertedResources}<{@link Encounter}>},<br>
     * {@link TableIdentifier#Diagnose} ->
     * {{@link ConvertedResources}<{@link Condition}>},<br>
     * {@link TableIdentifier#Prozedur} ->
     * {{@link ConvertedResources}<{@link Procedure}>},<br>
     * {@link TableIdentifier#Klinische_Dokumentation} ->
     * {{@link ConvertedResources}<{@link Observation}>},<br>
     * {@link TableIdentifier#Laborbefund} ->
     * {{@link ConvertedResources}<{@link Observation}>},<br>
     * {@link TableIdentifier#Medikation} ->
     * {{@link ConvertedResources}<{@link Medication}>,
     * {@link ConvertedResources}<{@link MedicationStatement}>,
     * {@link ConvertedResources}<{@link MedicationAdministration}>},<br>
     */
    private final Multimap<TableIdentifier, ConvertedResources<? extends Resource>> createdResources = ArrayListMultimap.create();

    /**  */
    private ConverterResultStatistics statistics = null;

    /**
     * @param tableSource
     * @param resource
     */
    @SuppressWarnings("unchecked")
    public <T extends Resource> void add(TableIdentifier tableSource, T resource) {
        statistics = null;
        Collection<ConvertedResources<? extends Resource>> typedConverterResults = createdResources.get(tableSource);
        ConvertedResources<T> typedConverterResult = null;
        Class<T> resourceClass = (Class<T>) resource.getClass();
        for (ConvertedResources<? extends Resource> result : typedConverterResults) {
            if (result.hasContentType(resourceClass)) {
                typedConverterResult = (ConvertedResources<T>) result;
            }
        }
        if (typedConverterResult == null) {
            typedConverterResult = new ConvertedResources<>(resourceClass);
            createdResources.put(tableSource, typedConverterResult);
        }
        typedConverterResult.add(resource);
    }

    /**
     * Adds all resources tor this result.
     *
     * @param resources
     * @throws ClassCastException if at least one resource in the collection has
     *             not the generic type of this result.
     */
    public <T extends Resource> void addAll(TableIdentifier tableSource, Collection<T> resources) throws ClassCastException {
        for (T resource : resources) {
            add(tableSource, resource);
        }
    }

    /**
     * @param resourceType
     * @param id
     * @return
     */
    public <T extends Resource> T get(TableIdentifier tableSource, Class<T> resourceType, String id) {
        ConvertedResources<T> result = getResult(tableSource, resourceType);
        if (result != null) {
            id = getBaseId(id);
            for (T resource : result.values()) {
                String resourceID = resource.getId();
                if (Objects.equals(id, resourceID)) {
                    return resource;
                }
            }
        }
        return null;
    }

    /**
     * @param <T>
     * @param tableSource
     * @param resourceType
     * @return
     */
    @SuppressWarnings("unchecked")
    private <T extends Resource> ConvertedResources<T> getResult(TableIdentifier tableSource, Class<T> resourceType) {
        for (ConvertedResources<? extends Resource> result : createdResources.get(tableSource)) {
            if (result.hasContentType(resourceType)) {
                return (ConvertedResources<T>) result;
            }
        }
        return null;
    }

    /**
     * @param <T>
     * @param tableSource
     * @param resourceType
     * @return
     */
    @SuppressWarnings("unchecked")
    public <T extends Resource> Iterable<T> getResources(TableIdentifier tableSource, Class<T> resourceType) {
        ConvertedResources<T> result = getResult(tableSource, resourceType);
        return result != null ? result : Collections.EMPTY_LIST;
    }

    /**
     * @param tableSource
     * @param resourceType
     * @return
     */
    public <T extends Resource> int getResourceCount(TableIdentifier tableSource, Class<T> resourceType) {
        ConvertedResources<T> result = getResult(tableSource, resourceType);
        return result != null ? result.size() : 0;
    }

    /**
     * @param tableSource
     * @param resourceType
     * @return
     */
    public <T extends Resource> int getNextId(TableIdentifier tableSource, Class<T> resourceType) {
        return getResourceCount(tableSource, resourceType) + 1;
    }

    /**
     * @return
     */
    public ConverterResultStatistics getStatistics() {
        if (statistics == null) {
            statistics = new ConverterResultStatistics().add(this);
        }
        return statistics;
    }

    @Override
    public String toString() {
        return getStatistics().toString();
    }

    /**
     * Can store the one count for each Resource type and output it
     * appropriately via the toString() function.
     *
     * @author AXS (07.06.2022)
     */
    public static final class ConverterResultStatistics {

        /** Maps from a resource type to the count of this type. */
        private final Map<Class<? extends Resource>, Integer> stats = new HashMap<>();

        /**
         * Adds the values from the other statistics to this.
         *
         * @param other
         */
        public ConverterResultStatistics add(ConverterResultStatistics other) {
            for (Class<? extends Resource> resourceType : other.stats.keySet()) {
                Integer value2Add = other.stats.get(resourceType);
                add(resourceType, value2Add);
            }
            return this;
        }

        /**
         * Adds the values from the statistics of the given
         * {@link ConverterResult} to this.
         *
         * @param result
         */
        public ConverterResultStatistics add(ConverterResult result) {
            add(result.createdResources);
            return this;
        }

        /**
         * @param createdResources
         */
        private void add(Multimap<TableIdentifier, ConvertedResources<? extends Resource>> createdResources) {
            for (TableIdentifier key : createdResources.keySet()) {
                Collection<ConvertedResources<? extends Resource>> resources = createdResources.get(key);
                for (ConvertedResources<? extends Resource> convertedResource : resources) {
                    Class<? extends Resource> resourceType = convertedResource.getContentType();
                    int count = convertedResource.size();
                    add(resourceType, count);
                }
            }
        }

        /**
         * @param resourceType
         * @param value2Add
         */
        private void add(Class<? extends Resource> resourceType, int value2Add) {
            Integer oldCount = stats.getOrDefault(resourceType, 0);
            stats.put(resourceType, oldCount + value2Add);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            int fullCount = 0;
            List<Class<? extends Resource>> resourceTypes = new ArrayList<>(stats.keySet());
            Alphabetical.sort(resourceTypes);
            for (Class<? extends Resource> resourceType : resourceTypes) {
                sb.append("\t");
                sb.append(resourceType.getSimpleName());
                sb.append("=");
                int value = stats.getOrDefault(resourceType, 0);
                sb.append(value);
                sb.append("\n");
                fullCount += value;
            }
            sb.append("\tFull Resource Count=");
            sb.append(fullCount);
            return sb.toString();
        }

    }

}
