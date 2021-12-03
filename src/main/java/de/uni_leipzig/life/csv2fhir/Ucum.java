package de.uni_leipzig.life.csv2fhir;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author fheuschkel (02.11.2020), AXS
 */
public class Ucum {

    /**  */
    private static Logger LOG = LoggerFactory.getLogger(Ucum.class);

    /**
     * Name of the ucum map file in the resources which maps from non-UCUM text.
     */
    private static final CodeSystemMapper UCUM_CODE_MAPPER = new CodeSystemMapper("UCUM_Code.map");

    /**
     * @param ucum
     * @return
     */
    public static boolean isUcum(String ucum) {
        if (ucum == null || ucum.isBlank()) {
            return false;
        }
        String[] uArr = ucum.split("/", -1);
        for (String u : uArr) {
            String h = UCUM_CODE_MAPPER.getCodeToHuman(u);
            if (h == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param ucum
     * @return
     */
    public static String ucum2human(String ucum) {
        String[] uArr = ucum.split("/", -1);
        String human = "";
        for (String u : uArr) {
            String h = UCUM_CODE_MAPPER.getCodeToHuman(u.trim());
            if (h == null) {
                LOG.info("unknown ucum unit <" + u + "> in " + ucum + "; error ignored");
                h = u;
            }
            if (!human.isEmpty()) {
                human += "/";
            }
            human += h;
        }
        return human;
    }

    /**
     * @param human
     * @return
     */
    public static String human2ucum(String human) {
        String[] hArr = human.split("/", -1);
        String ucum = "";
        boolean first = true;
        for (String h : hArr) {
            String u = UCUM_CODE_MAPPER.getHumanToCode(h.trim());
            if (u == null) {
                LOG.info("unknown human readable unit <" + h + "> in " + human + "; ucum will be empty");
                return "";
            }

            if (first) {
                first = false;
            } else {
                ucum += "/";
            }

            ucum += u;
        }
        return ucum;
    }
}
