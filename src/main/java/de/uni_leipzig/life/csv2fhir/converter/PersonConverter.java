package de.uni_leipzig.life.csv2fhir.converter;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.utils.DateUtil;
import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

import java.util.Collections;
import java.util.List;

public class PersonConverter implements Converter {

    private final CSVRecord record;

    public PersonConverter(CSVRecord record) {
        this.record = record;
    }

    @Override
    public List<Resource> convert() throws Exception {
        Patient patient = new Patient();
        patient.setId(parsePatientId());
        patient.addName(parseName());
        patient.setGender(parseSex());
        patient.setBirthDateElement(parseBirthDate());
        patient.addAddress(parseAddress());
        patient.addGeneralPractitioner(parseHealthProvider());
        return Collections.singletonList(patient);
    }

    private String parsePatientId() throws Exception {
        String id = record.get("Patient-ID");
        if (id != null) {
            return id;
        } else {
            throw new Exception("Error on Patient: Patient-ID empty for Record: "
                    + record.getRecordNumber() + "! " + record.toString());
        }
    }

    private HumanName parseName() {
        String forename = record.get("Vorname");
        String surname = record.get("Nachname");


        if (forename != null && surname != null) {
            HumanName humanName = new HumanName().setFamily(surname);
            for (String name : forename.split(" ")) {
                humanName.addGiven(name);
            }
            return humanName;
        } else {
            System.out.println("On Patient: Vorname or Nachname empty for Record: "
                    + record.getRecordNumber() + "! " + record.toString());
            return null;
        }
    }

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
                        throw new Exception("Error on Patient: Geschlecht <"+ sex+ ">not parsable for Record: "
                                + record.getRecordNumber() + "! " + record.toString());
                }
            } else {
                throw new Exception("Error on Patient: Geschlecht empty for Record: "
                        + record.getRecordNumber() + "! " + record.toString());
            }
        } else {
            throw new Exception("Error on Patient: Geschlecht not found!");
        }
    }

    private DateType parseBirthDate() throws Exception {
        String birthday = record.get("Geburtsdatum");
        if (birthday != null) {
            try {
                return DateUtil.parseDateType(birthday);
            } catch (Exception e) {
                throw new Exception("Error on Patient: Can not parse birthday for Record: "
                        + record.getRecordNumber() + "!\n" + record.toString());
            }
        } else {
            throw new Exception("Error on Patient: Geburtsdatum empty for Record: "
                    + record.getRecordNumber() + "! " + record.toString());
        }
    }

    private Address parseAddress() {
        String address = record.get("Anschrift");
        if (address != null) {
            String[] addressSplitByComma = address.split(",");
            if (addressSplitByComma.length == 2) {
                String[] addressPlzAndCity = addressSplitByComma[1].split(" ");
                String plz = addressPlzAndCity[1];
                StringBuilder city = new StringBuilder();
                for (int i = 2; i < addressPlzAndCity.length; i++) {
                    city.append(addressPlzAndCity[i]);
                }
                return new Address().setCity(city.toString()).setPostalCode(plz).setText(address);
            } else {
                System.out.println("On Patient: Can not parse Address for Record: "
                        + record.getRecordNumber() + "! " + record.toString());
                return null;
            }
        } else {
            System.out.println("On Patient: Anschrift empty for Record: "
                    + record.getRecordNumber() + "! " + record.toString());
            return null;
        }
    }

    private Reference parseHealthProvider() throws Exception {
        String practitioner = record.get("Krankenkasse");
        if (practitioner != null) {
            if (practitioner.length() != 0) {
                return new Reference().setDisplay(practitioner);
            } else {
                throw new Exception("Error on Patient: Krankenkasse empty for Record: "
                        + record.getRecordNumber() + "!\n" + record.toString());
            }
        } else {
            throw new Exception("Error on Patient: Collumn Krankenkasse not found!");
        }
    }
}
