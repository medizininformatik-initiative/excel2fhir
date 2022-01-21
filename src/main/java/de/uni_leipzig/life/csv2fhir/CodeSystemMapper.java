package de.uni_leipzig.life.csv2fhir;

import de.uni_leipzig.life.csv2fhir.utils.BothDirectionResourceMapper;

/**
 * @author AXS (17.11.2021)
 */
public class CodeSystemMapper extends BothDirectionResourceMapper {

    /**
     * If there is a profile in such a map then this defualt key can/should be
     * used.
     */
    public static final String PROFILE_RESOURCE_KEY = "PROFILE";

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
        return getFirstBackwardKey(code);
    }

    /**
     * @param humanReadableText
     * @return
     */
    public String getHumanToCode(String humanReadableText) {
        return getForwardValue(humanReadableText);
    }

    /**
     * @return
     */
    public String getCodeSystem() {
        return getForwardValue(CODE_SYSTEM_URL_RESOURCE_KEY);
    }

    /**
     * @return
     */
    public String getProfile() {
        return getForwardValue(PROFILE_RESOURCE_KEY);
    }

}
