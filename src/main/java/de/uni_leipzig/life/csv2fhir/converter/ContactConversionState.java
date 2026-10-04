package de.uni_leipzig.life.csv2fhir.converter;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.r4.model.Encounter;

/** Mutable input reconstruction state owned by one ConverterResult. */
public final class ContactConversionState {
    Encounter primaryContact;
    boolean primaryEndDerived, facilityBound;
    final List<Encounter> secondaryContacts = new ArrayList<>();
    final Map<Encounter, Map<String, String>> derivedEnds = new IdentityHashMap<>();
    Encounter previousEncounterLevel1;
    Encounter previousEncounterLevel2;
    String previousDepartmentName;
}
