package de.uni_leipzig.life.csv2fhir;

import static org.junit.Assert.*;
import static de.uni_leipzig.life.csv2fhir.ContactIndex.Level.*;
import org.hl7.fhir.r4.model.*;
import org.junit.Test;
import de.uni_leipzig.life.csv2fhir.converter.EncounterConverter;

public class ContactOutputPolicyTest {
    private final ContactIndex index = new ContactIndex();
    private Encounter contact(String id, ContactIndex.Level level, String parent) {
        Encounter e = new Encounter(); e.setId(id);
        e.setSubject(new Reference("Patient/p"));
        if (parent != null) e.setPartOf(new Reference("Encounter/" + parent));
        index.add(e, "p", "root", level, parent, 1, false);
        return e;
    }
    private ContactOutputPolicy policy(String values) {
        return new ContactOutputPolicy(ContractConfiguration.parse("CONFIGURATION_VERSION=1\n" + values), index);
    }
    @Test public void hierarchyUsesInputAncestorsAndNeverSubstitutesDisabledParents() {
        var root = contact("root", FACILITY, null);
        var department = contact("department", DEPARTMENT, "root");
        var ward = contact("ward", WARD_SERVICE, "department");
        var p = policy("CONTACT_DEPARTMENT_ENABLED=false\nCONTACT_WARD_SERVICE_PART_OF=facility\n");
        assertNull(p.output(department));
        assertNotNull(p.output(root));
        assertEquals("Encounter/root", ((Encounter)p.output(ward)).getPartOf().getReference());
        assertEquals("Encounter/department", ward.getPartOf().getReference());
        p = policy("CONTACT_DEPARTMENT_ENABLED=false\nCONTACT_WARD_SERVICE_PART_OF=department\n");
        assertFalse(((Encounter)p.output(ward)).hasPartOf());
        p = policy("CONTACT_FACILITY_ENABLED=false\n");
        assertNull(p.output(root));
        assertFalse(((Encounter)p.output(department)).hasPartOf());
        assertEquals("Encounter/department", ((Encounter)p.output(ward)).getPartOf().getReference());
    }
    @Test public void allLevelsOffKeepsOneGeneralCaseWithoutKdsLevelClaim() {
        var root = contact("root", FACILITY, null);
        root.addType().addCoding().setSystem("http://fhir.de/CodeSystem/Kontaktebene").setCode("einrichtungskontakt");
        root.getMeta().addProfile(EncounterConverter.ENCOUNTER_LEVEL1_CLASS_RESOURCES.getProfile());
        root.getMeta().addProfile("urn:other-profile");
        var department = contact("department", DEPARTMENT, "root");
        var ward = contact("ward", WARD_SERVICE, "department");
        var p = policy("CONTACT_FACILITY_ENABLED=false\nCONTACT_DEPARTMENT_ENABLED=false\nCONTACT_WARD_SERVICE_ENABLED=false\n");
        Encounter output = (Encounter)p.output(root);
        assertEquals("root", output.getId());
        assertFalse(output.hasType());
        assertFalse(output.hasPartOf());
        assertEquals("urn:other-profile", output.getMeta().getProfile().get(0).getValue());
        assertNull(p.output(department)); assertNull(p.output(ward));
        assertTrue(root.hasType());
        assertEquals(2, root.getMeta().getProfile().size());
    }
    @Test public void encounterOffOmitsEveryContactAndKeepsInternalIndex() {
        var root = contact("root", FACILITY, null);
        var ward = contact("ward", WARD_SERVICE, "root");
        var p = policy("ENCOUNTER_ENABLED=false\n");
        assertNull(p.output(root)); assertNull(p.output(ward));
        assertEquals(2, index.entries("p").size());
        var condition = new Condition();
        assertSame(condition, p.output(condition));
    }
    @Test public void classSelectionUsesActualClassAcrossAllLevels() {
        for (var level : ContactIndex.Level.values()) {
            var encounter = contact("e" + level, level, null);
            var p = policy("ENCOUNTER_INPATIENT_ENABLED=false\n");
            encounter.getClass_().setCode("IMP"); assertNull(p.output(encounter));
            encounter.getClass_().setCode("AMB"); assertNotNull(p.output(encounter));
            encounter.getClass_().setCode("PRENC"); assertNotNull(p.output(encounter));
        }
    }
}
