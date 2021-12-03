package de.uni_leipzig.life.csv2fhir;

import de.uni_leipzig.life.csv2fhir.utils.BothDirectionResourceMapper;

/**
 * @author AXS (17.11.2021)
 */
public class CodeSystemMapper extends BothDirectionResourceMapper {

    /**
     * As a convention, every code system map should contain this key, which
     * maps to the valid code system URL.
     */
    public static final String CODE_SYSTEM_URL_RESOURCE_KEY = "CODE_SYSTEM_URL";

    /**
     * @param resourceFileName
     */
    public CodeSystemMapper(String resourceFileName) {
        super(resourceFileName);
    }

    /**
     * @param code
     * @return
     */
    public String getCodeToHuman(String code) {
        return super.getFirstBackwardKey(code);
    }

    /**
     * @param humanReadableText
     * @return
     */
    public String getHumanToCode(String humanReadableText) {
        return super.getForwardValue(humanReadableText);
    }

    /**
     * @return
     */
    public String getCodeSystem() {
        return getHumanToCode(CODE_SYSTEM_URL_RESOURCE_KEY);
    }

}
