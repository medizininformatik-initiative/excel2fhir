package de.uni_leipzig.life.synthifhir.converter;

import static de.uni_leipzig.life.synthifhir.TableIdentifier.Person;

import static de.uni_leipzig.life.synthifhir.converter.PatientConverter.Person_Columns.Geburtsdatum;
import static de.uni_leipzig.life.synthifhir.converter.PatientConverter.Person_Columns.Geschlecht;
import static de.uni_leipzig.life.synthifhir.converter.PatientConverter.Person_Columns.Nachname;
import static de.uni_leipzig.life.synthifhir.converter.PatientConverter.Person_Columns.Vorname;
import static org.hl7.fhir.r4.model.Enumerations.AdministrativeGender.FEMALE;
import static org.hl7.fhir.r4.model.Enumerations.AdministrativeGender.MALE;
import static org.hl7.fhir.r4.model.Enumerations.AdministrativeGender.OTHER;
import static org.hl7.fhir.r4.model.Enumerations.AdministrativeGender.UNKNOWN;

import java.util.Collections;
import java.util.List;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.HumanName.NameUse;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.DateType;


import de.uni_leipzig.imise.validate.FHIRValidator;
import de.uni_leipzig.life.synthifhir.Converter;
import de.uni_leipzig.life.synthifhir.ConverterOptions;
import de.uni_leipzig.life.synthifhir.ConverterResult;
import de.uni_leipzig.life.synthifhir.TableColumnIdentifier;

/**
 * @author jheuschkel (19.10.2020), AXS (05.11.2021)
 */
public class PatientConverter extends Converter {

    /**
     * toString() result of these enum values are the names of the columns in the
     * correspunding excel sheet.
     */
    public static enum Person_Columns implements TableColumnIdentifier {
        Vorname,
        Nachname,
        Geburtsdatum,
        Geschlecht
    }

    // ISO 3166-2:DE, as bound by the German address profile. Keep human-readable Excel values.
    private static final java.util.Map<String, String> GERMAN_STATES = java.util.Map.ofEntries(
            java.util.Map.entry("Baden-Württemberg", "DE-BW"), java.util.Map.entry("Bayern", "DE-BY"),
            java.util.Map.entry("Berlin", "DE-BE"), java.util.Map.entry("Brandenburg", "DE-BB"),
            java.util.Map.entry("Bremen", "DE-HB"), java.util.Map.entry("Hamburg", "DE-HH"),
            java.util.Map.entry("Hessen", "DE-HE"), java.util.Map.entry("Mecklenburg-Vorpommern", "DE-MV"),
            java.util.Map.entry("Niedersachsen", "DE-NI"), java.util.Map.entry("Nordrhein-Westfalen", "DE-NW"),
            java.util.Map.entry("Rheinland-Pfalz", "DE-RP"), java.util.Map.entry("Saarland", "DE-SL"),
            java.util.Map.entry("Sachsen", "DE-SN"), java.util.Map.entry("Sachsen-Anhalt", "DE-ST"),
            java.util.Map.entry("Schleswig-Holstein", "DE-SH"), java.util.Map.entry("Thüringen", "DE-TH"));

    /**  */
    String PROFILE = "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Patient";
    // @see
    // https://simplifier.net/MedizininformatikInitiative-ModulPerson/PatientIn

    /**
     * @param record
     * @param previousRecordPID
     * @param result
     * @param validator
     * @param options
     * @throws Exception
     */
    public PatientConverter(CSVRecord record, String previousRecordPID, ConverterResult result, FHIRValidator validator,
            ConverterOptions options) throws Exception {
        super(record, previousRecordPID, result, validator, options);
    }

    @Override
    protected List<Resource> convertInternal() throws Exception {
        Patient patient = new Patient();
        patient.setMeta(new Meta().addProfile(PROFILE));
        patient.setId(getPatientId());
        HumanName name = parseName();
        if (!name.isEmpty()) patient.addName(name);
        patient.setGenderElement(InputCode.of(patient.getGenderElement(), parseGender()));
        String birthDate = get(Geburtsdatum);
        var birthAbsent = DiagnosisValues.absentReason(birthDate);
        if (birthAbsent != null) patient.setBirthDateElement((DateType)new DateType().addExtension(birthAbsent));
        else if (birthDate != null && !birthDate.isBlank()) patient.setBirthDateElement(parseDate(Geburtsdatum));
        Address address = parseAddress();
        if (!address.isEmpty()) patient.addAddress(address);
        patient.setDeceased(ClinicalValues.date(ClinicalValues.get(this, ClinicalValues.Column.Sterbezeitpunkt)));
        // String resourceAsJson =
        // OutputFileType.JSON.getParser().setPrettyPrint(true).encodeResourceToString(patient);
        // // for debug
        // Sys.out1(resourceAsJson);
        return Collections.singletonList(patient);
    }

    /**
     * @return
     */
    private HumanName parseName() {
        String forename = get(Vorname);
        String surname = get(Nachname);
        HumanName humanName = new HumanName();
        var familyAbsent = explicitNameAbsent(surname);
        if (familyAbsent != null) humanName.setFamilyElement((StringType)new StringType().addExtension(familyAbsent));
        else if (surname != null && !surname.isBlank()) humanName.setFamily(surname);
        var givenAbsent = explicitNameAbsent(forename);
        if (givenAbsent != null) humanName.addGivenElement().addExtension(givenAbsent);
        else if (forename != null && !forename.isBlank()) {
            for (String name : forename.split(" ")) humanName.addGiven(name);
        }
        if (!humanName.isEmpty()) humanName.setUse(NameUse.OFFICIAL);
        return humanName;
    }

    private static org.hl7.fhir.r4.model.Extension explicitNameAbsent(String value) {
        // Ordinary names remain literal; only an explicit marker denotes missing data.
        return value != null && (value.startsWith(DiagnosisValues.DAR_PREFIX) || value.endsWith(" (Data Absent Reason)"))
                ? DiagnosisValues.absentReason(value) : null;
    }

    /**
     * @return
     * @throws Exception
     */
    private String parseGender() throws Exception {
        String gender = get(Geschlecht);
        if (gender != null) {
            if (!gender.isBlank()) {
                switch (gender) {
                case "m":
                case "male":
                case "männlich":
                    return MALE.toCode();
                case "w":
                case "weiblich":
                case "female":
                case "f":
                    return FEMALE.toCode();
                case "d":
                case "divers":
                case "other":
                case "unbestimmt":
                    return OTHER.toCode();
                case "x":
                case "unbekannt":
                case "unknown":
                    return UNKNOWN.toCode();
                default:
                    return gender;
                }
            }
            return null;
        }
        return null;
    }

    /**
     * @return
     */
    private Address parseAddress() {
        Address address = new Address();
        String street = ClinicalValues.get(this, ClinicalValues.Column.Straße);
        if (street != null) address.getLine().add(ClinicalValues.text(street));
        address.setPostalCodeElement(ClinicalValues.text(ClinicalValues.get(this, ClinicalValues.Column.Postleitzahl)));
        address.setCityElement(ClinicalValues.text(ClinicalValues.get(this, ClinicalValues.Column.Ort)));
        String country = ClinicalValues.get(this, ClinicalValues.Column.Land);
        address.setCountryElement(ClinicalValues.text(country));
        String state = ClinicalValues.get(this, ClinicalValues.Column.Bundesland);
        address.setStateElement(ClinicalValues.text("DE".equals(country) ? GERMAN_STATES.getOrDefault(state == null ? "" : state, state) : state));
        return address;
    }

}
