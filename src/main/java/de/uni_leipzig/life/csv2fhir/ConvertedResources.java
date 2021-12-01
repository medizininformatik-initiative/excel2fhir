package de.uni_leipzig.life.csv2fhir;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.map.HashedMap;
import org.hl7.fhir.r4.model.Resource;

/**
 * Basically a map that maps from the ID of a resource to the resource. For
 * simplicity, however, the map interface is not implemented here.
 *
 * @author AXS (30.11.2021)
 * @param <T>
 */
public class ConvertedResources<T extends Resource> implements Iterable<T> {

    /** the class of the items in this result */
    private final Class<T> contentType;

    /**  */
    private final Map<String, T> idToResourceMap = new HashedMap<>();

    /**
     * @param contentType
     */
    public ConvertedResources(Class<T> contentType) {
        this.contentType = contentType;
    }

    /**
     * @param resource
     */
    public void add(T resource) {
        String id = resource.getId();
        idToResourceMap.put(id, resource);
    }

    /**
     * @param key
     * @return
     * @see java.util.Map#get(java.lang.Object)
     */
    public T get(String id) {
        return idToResourceMap.get(id);
    }

    /**
     * @return
     * @see java.util.Map#keySet()
     */
    public Set<String> keySet() {
        return idToResourceMap.keySet();
    }

    /**
     * @return
     * @see java.util.Map#values()
     */
    public Collection<T> values() {
        return idToResourceMap.values();
    }

    /**
     * @return the content type
     */
    public final Class<T> getContentType() {
        return contentType;
    }

    /**
     * @param contentType
     * @return
     */
    public boolean hasContentType(Class<? extends Resource> contentType) {
        return contentType.isAssignableFrom(this.contentType);
    }

    /**
     * @return
     * @see java.util.Map#size()
     */
    public int size() {
        return idToResourceMap.size();
    }

    @Override
    public Iterator<T> iterator() {
        return values().iterator();
    }

}
