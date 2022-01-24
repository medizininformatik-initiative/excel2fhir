package de.uni_leipzig;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

import de.uni_leipzig.life.csv2fhir.ucum.UcumCodesExtractor;
import de.uni_leipzig.life.csv2fhir.utils.BothDirectionResourceMapper;

/**
 * @author AXS (10.12.2021)
 */
public class UcumMapper {

    /** Logger for this class */
    private static Logger LOG = LoggerFactory.getLogger(UcumMapper.class);

    /** Collection of all invalid UCUM codes during FHIR creation */
    public static List<String> invalidUcumCodes = new ArrayList<>();

    /**
     * Name of the map file that maps from a synonym to the correct UCUM code.
     * This file is wtitten by the socond process.
     */
    public static final String UCUM_MANUAL_CREATED_SYNONYM_TO_UCUM_CODE_MAP_RESOURCE_FILE_NAME = "ucum/UCUM_Synonyms_manual.map";

    /**
     * Maps from a correct UCUM unit to its human readable code. One entry could
     * be UCUM unit 'kg' -> UCUM code 'kilogram'.
     */
    private final BothDirectionResourceMapper validUcumCodeToDisplay = new BothDirectionResourceMapper(UcumCodesExtractor.UCUM_CODE_TO_DISPLAY_MAP_RESOURCE_FILE_NAME);

    /** Maps from a synonym to the correct ucum unit */
    private final BothDirectionResourceMapper synonymUcumCodeToValidUcumCode = new BothDirectionResourceMapper(UcumCodesExtractor.UCUM_AUTOMATIC_CREATED_SYNONYM_TO_UCUM_CODE_MAP_RESOURCE_FILE_NAME,
            UCUM_MANUAL_CREATED_SYNONYM_TO_UCUM_CODE_MAP_RESOURCE_FILE_NAME);

    /** Singleton */
    private static UcumMapper mapper;

    /**
     * @param ucumCode
     * @return
     * @throws Exception
     */
    public static String getValidUcumCode(String ucumCode) throws Exception {
        String validUcumCode = getValidUcumCodeInternal(ucumCode);
        if (Strings.isNullOrEmpty(validUcumCode)) {

            // replace all invalid chars by their valid UCUM chars
            String correctedUcumCode = ucumCode.replace('´', '\'').replace('`', '\'').replace('²', '2').replace('³', '3').replace('µ', 'u');

            // special handling for degree sigm
            int degreeSignIndex = correctedUcumCode.indexOf('°');
            if (degreeSignIndex >= 0) {
                int celDegreeIndex = correctedUcumCode.indexOf("°C");
                // if there is a "°C" (but not "°Cel") -> replace by correct UCUM "Cel"
                if (celDegreeIndex >= 0 && celDegreeIndex != correctedUcumCode.indexOf("°Cel")) {
                    correctedUcumCode = correctedUcumCode.replace("°C", "Cel");
                } else { // all other cases (eg. "°K" or "°F" or "°Cel" or "°XXX") -> simple remove degree sign
                    correctedUcumCode = correctedUcumCode.replace("°", "");
                }
            }

            //is it now a valid UCUM code ?
            validUcumCode = getValidUcumCodeInternal(correctedUcumCode);
            if (Strings.isNullOrEmpty(validUcumCode)) {
                //maybe there are spaces in the ucum code -> try the same procedure without spaces
                if (ucumCode.contains(" ")) {
                    correctedUcumCode = ucumCode.replace(" ", "");
                    validUcumCode = getValidUcumCodeInternal(correctedUcumCode);
                }
            }
        }
        if (Strings.isNullOrEmpty(validUcumCode)) {
            LOG.error("Invalid UCUM code " + ucumCode);
            if (!invalidUcumCodes.contains(ucumCode)) {
                invalidUcumCodes.add(ucumCode);
            }
            return ucumCode;
        }
        return validUcumCode;
    }

    /**
     * @param ucumCode
     * @return
     * @throws Exception
     */
    private static String getValidUcumCodeInternal(String ucumCode) throws Exception {
        if (mapper == null) {
            mapper = new UcumMapper();
        }
        //it is already a valid UCUM code ?
        if (mapper.validUcumCodeToDisplay.containsKey(ucumCode)) {
            return ucumCode;
        }
        //try the synonyms
        return mapper.synonymUcumCodeToValidUcumCode.get(ucumCode);
    }

    /**
     * @param ucumCode
     * @return
     * @throws Exception
     */
    public static String getUcumUnit(String ucumCode) throws Exception {
        String correctUcumCode = getValidUcumCode(ucumCode);
        String unit = mapper.validUcumCodeToDisplay.get(correctUcumCode);
        return Strings.isNullOrEmpty(unit) ? ucumCode : unit;
    }

}
