package heuschkel.life.de.Convertable;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import heuschkel.life.de.Convertable2Fhir;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.*;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Map;

public class Person implements Convertable2Fhir {
    private final FhirContext ctx;
    private final File file;
    public Person(File file){
        this.ctx = FhirContext.forR4();
        this.file = file;
    }

    private void parsePatientId(Map<String,String> data, Patient patient) throws Exception {
        if(data.containsKey("Patient-ID")){
            if(data.get("Patient-ID").length() != 0){
                patient.setId(data.get("Patient-ID"));
            }else{
                throw new Exception("Error: Patient-ID empty for Record: " + data.get("_intern:RecordNr") +"!");
            }
        }else{
            throw new Exception("Error: Collumn Patient-ID not found!");
        }
    }
    private void parsePatientName(Map<String,String> data, Patient patient) throws Exception {
        if(data.containsKey("Vorname") && data.containsKey("Nachname")){
            if(data.get("Vorname").length() != 0 && data.get("Nachname").length() != 0){
                HumanName humanName = patient.addName().setFamily(data.get("Nachname"));
                for(String name : data.get("Vorname").split(" ")){
                    humanName.addGiven(name);
                }
            }else{
                //throw new Exception("Error: Vorname or Nachname empty for Record: " + data.get("_intern:RecordNr") +"!");
                System.out.println("Vorname or Nachname empty for Record: " + data.get("_intern:RecordNr") +"!");
            }
        }else{
            throw new Exception("Error: Collumn Patient-ID not found!");
        }
    }

    private void parsePatientAddres(Map<String,String> data, Patient patient) throws Exception {
        if(data.containsKey("Anschrift")){
            if(data.get("Anschrift").length() != 0){
                String[] addressSplitByComma = data.get("Anschrift").split(",");
                if(addressSplitByComma.length == 2){
                    String[] addressPlzAndCity = addressSplitByComma[1].split(" ");
                    String plz = addressPlzAndCity[1];
                    StringBuilder city = new StringBuilder();
                    for(int i = 2; i<addressPlzAndCity.length;i++ ){
                        city.append(addressPlzAndCity[i]);
                    }
                    patient.addAddress().setCity(city.toString()).setPostalCode(plz).setText(data.get("Anschrift"));
                }else{
                    throw new Exception("Error: Can not parse Address for Record: " + data.get("_intern:RecordNr") +"!");
                }
            }else{
                throw new Exception("Error: Anschrift empty for Record: " + data.get("_intern:RecordNr") +"!");
            }
        }else{
            throw new Exception("Error: Collumn Anschrift not found!");
        }
    }

    private void parsePatientBirthDate(Map<String,String> data, Patient patient) throws Exception {
        if(data.containsKey("Geburtsdatum")){
            if(data.get("Geburtsdatum").length() != 0){
                try{
                    Year year =Year.parse(data.get("Geburtsdatum"));
                    Date date = Date.from(year.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
                    patient.setBirthDateElement(new DateType(date,TemporalPrecisionEnum.YEAR));
                }catch (DateTimeParseException eYear){
                    try{
                        YearMonth yearMonth = YearMonth.parse(data.get("Geburtsdatum"));
                        Date date = Date.from(yearMonth.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
                        patient.setBirthDateElement(new DateType(date,TemporalPrecisionEnum.MONTH));
                    }catch (DateTimeParseException eMonth){
                        try{
                            LocalDate localDate = LocalDate.parse(data.get("Geburtsdatum"));
                            Date date = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
                            patient.setBirthDateElement(new DateType(date,TemporalPrecisionEnum.DAY));
                        }catch (DateTimeParseException eDay){
                            throw new Exception("");
                        }
                    }
                }
            }else{
                throw new Exception("Error: Geburtsdatum empty for Record: " + data.get("_intern:RecordNr") +"!");
            }
        }else{
            throw new Exception("Error: Collumn Geburtsdatum not found!");
        }
    }

    private void parsePatientSex(Map<String,String> data, Patient patient) throws Exception {
        if(data.containsKey("Geschlecht")){
            if(data.get("Geschlecht").length() != 0){
                switch (data.get("Geschlecht")) {
                    case "m", "männlich" -> patient.setGender(Enumerations.AdministrativeGender.MALE);
                    case "w", "weiblich" -> patient.setGender(Enumerations.AdministrativeGender.FEMALE);
                    case "d", "divers" -> patient.setGender(Enumerations.AdministrativeGender.OTHER);
                    default -> throw new Exception("Error: Geschlecht not parsable!");
                }
            }else{
                throw new Exception("Error: Geschlecht empty for Record: " + data.get("_intern:RecordNr") +"!");
            }
        }else{
            throw new Exception("Error: Collumn Geschlecht not found!");
        }
    }

    private void parsePatientHealthProvider(Map<String,String> data, Patient patient) throws Exception {
        if(data.containsKey("Krankenkasse")){
            if(data.get("Krankenkasse").length() != 0){
                patient.addGeneralPractitioner().setDisplay(data.get("Krankenkasse"));
            }else{
                throw new Exception("Error: Krankenkasse empty for Record: " + data.get("_intern:RecordNr") +"!");
            }
        }else{
            throw new Exception("Error: Collumn Krankenkasse not found!");
        }
    }
    private Patient parsePatient(CSVRecord record) throws Exception {
        Patient patient = new Patient();
        Map<String,String> data = record.toMap();
        data.put("_intern:RecordNr",String.valueOf(record.getRecordNumber()));
        parsePatientId(data,patient);
        parsePatientName(data,patient);
        parsePatientAddres(data,patient);
        parsePatientBirthDate(data,patient);
        parsePatientSex(data,patient);
        parsePatientHealthProvider(data,patient);
        return patient;
    }

    public String convert() throws Exception {
        if(file != null && file.exists()){
            Reader in = new FileReader(file);
            Iterable<CSVRecord> records = CSVFormat.DEFAULT
                    .withAllowMissingColumnNames(true).withFirstRecordAsHeader().parse(in);
            Bundle bundle = new Bundle();
            bundle.setType(Bundle.BundleType.TRANSACTION);

            for (CSVRecord record : records){
               Patient tmpPatient = parsePatient(record);
               bundle.addEntry().setResource(tmpPatient).setRequest(new Bundle.BundleEntryRequestComponent().setMethod(Bundle.HTTPVerb.PUT).setUrl("TODO"));

            }

            return(ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle));
        }else{
            throw new Exception("Person File is null or does not exist!");
        }
    }
}
