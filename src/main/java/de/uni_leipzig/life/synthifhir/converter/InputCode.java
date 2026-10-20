package de.uni_leipzig.life.synthifhir.converter;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.EnumFactory;
import org.hl7.fhir.r4.model.Enumeration;
import org.hl7.fhir.r4.model.Extension;

/** Preserves explicit table codes; vocabulary membership belongs to FHIR validation. */
public final class InputCode<T extends Enum<?>> extends Enumeration<T> {
    private static final long serialVersionUID = 1L;

    /**
     * @deprecated For Java deserialization only; use {@link #of(Enumeration, String)}.
     * Enumeration.readExternal restores the enum factory.
     */
    @Deprecated
    public InputCode() {
        super();
    }

    private InputCode(EnumFactory<T> factory) {
        super(factory);
    }

    public static <T extends Enum<?>> Enumeration<T> of(Enumeration<T> target, String value) {
        InputCode<T> result = new InputCode<>(target.getEnumFactory());
        Extension absent = DiagnosisValues.absentReason(value);
        if (absent != null) result.addExtension(absent);
        else result.setValueAsString(value);
        return result;
    }

    @Override
    protected T parse(String value) {
        try {
            return super.parse(value);
        } catch (FHIRException | IllegalArgumentException e) {
            // The primitive string remains available even without a corresponding Java enum.
            return null;
        }
    }

    @Override
    public InputCode<T> copy() {
        InputCode<T> result = new InputCode<>(getEnumFactory());
        copyValues(result);
        result.setValueAsString(getValueAsString());
        return result;
    }
}
