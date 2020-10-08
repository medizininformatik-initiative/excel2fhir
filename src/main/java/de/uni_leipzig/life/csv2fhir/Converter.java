package de.uni_leipzig.life.csv2fhir;


import org.hl7.fhir.r4.model.*;
import java.util.List;

public interface Converter {

    List<Resource> convert() throws Exception;
}
