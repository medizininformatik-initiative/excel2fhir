package de.uni_leipzig.life.csv2fhir.converter;

import static de.uni_leipzig.life.csv2fhir.BundleFunctions.createReference;
import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Consent;
import static de.uni_leipzig.life.csv2fhir.converterFactory.ConsentConverterFactory.Consent_Columns.Datum_Einwilligung;

import java.util.Collections;
import java.util.List;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Consent.ConsentPolicyComponent;
import org.hl7.fhir.r4.model.Consent.ConsentState;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;

import de.uni_leipzig.imise.FHIRValidator;
import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterResult;
import de.uni_leipzig.life.csv2fhir.utils.ResourceMapper;

/**
 * @author AXS (18.01.2022)
 */
public class ConsentConverter extends Converter {

    /**
     *
     */
    private static final ResourceMapper consentResources = new ResourceMapper("Consent.map");

    /**
     * @param record
     * @param result
     * @param validator
     * @throws Exception
     */
    public ConsentConverter(CSVRecord record, ConverterResult result, FHIRValidator validator) throws Exception {
        super(record, result, validator);
    }

    @Override
    public List<Resource> convert() throws Exception {
        DateType consentDate = parseDate(Datum_Einwilligung);
        if (consentDate == null) {
            return Collections.emptyList();
        }
        Consent consent = new Consent();
        int nextId = result.getNextId(Consent, Consent.class);
        String pid = getPatientId();
        String id = pid + "-CO-" + nextId;
        consent.setId(id);
        consent.setMeta(new Meta().addProfile(res("CONSENT_PROFILE")));
        consent.setStatus(ConsentState.ACTIVE);
        consent.setPatient(createReference(Patient.class, pid));
        consent.setDateTime(consentDate.getValue());
        consent.setScope(createCodeableConcept(res("CONSENT_SCOPE_CODING_SYSTEM"), res("CONSENT_SCOPE_CODING_CODE")));
        consent.setCategory(Collections.singletonList(createCodeableConcept(res("CONSENT_CATEGORY_CODING_SYSTEM"), res("CONSENT_CATEGORY_CODING_CODE"))));
        consent.setPolicy(getPolicy());
        return Collections.singletonList(consent);
    }

    /**
     * @param resourceKey
     * @return
     */
    private static String res(Object resourceKey) {
        return String.valueOf(consentResources.get(resourceKey));
    }

    /**
     * @return
     */
    private static List<ConsentPolicyComponent> getPolicy() {
        ConsentPolicyComponent consentPolicyComponent = new ConsentPolicyComponent();
        consentPolicyComponent.setUri(res("CONSENT_POLICY_URI")); //hier z.B. MII Broad Consent
        return Collections.singletonList(consentPolicyComponent);
    }

    @Override
    protected Enum<?> getPatientIDColumnIdentifier() {
        return Consent.getPIDColumnIdentifier();
    }

}
