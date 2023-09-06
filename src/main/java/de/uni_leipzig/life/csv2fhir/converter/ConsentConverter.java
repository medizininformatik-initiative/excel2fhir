package de.uni_leipzig.life.csv2fhir.converter;

import static de.uni_leipzig.life.csv2fhir.ConverterOptions.IntOption.START_ID_CONSENT;
import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Consent;
import static de.uni_leipzig.life.csv2fhir.converter.ConsentConverter.Consent_Columns.Datum_Einwilligung;
import static org.apache.logging.log4j.util.Strings.isNotBlank;
import static org.hl7.fhir.r4.model.Consent.ConsentProvisionType.DENY;
import static org.hl7.fhir.r4.model.Consent.ConsentProvisionType.PERMIT;

import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Consent.ConsentPolicyComponent;
import org.hl7.fhir.r4.model.Consent.ConsentProvisionType;
import org.hl7.fhir.r4.model.Consent.ConsentState;
import org.hl7.fhir.r4.model.Consent.provisionComponent;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Resource;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import de.uni_leipzig.imise.validate.FHIRValidator;
import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterOptions;
import de.uni_leipzig.life.csv2fhir.ConverterResult;
import de.uni_leipzig.life.csv2fhir.TableColumnIdentifier;
import de.uni_leipzig.life.csv2fhir.utils.ResourceMapper;

/**
 * @author AXS (18.01.2022)
 */
public class ConsentConverter extends Converter {

    /**
     *
     */
    public static enum Consent_Columns implements TableColumnIdentifier {
        Datum_Einwilligung,
        PDAT_Einwilligung,
        KKDAT_retro_Einwilligung,
        KKDAT_Einwilligung,
        BIOMAT_Einwilligung,
        BIOMAT_Zusatz_Einwilligung;

        @Override
        public String toString() {
            return name().replace('_', ' ');
        }

        @Override
        public boolean isMandatory() {
            return false;
        }
    }

    /**
     * Default duration of a consent (only some consents have a smaller
     * duration).
     */
    public static final int CONSENT_DEFAULT_DURATION_THIRTY_YEARS = 30;

    /**
     *
     */
    private static final ResourceMapper CONSENT_RESOURCES = ResourceMapper.of("Consent.map");

    /**
     *
     */
    private static final StaticConsentData STATIC_CONSENT_DATA = new StaticConsentData();

    /**
     * @param record
     * @param result
     * @param validator
     * @param options
     * @throws Exception
     */
    public ConsentConverter(CSVRecord record, ConverterResult result, FHIRValidator validator, ConverterOptions options) throws Exception {
        super(record, result, validator, options);
    }

    @Override
    protected List<Resource> convertInternal() throws Exception {
        DateType consentDate = parseDate(Datum_Einwilligung);
        if (consentDate == null) {
            return Collections.emptyList();
        }
        Consent consent = new Consent();
        int nextId = result.getNextId(Consent, Consent.class, START_ID_CONSENT);
        String pid = getPatientId();
        String id = pid + "-CO-" + nextId;
        consent.setId(id);
        consent.setMeta(new Meta().addProfile(res("CONSENT_PROFILE")));
        consent.setStatus(ConsentState.ACTIVE);
        consent.setPatient(getPatientReference());
        consent.setDateTime(consentDate.getValue());
        consent.setScope(createCodeableConcept(res("CONSENT_SCOPE_CODING_SYSTEM"), res("CONSENT_SCOPE_CODING_CODE")));
        consent.setCategory(Collections.singletonList(createCodeableConcept(res("CONSENT_CATEGORY_CODING_SYSTEM"), res("CONSENT_CATEGORY_CODING_CODE"))));
        consent.setPolicy(getPolicy());
        consent.setProvision(getProvision());
        return Collections.singletonList(consent);
    }

    /**
     * @param resourceKey
     * @return
     */
    private static String res(Object resourceKey) {
        return String.valueOf(CONSENT_RESOURCES.get(resourceKey));
    }

    /**
     * @return
     */
    private static List<ConsentPolicyComponent> getPolicy() {
        ConsentPolicyComponent consentPolicyComponent = new ConsentPolicyComponent();
        consentPolicyComponent.setUri(res("CONSENT_POLICY_URI")); //hier z.B. MII Broad Consent
        return Collections.singletonList(consentPolicyComponent);
    }

    /**
     * @param consentDate
     * @return
     * @throws Exception
     */
    private provisionComponent getProvision() throws Exception {
        provisionComponent provision = new provisionComponent();
        provision.setType(DENY);
        Period defaultPeriod = getPeriod(CONSENT_DEFAULT_DURATION_THIRTY_YEARS);
        provision.setPeriod(defaultPeriod);
        addSubProvisions(provision, defaultPeriod);
        return provision;
    }

    /**
     * @param yearsDiff
     * @return
     * @throws Exception
     */
    private Period getPeriod(int yearsDiff) throws Exception {
        DateTimeType consentDate = parseDateTimeType(Datum_Einwilligung);
        DateTimeType consentPeriodDate = parseDateTimeType(Datum_Einwilligung);
        addYears(consentPeriodDate, yearsDiff);
        return createPeriod(consentDate, consentPeriodDate);
    }

    /**
     * @param provision
     * @param defaultPeriod
     * @throws Exception
     */
    private void addSubProvisions(provisionComponent provision, Period defaultPeriod) throws Exception {
        Set<Integer> provisionGroupIndices = STATIC_CONSENT_DATA.provisionGroupIndexToGroupMemberIndices.keySet();
        for (int provisionGroupIndex : provisionGroupIndices) {
            String consentGroupColumnName = STATIC_CONSENT_DATA.getProvisionGroupColumnName(provisionGroupIndex);
            String consentValue = get(consentGroupColumnName);
            if (isNotBlank(consentValue)) {
                for (int subProvisionIndex : STATIC_CONSENT_DATA.provisionGroupIndexToGroupMemberIndices.get(provisionGroupIndex)) {
                    provisionComponent subProvision = provision.addProvision();
                    // permit or deny
                    ConsentProvisionType provisionType = isYesValue(consentValue) ? PERMIT : DENY;
                    subProvision.setType(provisionType);
                    // provision period
                    int durationYears = STATIC_CONSENT_DATA.provisionIndexToDurationYears.getOrDefault(subProvisionIndex, CONSENT_DEFAULT_DURATION_THIRTY_YEARS);
                    Period period = defaultPeriod;
                    if (durationYears != CONSENT_DEFAULT_DURATION_THIRTY_YEARS) {
                        period = getPeriod(durationYears);
                    }
                    subProvision.setPeriod(period);
                    // provision coding with system, code and display text
                    String system = STATIC_CONSENT_DATA.getProvisionSystem();
                    String code = STATIC_CONSENT_DATA.getProvisionCode(subProvisionIndex);
                    String display = STATIC_CONSENT_DATA.getProvisionDisplayText(subProvisionIndex);
                    CodeableConcept coding = createCodeableConcept(system, code, display, null);
                    subProvision.addCode(coding);
                }
            }
        }
    }

    /**
     * @param date
     * @param years
     * @param minusOneDay
     */
    private static void addYears(DateTimeType date, int years) {
        date.add(Calendar.YEAR, years);
        //always substract one day!
        int dayDiff = years < 0 ? 1 : -1;
        date.add(Calendar.DATE, dayDiff);
    }

    /**
     *
     */
    private static class StaticConsentData {

        /**  */
        public static final String CONSENT_PROVISION_SYSTEM = res("CONSENT_PROVISION_SYSTEM");

        /**  */
        public static final String CONSENT_PROVISION_COLUMN_KEY_PREFIX = "CONSENT_PROVISION_COLUMN_";

        /**  */
        public static final String CONSENT_PROVISION_GROUP_KEY_PREFIX = "CONSENT_PROVISION_GROUP_";

        public static final String CONSENT_PROVISION_TEXT_KEY_PREFIX = "CONSENT_PROVISION_TEXT_";

        /**  */
        private final Multimap<Integer, Integer> provisionGroupIndexToGroupMemberIndices = ArrayListMultimap.create();

        /**  */
        private final Map<Integer, Integer> provisionIndexToDurationYears = new HashMap<>();

        /**
         *
         */
        public StaticConsentData() {
            for (Object keyObject : CONSENT_RESOURCES.keySet()) {
                String key = String.valueOf(keyObject);
                if (key.startsWith(CONSENT_PROVISION_GROUP_KEY_PREFIX)) {
                    String groupIndexString = key.substring(CONSENT_PROVISION_GROUP_KEY_PREFIX.length());
                    int groupIndex = Integer.parseInt(groupIndexString);
                    String groupMembersResourceString = CONSENT_RESOURCES.getProperty(key);
                    String[] groupMembers = groupMembersResourceString.split("\\s"); //separated by spaces
                    for (String groupMember : groupMembers) {
                        int periodYearsStartIndex = groupMember.indexOf("("); //contains an other time perod than the standard 30 years
                        int durationYears = CONSENT_DEFAULT_DURATION_THIRTY_YEARS;
                        if (periodYearsStartIndex > 0) {
                            String yearsString = groupMember.substring(periodYearsStartIndex + 1, groupMember.length() - 1); //last char must be a ')' in this case -> -1
                            durationYears = "*".equals(yearsString) ? 0 : Integer.parseInt(yearsString); //a "*" means a single consent
                            groupMember = groupMember.substring(0, periodYearsStartIndex);
                        }
                        int groupMemberIndex = Integer.parseInt(groupMember);
                        provisionGroupIndexToGroupMemberIndices.put(groupIndex, groupMemberIndex);
                        provisionIndexToDurationYears.put(groupMemberIndex, durationYears);
                    }
                }
            }
        }

        /**
         * @param groupIndex
         * @return
         */
        public String getProvisionGroupColumnName(int groupIndex) {
            String columnNameResourceKey = CONSENT_PROVISION_COLUMN_KEY_PREFIX + groupIndex;
            return CONSENT_RESOURCES.getProperty(columnNameResourceKey);
        }

        /**
         * @param provisionIndex
         * @return
         */
        public String getProvisionDisplayText(int provisionIndex) {
            String displayTextResourceKey = CONSENT_PROVISION_TEXT_KEY_PREFIX + provisionIndex;
            return CONSENT_RESOURCES.getProperty(displayTextResourceKey);
        }

        /**
         * @param provisionIndex
         * @return
         */
        public String getProvisionCode(int provisionIndex) {
            return CONSENT_PROVISION_SYSTEM + "." + provisionIndex;
        }

        /**
         * @return
         */
        public String getProvisionSystem() {
            return "urn:oid:" + CONSENT_PROVISION_SYSTEM;
        }

    }

}
