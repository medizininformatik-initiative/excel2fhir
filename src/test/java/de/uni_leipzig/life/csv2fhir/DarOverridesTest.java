package de.uni_leipzig.life.csv2fhir;

import static org.junit.Assert.*;
import org.junit.Test;
import org.hl7.fhir.r4.model.*;

public class DarOverridesTest {
    private Resource apply(Resource source, String settings) {
        return new DarOverrides(ContractConfiguration.parse("CONFIGURATION_VERSION=1\n" + settings)).output(source);
    }
    private void reason(Element element, String code) {
        assertEquals(code, ((CodeType)element.getExtensionByUrl(DarOverrides.URL).getValue()).getValue());
        assertEquals(1, element.getExtensionsByUrl(DarOverrides.URL).size());
    }
    @Test public void primitiveOverridesPreserveSourceAndOtherExtensionsAndCreateMissingScalars() {
        Patient p = new Patient(); p.addName().setFamily("Smith").addGiven("Ada").addGiven("Jane");
        p.getNameFirstRep().getFamilyElement().addExtension("urn:other", new StringType("kept"));
        p.getNameFirstRep().getFamilyElement().addExtension(DarOverrides.URL, new CodeType("unknown"));
        Patient out = (Patient)apply(p, "DAR_PATIENT_NAME_FAMILY=masked\nDAR_PATIENT_NAME_GIVEN=masked\nDAR_PATIENT_BIRTH_DATE=unknown\nDAR_PATIENT_DECEASED_X=unknown\n");
        assertFalse(out.getNameFirstRep().getFamilyElement().hasValue());
        reason(out.getNameFirstRep().getFamilyElement(), "masked");
        assertNotNull(out.getNameFirstRep().getFamilyElement().getExtensionByUrl("urn:other"));
        assertEquals(2, out.getNameFirstRep().getGiven().size());
        for (StringType given : out.getNameFirstRep().getGiven()) { assertFalse(given.hasValue()); reason(given,"masked"); }
        reason(out.getBirthDateElement(), "unknown"); reason(out.getDeceasedDateTimeType(), "unknown");
        assertEquals("Smith", p.getNameFirstRep().getFamily()); assertFalse(p.hasBirthDate());
    }
    @Test public void preservesCodingSystemsAndDoesNotInventCodingsOrRepeatedItems() {
        Condition c = new Condition(); c.getCode().addCoding().setSystem("urn:icd").setCode("A");
        Condition out = (Condition)apply(c, "DAR_CONDITION_CODE_CODING_CODE=unknown\nDAR_CONDITION_CODE_CODING_VERSION=unknown\n");
        Coding coding = out.getCode().getCodingFirstRep(); assertEquals("urn:icd", coding.getSystem());
        assertFalse(coding.getCodeElement().hasValue()); reason(coding.getCodeElement(), "unknown"); reason(coding.getVersionElement(), "unknown");
        assertFalse(((Condition)apply(new Condition(), "DAR_CONDITION_CODE_CODING_CODE=unknown\n")).hasCode());
        assertFalse(((Patient)apply(new Patient(), "DAR_PATIENT_NAME_FAMILY=unknown\n")).hasName());
    }
    @Test public void selectsMeasurementVariantFromOriginalAndKeepsCategories() {
        Observation o = new Observation(); o.setValue(new Quantity(5));
        o.addCategory().addCoding().setSystem("urn:fixed").setCode("laboratory");
        o.addComponent().setValue(new StringType("positive"));
        Observation out = (Observation)apply(o, "DAR_LABORATORY_VALUE_X_NUMERIC_MEASUREMENT=error\nDAR_LABORATORY_VALUE_X_MEASUREMENT=unknown\nDAR_LABORATORY_COMPONENT_VALUE_X_MEASUREMENT=masked\n");
        assertFalse(out.hasValue()); assertEquals("error", out.getDataAbsentReason().getCodingFirstRep().getCode());
        assertEquals("masked", out.getComponentFirstRep().getDataAbsentReason().getCodingFirstRep().getCode());
        assertFalse(out.getComponentFirstRep().hasValue()); assertEquals("laboratory", out.getCategoryFirstRep().getCodingFirstRep().getCode());
        assertTrue(o.hasValue());
    }
    @Test public void clearsComplexValuesAndAttachmentMetadataButPreservesUrl() {
        MedicationAdministration a = new MedicationAdministration();
        a.setEffective(new Period().setStartElement(new DateTimeType("2020-01-01")).setEndElement(new DateTimeType("2020-01-02")));
        a.getDosage().setDose(new Quantity(7).setUnit("mg"));
        MedicationAdministration out = (MedicationAdministration)apply(a,"DAR_MEDICATION_ADMINISTRATION_EFFECTIVE_X=unknown\nDAR_MEDICATION_ADMINISTRATION_DOSAGE_DOSE=unknown\n");
        assertFalse(out.getEffectivePeriod().hasStart()); assertFalse(out.getEffectivePeriod().hasEnd()); reason(out.getEffectivePeriod(),"unknown");
        assertFalse(out.getDosage().getDose().hasValue()); assertFalse(out.getDosage().getDose().hasUnit()); reason(out.getDosage().getDose(),"unknown");
        DocumentReference d = new DocumentReference(); d.addContent().getAttachment().setData(new byte[]{1}).setHash(new byte[]{2}).setSize(1).setUrl("https://example.org/document");
        Attachment attachment = ((DocumentReference)apply(d,"DAR_DOCUMENT_REFERENCE_CONTENT_ATTACHMENT_DATA=masked\n")).getContentFirstRep().getAttachment();
        assertFalse(attachment.getDataElement().hasValue()); assertFalse(attachment.hasHash()); assertFalse(attachment.hasSize());
        reason(attachment.getDataElement(),"masked"); assertEquals("https://example.org/document",attachment.getUrl());
    }
    @Test public void rejectsAsTextWithoutNarrativeAndConflictingConditionStatus() {
        Condition c = new Condition(); c.getCode().addCoding().setCode("A");
        assertThrows(IllegalArgumentException.class, () -> apply(c,"DAR_CONDITION_CODE_CODING_CODE=as-text\n"));
        c.getText().setStatus(Narrative.NarrativeStatus.GENERATED).setDivAsString("<div xmlns=\"http://www.w3.org/1999/xhtml\">Diagnosis A</div>");
        reason(((Condition)apply(c,"DAR_CONDITION_CODE_CODING_CODE=as-text\n")).getCode().getCodingFirstRep().getCodeElement(),"as-text");
        c.setAbatement(new DateTimeType("2020-01-01"));
        assertThrows(IllegalArgumentException.class, () -> apply(c,"DAR_CONDITION_CLINICAL_STATUS=unknown\n"));
        assertTrue(c.hasAbatement());
    }
    @Test public void createsNestedScalarWithoutInventingRepeatedParents() {
        Encounter out = (Encounter)apply(new Encounter(),"DAR_ENCOUNTER_PERIOD_END=unknown\n");
        reason(out.getPeriod().getEndElement(), "unknown");
        Encounter e = new Encounter();
        Extension admission = new Extension("http://fhir.de/StructureDefinition/Aufnahmegrund");
        admission.addExtension("VierteStelle", new Coding("urn:system", "1", null));
        e.addExtension(admission);
        out = (Encounter)apply(e, "DAR_ENCOUNTER_ADMISSION_REASON_FOURTH_DIGIT=unknown\n");
        Coding coding = (Coding)out.getExtension().get(0).getExtension().get(0).getValue();
        assertEquals("urn:system",coding.getSystem()); reason(coding.getCodeElement(),"unknown");
    }
    @Test public void refusesIncompatibleNewAbatementAndUnsupportedChoiceTypes() {
        Condition c = new Condition();
        c.getClinicalStatus().addCoding().setSystem("http://terminology.hl7.org/CodeSystem/condition-clinical").setCode("active");
        assertThrows(IllegalArgumentException.class, () -> apply(c, "DAR_CONDITION_ABATEMENT_X=unknown\n"));
        c.getClinicalStatus().getCodingFirstRep().setCode("resolved");
        reason(((Condition)apply(c, "DAR_CONDITION_ABATEMENT_X=unknown\n")).getAbatementDateTimeType(), "unknown");
        Patient p = new Patient(); p.setDeceased(new BooleanType(false));
        assertThrows(IllegalArgumentException.class, () -> apply(p, "DAR_PATIENT_DECEASED_X=unknown\n"));
    }
    @Test public void distinguishesVitalSignsAndRemovesEmbeddedDocumentUrls() {
        Observation vital = new Observation(); vital.setValue(new Quantity(42));
        vital.addCategory().addCoding().setSystem("http://terminology.hl7.org/CodeSystem/observation-category").setCode("vital-signs");
        Observation out = (Observation)apply(vital, "DAR_LABORATORY_VALUE_X_NUMERIC_MEASUREMENT=masked\n");
        assertTrue(out.hasValue());
        out = (Observation)apply(vital, "DAR_VITAL_SIGNS_VALUE_X_NUMERIC_MEASUREMENT=masked\n");
        assertFalse(out.hasValue()); assertEquals("masked",out.getDataAbsentReason().getCodingFirstRep().getCode());
        DocumentReference d = new DocumentReference(); d.addContent().getAttachment().setUrl("data:text/plain;base64,c2VjcmV0");
        Attachment attachment = ((DocumentReference)apply(d,"DAR_DOCUMENT_REFERENCE_CONTENT_ATTACHMENT_DATA=masked\n")).getContentFirstRep().getAttachment();
        assertFalse(attachment.hasUrl()); reason(attachment.getDataElement(),"masked");
    }
    @Test public void serializationContainsOnlyAbsentPrimitiveExtensionAndDerivedResourcesCanBeOverridden() {
        Patient p = new Patient(); p.setBirthDateElement(new DateType("2000-01-01"));
        Patient out = (Patient)apply(p,"DAR_PATIENT_BIRTH_DATE=masked\n");
        String json = ca.uhn.fhir.context.FhirContext.forR4Cached().newJsonParser().encodeResourceToString(out);
        assertTrue(json.contains("_birthDate")); assertFalse(json.contains("2000-01-01"));
        MedicationStatement statement = new MedicationStatement(); statement.setId("derived-example");
        reason(((MedicationStatement)apply(statement,"DAR_MEDICATION_STATEMENT_EFFECTIVE_X=unknown\n")).getEffectiveDateTimeType(),"unknown");
    }

}
