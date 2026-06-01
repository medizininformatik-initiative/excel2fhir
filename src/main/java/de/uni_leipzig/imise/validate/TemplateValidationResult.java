package de.uni_leipzig.imise.validate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of an Excel input template validation run.
 */
public class TemplateValidationResult {

    private final List<TemplateValidationIssue> issues = new ArrayList<>();

    public void add(TemplateValidationIssue issue) {
        issues.add(issue);
    }

    public List<TemplateValidationIssue> getIssues() {
        return Collections.unmodifiableList(issues);
    }

    public boolean hasErrors() {
        return issues.stream().anyMatch(TemplateValidationIssue::isError);
    }

    public long getErrorCount() {
        return issues.stream().filter(TemplateValidationIssue::isError).count();
    }

    public long getWarningCount() {
        return issues.size() - getErrorCount();
    }
}
