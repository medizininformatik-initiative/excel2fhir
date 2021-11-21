package de.uni_leipzig.life.csv2fhir;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.Resource;

import de.uni_leipzig.life.csv2fhir.converterFactory.AbteilungsfallConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.DiagnoseConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.KlinischeDokumentationConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.LaborbefundConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.MedikationConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.PersonConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.ProzedurConverterFactory;
import de.uni_leipzig.life.csv2fhir.converterFactory.VersorgungsfallConverterFactory;

/**
 * @author AXS (18.11.2021)
 */
public enum TableIdentifier {
    Person(new PersonConverterFactory(), PersonConverterFactory.NeededColumns.Patient_ID),
    Versorgungsfall(new VersorgungsfallConverterFactory(), VersorgungsfallConverterFactory.NeededColumns.Patient_ID),
    Abteilungsfall(new AbteilungsfallConverterFactory(), AbteilungsfallConverterFactory.NeededColumns.Patient_ID),
    Laborbefund(new LaborbefundConverterFactory(), LaborbefundConverterFactory.NeededColumns.Patient_ID),
    Diagnose(new DiagnoseConverterFactory(), DiagnoseConverterFactory.NeededColumns.Patient_ID),
    Prozedur(new ProzedurConverterFactory(), ProzedurConverterFactory.NeededColumns.Patient_ID),
    Medikation(new MedikationConverterFactory(), MedikationConverterFactory.NeededColumns.Patient_ID),
    Klinische_Dokumentation(new KlinischeDokumentationConverterFactory(), KlinischeDokumentationConverterFactory.NeededColumns.Patient_ID);

    /** Maps from the reosurce ID to the resource */
    private final Map<String, Resource> idToResourceMap = new HashMap<>();

    /** The converterFactory for this type */
    private final ConverterFactory converterFactory;

    /** The column identifier for the patien ID in the table */
    private final Enum<?> pidColumnIdentifier;

    /**
     * @param converterFactory
     * @param pidColumnIdentifier
     */
    private TableIdentifier(ConverterFactory converterFactory, Enum<?> pidColumnIdentifier) {
        this.converterFactory = converterFactory;
        this.pidColumnIdentifier = pidColumnIdentifier;
    }

    /**
     * @return the identifier for the PID column in the table
     */
    public final Enum<?> getPIDColumnIdentifier() {
        return pidColumnIdentifier;
    }

    /**
     * @return the name of the PID colun in the table
     */
    public String getPIDColumnName() {
        return getPIDColumnIdentifier().toString();
    }

    /**
     * @param csvRecord
     * @return
     * @throws Exception
     */
    public List<Resource> convert(CSVRecord csvRecord) throws Exception {
        Converter converter = converterFactory.create(csvRecord);
        List<Resource> resources = converter.convert();
        for (Resource resource : resources) {
            String id = resource.getId();
            idToResourceMap.put(id, resource);
        }
        return resources;
    }

    /**
     * Clears the map from IDs to resources.
     */
    public void clear() {
        idToResourceMap.clear();
    }

    /**
     * @param id
     * @return
     */
    public Resource getResource(String id) {
        return idToResourceMap.get(id);
    }

    /**
     * @param id
     * @return
     */
    public Set<String> getIDs(String id) {
        return idToResourceMap.keySet();
    }

    /**
     * @return
     */
    public Collection<Resource> getResources() {
        return idToResourceMap.values();
    }

    @Override
    public String toString() {
        return name().replace('_', ' ');
    }

    /**
     * @return
     */
    public String getCsvFileName() {
        return toString() + ".csv";
    }

    /**
     * @return
     */
    public String getExcelSheetName() {
        return toString();
    }

    /**
     * @return
     * @see de.uni_leipzig.life.csv2fhir.ConverterFactory#getNeededColumns()
     */
    public Enum<?>[] getNeededColumns() {
        return converterFactory.getNeededColumns();
    }

    /**
     * @return
     * @see de.uni_leipzig.life.csv2fhir.ConverterFactory#getNeededColumnNames()
     */
    public List<String> getNeededColumnNames() {
        return converterFactory.getNeededColumnNames();
    }

    /**
     * @return
     */
    public static List<String> getCsvFileNames() {
        List<String> csvFileNames = new ArrayList<>();
        for (TableIdentifier csvFileName : values()) {
            csvFileNames.add(csvFileName.getCsvFileName());
        }
        return csvFileNames;
    }

    /**
     * @return
     */
    public static List<String> getExcelSheetNames() {
        List<String> excelSheetNames = new ArrayList<>();
        for (TableIdentifier csvFileName : values()) {
            excelSheetNames.add(csvFileName.getExcelSheetName());
        }
        return excelSheetNames;
    }

}
