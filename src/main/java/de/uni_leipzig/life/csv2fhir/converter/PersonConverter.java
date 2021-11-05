package de.uni_leipzig.life.csv2fhir.converter;

import java.util.Collections;
import java.util.List;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Address.AddressType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.HumanName.NameUse;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Identifier.IdentifierUse;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.utils.DateUtil;

public class PersonConverter extends Converter {

    /**  */
    String PROFILE = "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Patient";
    // @see https://simplifier.net/MedizininformatikInitiative-ModulPerson/PatientIn

    /**
     * @param record
     * @throws Exception
     */
    public PersonConverter(CSVRecord record) throws Exception {
        super(record);
    }

    @Override
    public List<Resource> convert() throws Exception {
        Patient patient = new Patient();
        patient.setMeta(new Meta().addProfile(PROFILE));
        patient.setId(getPatientId());
        patient.setIdentifier(parseIdentifier());
        patient.addName(parseName());
        patient.setGender(parseSex());
        patient.setBirthDateElement(parseBirthDate());
        patient.addAddress(parseAddress());
        //        patient.addGeneralPractitioner(parseHealthProvider());
        return Collections.singletonList(patient);
    }

    /**
     * @return
     * @throws Exception
     */
    private List<Identifier> parseIdentifier() throws Exception {
        Identifier i = new Identifier();
        i.setValue(getPatientId()).setSystem("https://" + getDIZId() + ".de/pid").setUse(IdentifierUse.USUAL).setType(
                new CodeableConcept(new Coding().setCode("MR").setSystem("http://terminology.hl7.org/CodeSystem/v2-0203")));
        return Collections.singletonList(i);
    }

    /**
     * @return
     */
    private HumanName parseName() {
        String forename = record.get("Vorname");
        String surname = record.get("Nachname");

        if (forename != null && surname != null) {
            HumanName humanName = new HumanName().setFamily(surname).setUse(NameUse.OFFICIAL);
            for (String name : forename.split(" ")) {
                humanName.addGiven(name);
            }
            return humanName;
        }
        warning("Vorname or Nachname empty for Record");
        return null;
    }

    /**
     * @return
     * @throws Exception
     */
    private Enumerations.AdministrativeGender parseSex() throws Exception {
        String sex = record.get("Geschlecht");
        if (sex != null) {
            if (sex.length() != 0) {
                switch (sex) {
                case "m":
                case "männlich":
                    return Enumerations.AdministrativeGender.MALE;
                case "w":
                case "weiblich":
                    return Enumerations.AdministrativeGender.FEMALE;
                case "d":
                case "divers":
                    return Enumerations.AdministrativeGender.OTHER;
                default:
                    throw new Exception("Error on Patient: Geschlecht <" + sex + ">not parsable for Record: " + record
                            .getRecordNumber() + "! " + record.toString());
                }
            }
            warning("Geschlecht empty for Record");
            return null;
        }
        warning("Geschlecht not found");
        return null;
    }

    /**
     * @return
     * @throws Exception
     */
    private DateType parseBirthDate() throws Exception {
        String birthday = record.get("Geburtsdatum");
        if (birthday != null) {
            try {
                return DateUtil.parseDateType(birthday);
            } catch (Exception e) {
                error("Can not parse birthday for Record");
                return null;
            }
        }
        error("Geburtsdatum empty for Record");
        return null;
    }

    /**
     * @return
     */
    private Address parseAddress() {
        String address = record.get("Anschrift");
        Address a;
        if (address != null) {
            a = new Address();
            String[] addressSplitByComma = address.split(",");
            if (addressSplitByComma.length == 2) {
                String[] addressPlzAndCity = addressSplitByComma[1].split(" ");
                String plz = addressPlzAndCity[1];
                StringBuilder city = new StringBuilder();
                for (int i = 2; i < addressPlzAndCity.length; i++) {
                    city.append(addressPlzAndCity[i]);
                }
                List<StringType> l = Collections.singletonList(new StringType(addressSplitByComma[0]));
                a.setCity(city.toString()).setPostalCode(plz).setLine(l);
            } else {
                // "12345 ORT"
                String[] addressPlzAndCity = address.split(" ");
                if (addressPlzAndCity.length == 2) {
                    String plz = addressPlzAndCity[0];
                    String city = addressPlzAndCity[1];
                    a.setCity(city).setPostalCode(plz).setText(address);
                } else {
                    a.setText(address);
                }
            }
            return a.setType(AddressType.BOTH).setCountry("DE");
        }
        warning("On Patient: Anschrift empty for Record");
        return null;
    }

    // not used yet
    /**
     * @return
     * @throws Exception
     */
    @SuppressWarnings("unused")
    private Reference parseHealthProvider() throws Exception {
        String practitioner = record.get("Krankenkasse");
        if (practitioner != null) {
            if (practitioner.length() != 0) {
                return new Reference().setDisplay(practitioner);
            }
            error("Krankenkasse empty for Record");
            return null;
        }
        warning("Column Krankenkasse not found!");
        return null;
    }
}
