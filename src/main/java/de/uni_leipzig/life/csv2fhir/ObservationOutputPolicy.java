package de.uni_leipzig.life.csv2fhir;

import java.util.Set;

import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Type;

/** Deliberate measurement coding errors for test output, before DAR overrides. */
public final class ObservationOutputPolicy {
    private final ContractConfiguration configuration;

    public ObservationOutputPolicy(ContractConfiguration configuration) {
        this.configuration = configuration;
    }

    static boolean supports(String id) {
        return Set.of("resource.Observation.laboratory.ucumCodeInUnit",
                "resource.Observation.vitalSigns.ucumCodeInUnit").contains(id);
    }

    public Resource output(Resource source) {
        if (configuration == null || !(source instanceof Observation)) return source;
        String option = "resource." + ClinicalEncounterAssignment.optionResource(source) + ".ucumCodeInUnit";
        if (!supports(option) || !configuration.effective(option).map(v -> v.asBoolean()).orElse(false)) return source;
        Observation output = ((Observation)source).copy();
        moveCode(output.getValue());
        output.getComponent().forEach(component -> moveCode(component.getValue()));
        return output;
    }

    private static void moveCode(Type value) {
        if (!(value instanceof Quantity)) return;
        Quantity quantity = (Quantity)value;
        if (!"http://unitsofmeasure.org".equals(quantity.getSystem()) || !quantity.getCodeElement().hasValue()) return;
        quantity.setUnit(quantity.getCode());
        quantity.setCodeElement(null);
    }
}
