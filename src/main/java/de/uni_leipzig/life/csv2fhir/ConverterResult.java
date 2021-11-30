package de.uni_leipzig.life.csv2fhir;

import static de.uni_leipzig.life.csv2fhir.BundleFuntions.getBaseId;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

import org.hl7.fhir.r4.model.Resource;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

/**
 * @author AXS (29.11.2021)
 */
public class ConverterResult {

    /**
     * Maps from the Resource type and the id to the resource created by this
     * converter.
     */
    private final Multimap<TableIdentifier, ConvertedResources<? extends Resource>> createdResources = ArrayListMultimap.create();

    /**
     * @param tableSource
     * @param resource
     */
    @SuppressWarnings("unchecked")
    public <T extends Resource> void add(TableIdentifier tableSource, T resource) {
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

}
