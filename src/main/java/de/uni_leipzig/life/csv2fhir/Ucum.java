package de.uni_leipzig.life.csv2fhir;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class Ucum {
    private static Map<String, String> ucumMap = new HashMap<>();
    private static Map<String, String> humanMap = new HashMap<>();

    // http://download.hl7.de/documents/ucum/ucumdata.html
    // Mapping from "Valid UCUM Code to Common Synonym (non-UCUM) 
    static {
        // Times
        ucumMap.put("a", "year");
        ucumMap.put("mo", "month");
        ucumMap.put("wk", "week");
        ucumMap.put("d", "d");
        ucumMap.put("min", "min");
        ucumMap.put("h", "h");
        ucumMap.put("s", "s");
        ucumMap.put("ms", "ms");


        // Length
        ucumMap.put("m2", "m\u00b2");
        ucumMap.put("m", "m");
        ucumMap.put("cm", "cm");

        // Weight
        ucumMap.put("kg", "kg");
        ucumMap.put("g", "g");
        ucumMap.put("mg", "mg");
        ucumMap.put("ug", "µg");
        ucumMap.put("ng", "ng");
        ucumMap.put("pg", "pg");

        // Mol
        ucumMap.put("U", "U");
        ucumMap.put("uU", "µU");
        ucumMap.put("mmol", "mmol");
        ucumMap.put("umol", "µmol");
        ucumMap.put("nmol", "nmol");
        ucumMap.put("mosm", "mosmol"); // millio osmol

        // Other
        ucumMap.put("%", "%");
        ucumMap.put("cm[H2O]", "cmH2O");
        ucumMap.put("mm[Hg]", "mmHg");
        ucumMap.put("mbar", "mbar");
        ucumMap.put("Ohm", "\u03a9");
        ucumMap.put("Cel", "°C");
        ucumMap.put("10**9", "G");	
        ucumMap.put("10*12","T");
        ucumMap.put("deg", "\u00b0");
        ucumMap.put("[iU]", "IE");


        // Liter
        ucumMap.put("L", "l");
        ucumMap.put("dL", "dl");
        ucumMap.put("mL", "ml");
        ucumMap.put("uL", "µl");
        ucumMap.put("fL", "fl");


        //		ucumMap.put("", "");

        for (Entry<String, String> e : ucumMap.entrySet()) {
            humanMap.put(e.getValue(),e.getKey());
        }

        // Exceptions
        humanMap.put("I.E.","[iU]");
        humanMap.put("x 10^3","10*3");
        humanMap.put("x 10^6","10*6");
        humanMap.put("x10^12","10*12");
        humanMap.put("x10^9","10*9");
        humanMap.put("Mrd","10*9");
        humanMap.put("Sekunde(n)","s");
        humanMap.put("sec","s");
        humanMap.put("1","");	// like in 1/min
        humanMap.put("BPM","/m");	// like in 1/min
        humanMap.put("-","");	// eigentich empty

        //		humanMap.put("1/min","/min");
        //		humanMap.put("mmHg","mm[Hg]");
        //		humanMap.put("l","L");
        //		humanMap.put("ml","mL");
        //		humanMap.put("Jahre","a");
    }
    public static boolean isUcum(String ucum) {
        String[] uArr = ucum.split("/",-1);
        if (ucum == null || ucum.isBlank()) return false;
        for (String u : uArr) {
            String h = ucumMap.get(u);
            if (h == null) {
                return false;
            }
        }
        return true;		
    }
    public static String ucum2human(String ucum) {
        String[] uArr = ucum.split("/",-1);
        String human="";
        for (String u : uArr) {
            String h = ucumMap.get(u);
            if (h == null) {
                System.out.println("unknown ucum unit <" + u + "> in " + ucum + "; error ignored");
                h = u;
            }
            if (!human.isEmpty()) human +="/";
            human += h;
        }
        return human;
    }
    public static String human2ucum(String human) {
        String[] hArr = human.split("/",-1);
        String ucum = "";
        for (String h : hArr) {
            String u = humanMap.get(h);
            if (u == null) {
                System.out.println("unknown human readable unit <" + h + "> in " + human +"; ucum will be empty");
                return "";
            }

            if (!ucum.isEmpty()) ucum +="/";
            ucum += u;
        }
        return ucum;
    }
}
