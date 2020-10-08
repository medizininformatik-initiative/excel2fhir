package de.uni_leipzig.life.csv2fhir.converter;

import de.uni_leipzig.life.csv2fhir.Converter;
import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.Resource;
import java.util.Collections;
import java.util.List;

public class AbteilungsfallConverter implements Converter {

    private final CSVRecord record;

    public AbteilungsfallConverter(CSVRecord record) {
        this.record = record;
    }

    @Override
    public List<Resource> convert() throws Exception {
        //TODO
        return Collections.singletonList(null);
    }
}
