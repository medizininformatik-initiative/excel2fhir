package de.uni_leipzig.imise.validate;

import java.io.IOException;

/**
 * Exception thrown if an Excel input template contains blocking validation errors.
 */
public class TemplateValidationException extends IOException {

    private static final long serialVersionUID = 1L;

    private final TemplateValidationResult validationResult;

    public TemplateValidationException(TemplateValidationResult validationResult) {
        super("Excel template validation failed with " + validationResult.getErrorCount() + " error(s)");
        this.validationResult = validationResult;
    }

    public TemplateValidationResult getValidationResult() {
        return validationResult;
    }
}
