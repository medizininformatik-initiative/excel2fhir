package de.uni_leipzig.life.csv2fhir;

import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

import de.uni_leipzig.life.csv2fhir.converter.EncounterConverter;

/** Selects contact output without changing the input contact index. */
public final class ContactOutputPolicy {
    private final ContractConfiguration configuration;
    private final ContactIndex contacts;
    private final boolean generic;

    public ContactOutputPolicy(ContractConfiguration configuration, ContactIndex contacts) {
        this.configuration = configuration;
        this.contacts = contacts;
        generic = configuration != null && enabled("resource.Encounter.enabled")
                && !enabled("contact.facility.enabled") && !enabled("contact.department.enabled")
                && !enabled("contact.ward-service.enabled");
    }

    public boolean emits(Resource source) {
        if (configuration == null || !(source instanceof Encounter)) return true;
        if (!enabled("resource.Encounter.enabled")) return false;
        return contacts.get((Encounter)source).map(entry -> generic
                ? entry.level() == ContactIndex.Level.FACILITY
                : enabled("contact." + name(entry.level()) + ".enabled")).orElse(false);
    }

    public Resource output(Resource source) {
        if (!emits(source)) return null;
        if (configuration == null || !(source instanceof Encounter)) return source;
        Encounter output = ((Encounter)source).copy();
        var entry = contacts.get((Encounter)source).orElseThrow();
        output.setPartOf(null);
        if (generic) {
            output.getType().removeIf(type -> {
                boolean removed = type.getCoding().removeIf(c -> "http://fhir.de/CodeSystem/Kontaktebene".equals(c.getSystem()));
                return removed && type.getCoding().isEmpty();
            });
            output.getMeta().getProfile().removeIf(profile -> profile.getValue()
                    .equals(EncounterConverter.ENCOUNTER_LEVEL1_CLASS_RESOURCES.getProfile()));
            return output;
        }
        if (entry.level() != ContactIndex.Level.FACILITY) {
            String selected = configuration.effective("contact." + name(entry.level()) + ".partOf")
                    .map(value -> value.asText()).orElse("none");
            if (!selected.equals("none")) contacts.ancestor(entry.patientId(), source.getId(), level(selected))
                    .filter(parent -> emits(parent.encounter()))
                    .ifPresent(parent -> output.setPartOf(new Reference("Encounter/" + parent.encounter().getIdElement().getIdPart())));
        }
        return output;
    }

    private boolean enabled(String id) {
        return configuration.effective(id).map(value -> value.asBoolean()).orElse(false);
    }
    static String name(ContactIndex.Level level) {
        switch (level) {
        case FACILITY: return "facility";
        case DEPARTMENT: return "department";
        default: return "ward-service";
        }
    }
    static ContactIndex.Level level(String value) {
        switch (value) {
        case "facility": return ContactIndex.Level.FACILITY;
        case "department": return ContactIndex.Level.DEPARTMENT;
        case "ward-service": return ContactIndex.Level.WARD_SERVICE;
        default: throw new IllegalArgumentException("Unknown contact level: " + value);
        }
    }
}
