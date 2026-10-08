package de.uni_leipzig.life.csv2fhir;

import static org.junit.Assert.*;

import java.util.List;

import org.hl7.fhir.r4.model.*;
import org.junit.Test;

public class PatientOutputPolicyTest {
    @Test public void allPatientFieldsAreRemovedOnlyFromOutputCopies() {
        List<Resource> resources = List.of(new Encounter(), new Condition(), new Procedure(), new Observation(),
                new MedicationRequest(), new MedicationAdministration(), new MedicationStatement(),
                new DocumentReference(), new DiagnosticReport(), new CarePlan(), new Consent(), new Immunization());
        for (Resource source : resources) {
            String field = source instanceof Consent || source instanceof Immunization ? "patient" : "subject";
            source.setId("stable-id");
            source.setProperty(field, new Reference("Patient/p1").setDisplay("Patient description"));
            Resource output = PatientOutputPolicy.NEITHER.output(source);
            assertNotSame(source, output);
            assertEquals(source.getId(), output.getId());
            assertTrue(source.getNamedProperty(field).hasValues());
            assertFalse(source.fhirType(), output.getNamedProperty(field).hasValues());
            assertFalse(OutputFileType.JSON.getParser().encodeResourceToString(output).contains("Patient/p1"));
            assertTrue(OutputFileType.JSON.getParser().encodeResourceToString(source).contains("Patient/p1"));
            assertSame(source, PatientOutputPolicy.REFERENCE_ONLY.output(source));
            assertSame(source, PatientOutputPolicy.GENERATE_REFERENCE.output(source));
        }
    }

    @Test public void selectionDoesNotRenumberOrMutateInternalResources() {
        Patient patient = new Patient(); patient.setId("p1");
        Condition first = new Condition(); first.setId("condition-7"); first.setSubject(new Reference("Patient/p1"));
        Condition second = new Condition(); second.setId("condition-8"); second.setSubject(new Reference("Patient/p1"));
        for (PatientOutputPolicy mode : PatientOutputPolicy.values()) {
            var output = List.of(patient, first, second).stream().map(mode::output).filter(java.util.Objects::nonNull).toList();
            assertEquals(mode == PatientOutputPolicy.GENERATE_REFERENCE ? 3 : 2, output.size());
            assertEquals(List.of("condition-7", "condition-8"), output.stream().filter(r -> r instanceof Condition).map(Resource::getId).toList());
            assertEquals("Patient/p1", first.getSubject().getReference());
        }
        var groupObservation = new Observation().setSubject(new Reference("Group/g1"));
        assertEquals("Group/g1", ((Observation)PatientOutputPolicy.NEITHER.output(groupObservation)).getSubject().getReference());
        assertNull(PatientOutputPolicy.REFERENCE_ONLY.output(patient));
        assertNull(PatientOutputPolicy.NEITHER.output(patient));
    }
}
