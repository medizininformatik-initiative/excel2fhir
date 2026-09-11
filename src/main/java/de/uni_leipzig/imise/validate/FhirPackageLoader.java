package de.uni_leipzig.imise.validate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.hl7.fhir.common.hapi.validation.support.NpmPackageValidationSupport;
import org.hl7.fhir.utilities.npm.NpmPackage;

import ca.uhn.fhir.context.FhirContext;

/** Loads the reviewed R4 package set in the same order from a checkout or a JAR. */
final class FhirPackageLoader {

    private FhirPackageLoader() {
    }

    static NpmPackageValidationSupport load(FhirContext context) {
        NpmPackageValidationSupport support = new NpmPackageValidationSupport(context);
        try (BufferedReader manifest = new BufferedReader(new InputStreamReader(
                resource("fhir-packages.txt"), StandardCharsets.UTF_8))) {
            String line;
            int count = 0;
            while ((line = manifest.readLine()) != null) {
                String name = line.trim();
                if (name.isEmpty() || name.startsWith("#")) {
                    continue;
                }
                String path = "fhir/" + name;
                try (InputStream input = resource(path)) {
                    NpmPackage metadata = NpmPackage.fromPackage(input);
                    String versions = metadata.fhirVersionList();
                    if (versions != null && !versions.isBlank() && !versions.contains("4.0.1")) {
                        throw new IOException("Non-R4 package in validation manifest: " + name);
                    }
                }
                support.loadPackageFromClasspath(path);
                count++;
            }
            if (count == 0) {
                throw new IOException("FHIR package manifest is empty");
            }
            return support;
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Could not load the complete FHIR validation package set", e);
        }
    }

    private static InputStream resource(String name) throws IOException {
        InputStream input = FhirPackageLoader.class.getClassLoader().getResourceAsStream(name);
        if (input == null) {
            throw new IOException("Missing FHIR validation resource: " + name);
        }
        return input;
    }
}
