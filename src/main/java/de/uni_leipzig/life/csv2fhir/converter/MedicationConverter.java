package de.uni_leipzig.life.csv2fhir.converter;

import static de.uni_leipzig.life.csv2fhir.BundleFunctions.createReference;
import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Medikation;
import static de.uni_leipzig.life.csv2fhir.converterFactory.MedicationConverterFactory.Medication_Columns.ASK;
import static de.uni_leipzig.life.csv2fhir.converterFactory.MedicationConverterFactory.Medication_Columns.ATC_Code;
import static de.uni_leipzig.life.csv2fhir.converterFactory.MedicationConverterFactory.Medication_Columns.Anzahl_Dosen_pro_Tag;
import static de.uni_leipzig.life.csv2fhir.converterFactory.MedicationConverterFactory.Medication_Columns.Darreichungsform;
import static de.uni_leipzig.life.csv2fhir.converterFactory.MedicationConverterFactory.Medication_Columns.Einheit;
import static de.uni_leipzig.life.csv2fhir.converterFactory.MedicationConverterFactory.Medication_Columns.Einzeldosis;
import static de.uni_leipzig.life.csv2fhir.converterFactory.MedicationConverterFactory.Medication_Columns.FHIR_UserSelected;
import static de.uni_leipzig.life.csv2fhir.converterFactory.MedicationConverterFactory.Medication_Columns.Medikationstyp;
import static de.uni_leipzig.life.csv2fhir.converterFactory.MedicationConverterFactory.Medication_Columns.PZN_Code;
import static de.uni_leipzig.life.csv2fhir.converterFactory.MedicationConverterFactory.Medication_Columns.Therapieendedatum;
import static de.uni_leipzig.life.csv2fhir.converterFactory.MedicationConverterFactory.Medication_Columns.Therapiestartdatum;
import static de.uni_leipzig.life.csv2fhir.converterFactory.MedicationConverterFactory.Medication_Columns.Wirksubstanz_aus_Praeparat_Handelsname;
import static de.uni_leipzig.life.csv2fhir.converterFactory.MedicationConverterFactory.Medication_Columns.Zeitstempel;
import static de.uni_leipzig.life.csv2fhir.converterFactory.MedicationConverterFactory.Medikationstyp_Values.Verordnung;
import static de.uni_leipzig.life.csv2fhir.utils.DecimalUtil.parseDecimal;
import static java.util.Collections.singletonList;
import static org.hl7.fhir.r4.model.MedicationAdministration.MedicationAdministrationStatus.COMPLETED;
import static org.hl7.fhir.r4.model.MedicationStatement.MedicationStatementStatus.ACTIVE;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Dosage;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Medication.MedicationIngredientComponent;
import org.hl7.fhir.r4.model.MedicationAdministration;
import org.hl7.fhir.r4.model.MedicationAdministration.MedicationAdministrationDosageComponent;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Ratio;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.SimpleQuantity;
import org.hl7.fhir.r4.model.Type;

import com.google.common.base.Strings;

import de.uni_leipzig.imise.FHIRValidator;
import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterResult;
import de.uni_leipzig.life.csv2fhir.TableColumnIdentifier;
import de.uni_leipzig.life.csv2fhir.converterFactory.MedicationConverterFactory.Medication_Columns;
import de.uni_leipzig.life.csv2fhir.utils.DateUtil;

/**
 * MedicationStatement bei "Vor Aufnahme" MedicationAdminstration sonst
 */
public class MedicationConverter extends Converter {

    /**  */
    String PROFILE_ADM = "https://www.medizininformatik-initiative.de/fhir/core/modul-medikation/StructureDefinition/MedicationAdministration";

    /**  */
    String PROFILE_STM = "https://www.medizininformatik-initiative.de/fhir/core/modul-medikation/StructureDefinition/MedicationStatement";

    /**  */
    String PROFILE_MED = "https://www.medizininformatik-initiative.de/fhir/core/modul-medikation/StructureDefinition/Medication";
    // https://simplifier.net/medizininformatikinitiative-modulmedikation/medication-duplicate-3

    /*
     * Invalid : Instance count for 'Medication.ingredient' is 0, which is not
     * within the specified cardinality of 1..*
     */

    /**
     * @param record
     * @param validator
     * @throws Exception
     */
    public MedicationConverter(CSVRecord record, ConverterResult result, FHIRValidator validator) throws Exception {
        super(record, result, validator);
    }

    @Override
    public List<Resource> convert() throws Exception {
        List<Resource> resources = new ArrayList<>();
        String medicationId = getMedicationId();

        Medication medication = result.get(Medikation, Medication.class, medicationId);
        if (medication == null) {
            medication = parseMedication();
            if (!isValid(medication)) {
                return resources;
            }
            resources.add(medication);
        }
        if (Verordnung.equals(get(Medikationstyp))) {
            resources.add(parseMedicationStatement());
        } else {
            resources.add(parseMedicationAdministration());
        }
        return resources;
    }

    @Override
    protected Enum<?> getPatientIDColumnIdentifier() {
        return Medikation.getPIDColumnIdentifier();
    }

    @Override
    protected TableColumnIdentifier getMainEncounterNumberColumnIdentifier() {
        return Medication_Columns.Versorgungsfall_Nr;
    }

    /**
     * @return
     * @throws Exception
     */
    private Medication parseMedication() throws Exception {
        Medication medication = new Medication();
        medication.setMeta(new Meta().addProfile(PROFILE_MED));
        String medicationId = getMedicationId();
        medication.setId(medicationId);
        medication.setIdentifier(singletonList(new Identifier().setValue(medicationId))); // identifier is optional for medication
        medication.setCode(convertMedicationCodeableConcept());
        medication.setIngredient(singletonList(getIngredient()));
        return medication;
    }

    /**
     * @return
     * @throws Exception
     */
    private MedicationStatement parseMedicationStatement() throws Exception {
        MedicationStatement medicationStatement = new MedicationStatement();
        medicationStatement.setId(createId("-MS-", MedicationStatement.class));
        medicationStatement.setStatus(ACTIVE);
        medicationStatement.setMeta(new Meta().addProfile(PROFILE_STM));
        medicationStatement.setSubject(getPatientReference());
        medicationStatement.setContext(getEncounterReference());
        medicationStatement.setMedication(getMedicationReference());
        medicationStatement.setEffective(convertTimestamp());
        medicationStatement.addDosage(convertDosageStatement());
        return medicationStatement;
    }

    /**
     * @return
     * @throws Exception
     */
    private MedicationAdministration parseMedicationAdministration() throws Exception {
        MedicationAdministration medicationAdministration = new MedicationAdministration();
        medicationAdministration.setMeta(new Meta().addProfile(PROFILE_ADM));
        medicationAdministration.setId(createId("-MA-", MedicationAdministration.class));
        medicationAdministration.setStatus(COMPLETED);
        medicationAdministration.setSubject(getPatientReference());
        medicationAdministration.setContext(getEncounterReference());
        medicationAdministration.setMedication(getMedicationReference());
        medicationAdministration.setEffective(convertTimestamp());
        medicationAdministration.setDosage(convertDosageAdministration());
        return medicationAdministration;
    }

    /**
     * @param <T>
     * @param suffix
     * @param resourceType
     * @return
     * @throws Exception
     */
    private <T extends Resource> String createId(String suffix, Class<T> resourceType) throws Exception {
        String encounterID = getEncounterId();
        String superID = Strings.isNullOrEmpty(encounterID) ? getPatientId() : encounterID;
        int nextIDNumber = result.getNextId(Medikation, resourceType);
        return superID + suffix + nextIDNumber;
    }

    /**
     * @return
     */
    private MedicationIngredientComponent getIngredient() {
        MedicationIngredientComponent m = new MedicationIngredientComponent();
        try {
            Coding askCoding = getASKCoding();
            CodeableConcept askCodeableConcept = new CodeableConcept(askCoding);
            m.setItem(askCodeableConcept);
        } catch (Exception e) {
            warning("cannot set ATC");
        }
        try {
            m.setStrength(getDoseRate());
        } catch (Exception e) {
            warning("cannot set strength");
        }
        return m;
    }

    /**
     * @return
     */
    private CodeableConcept convertMedicationCodeableConcept() {
        CodeableConcept concept = new CodeableConcept();
        concept.addCoding(createCoding("http://fhir.de/CodeSystem/ifa/pzn", PZN_Code, FHIR_UserSelected));
        concept.addCoding(createCoding("http://fhir.de/CodeSystem/bfarm/atc", ATC_Code, FHIR_UserSelected));
        concept.setText(get(Wirksubstanz_aus_Praeparat_Handelsname));
        return concept;
    }

    /**
     * @return
     * @throws Exception
     */
    private String getMedicationId() throws Exception {
        String id;
        String atc = get(ATC_Code);
        if (atc != null) {
            id = atc;
        } else {
            String txt = get(Wirksubstanz_aus_Praeparat_Handelsname);
            if (txt == null) {
                error("ATC and Wirksubstanz aus Präparat/Handelsname empty");
                return null;
            }
            warning("ATC empty");
            id = txt;
        }
        return "Medication-" + id.hashCode();
    }

    /**
     * @return
     * @throws Exception
     */
    private Reference getMedicationReference() throws Exception {
        String medicationId = getMedicationId();
        if (Strings.isNullOrEmpty(medicationId)) {
            return null;
        }
        return createReference(Medication.class, medicationId);
    }

    /**
     * @return
     */
    private Coding getASKCoding() {
        String askCodeSystem = "http://fhir.de/CodeSystem/ask";
        Coding askCoding = createCoding(askCodeSystem, ASK, FHIR_UserSelected);
        if (askCoding == null) {
            // data absent reason to be KDS conform
            warning("no ask code -> set \"unknown\" data absent reason");
            askCoding = new Coding();
            askCoding.setSystem(askCodeSystem);
            CodeType codeElement = askCoding.getCodeElement();
            codeElement.addExtension(getUnknownDataAbsentReason());
        }
        return askCoding;
    }

    /**
     * @param codeSystem
     * @param codeColumnName
     * @param userSelectedIndicatorColumnName Name of the column which contains
     *            the indicator if the Coding is to set as
     *            {@link Coding#setUserSelected(boolean)}. To set the generated
     *            Coding as userSelected the value in this column must be
     *            contained in the
     *            <code>codeColumnName.toString()<code>. E.g the code column name
     *            <code>toString()</code> is "ATC-Code" and the value in the
     *            column with the name
     *            <code>userSelectedIndicatorColumnName</code> is "ATC" or "atc"
     *            or "ATC-Code" then the result Coding is set as user selected.
     * @return a new Coding with the given code system and code from the column
     *         with the codeColumnName or <code>null</code> if the code is
     *         missing.
     */
    public Coding createCoding(String codeSystem, Enum<?> codeColumnName, Enum<?> userSelectedIndicatorColumnName) {
        String code = get(codeColumnName);
        // Exception for PZN codes: These must be extended to 8 digits with leading 0s.
        if (code != null) {
            if (PZN_Code == codeColumnName) {
                code = Strings.padStart(code, 8, '0');
            }
            Coding coding = createCoding(codeSystem, code);
            String selectedColumnValue = get(userSelectedIndicatorColumnName);
            if (!Strings.isNullOrEmpty(selectedColumnValue)) {
                String codeColumnNameString = codeColumnName.toString();
                if (codeColumnNameString.toLowerCase().contains(selectedColumnValue.toLowerCase())) {
                    coding.setUserSelected(true);
                }
            }
            return coding;
        }
        return null;
    }

    //    /*
    //     * Wenn kein start/end vorhanden, dann nehme einfach mal an "start unbekannt", "end = Zeitstempel/heute"
    //     */
    //    private  Type convertDateTime() throws Exception {
    //        try {
    //
    //            String e = record.get("Zeitstempel");
    //            if (!StringUtils.isBlank(e)) {
    //                return DateUtil.parseDateTimeType(e);
    //                // Period with only end date
    //                // DateTimeType end = DateUtil.parseDateTimeType(e);
    //                //return new Period().setEndElement(end);
    //            }
    //        } catch (Exception e) {
    //            error("Can not parse Zeitstempel");
    //        }
    //        return null;
    //    }

    /**
     * In den Testdaten leider häufig falsch / täglich wiederholt genutzt
     *
     * @return
     * @throws Exception
     */
    private Type convertPeriod() throws Exception {
        try {
            String s = get(Therapiestartdatum);
            String e = get(Therapieendedatum);
            if (StringUtils.isBlank(s)) {
                if (StringUtils.isBlank(e)) {
                    // no date given
                    error("cannot administer without effective date");
                    return null;
                }
                // Period with only end date
                DateTimeType end = DateUtil.parseDateTimeType(e);
                return new Period().setEndElement(end);
            }
            DateTimeType start = DateUtil.parseDateTimeType(s);
            if (StringUtils.isBlank(e) || e.equals(s)) {
                // Just a single day
                return start;
            }
            DateTimeType end = DateUtil.parseDateTimeType(e);
            // complete Period
            return new Period().setStartElement(start).setEndElement(end);
        } catch (Exception e) {
            error("Can not parse " + Therapiestartdatum + " or " + Therapieendedatum);
        }
        return null;
    }

    /**
     * @return
     * @throws Exception
     */
    private Ratio getDoseRate() throws Exception {
        String ucumCode = get(Einheit);
        if (ucumCode != null) {
            return new Ratio()
                    .setNumerator(
                            getUcumQuantity(getDose(), ucumCode))
                    .setDenominator(
                            new Quantity().setValue(new BigDecimal(1))
                                    .setSystem("http://XXX")
                                    .setCode(get(Darreichungsform)));
        }
        error(Einheit + " empty for Record");
        return null;
    }

    /**
     * @return
     * @throws Exception
     */
    private BigDecimal getDose() throws Exception {
        try {
            return parseDecimal(get(Einzeldosis));
        } catch (Exception e) {
            error(Einzeldosis + " is not a numerical value for Record");
            return null;
        }
    }

    /**
     * @return
     * @throws Exception
     */
    private DateTimeType convertTimestamp() throws Exception {
        return parseDateTimeType(Zeitstempel);
    }

    /**
     * @return
     * @throws Exception
     */
    private Quantity convertQuantity() throws Exception {
        BigDecimal value = null;
        String doseCount = get(Anzahl_Dosen_pro_Tag);
        try {
            value = parseDecimal(doseCount);
        } catch (Exception e) {
            warning("no dose defined");
            return new SimpleQuantity().setUnit(doseCount);
        }
        String ucum = "1"; // see https://ucum.org/ucum.html#section-Examples-for-some-Non-Units.
        String synonym = get(Darreichungsform);
        return new SimpleQuantity().setValue(value).setUnit(synonym).setSystem("http://unitsofmeasure.org").setCode(ucum);
    }

    /**
     * @return
     * @throws Exception
     */
    private MedicationAdministrationDosageComponent convertDosageAdministration() throws Exception {
        return new MedicationAdministrationDosageComponent().setDose(convertQuantity());
    }

    /**
     * @return
     * @throws Exception
     */
    private Dosage convertDosageStatement() throws Exception {
        Dosage d = new Dosage();
        d.addDoseAndRate().setDose(convertQuantity());
        return d;
    }

}
