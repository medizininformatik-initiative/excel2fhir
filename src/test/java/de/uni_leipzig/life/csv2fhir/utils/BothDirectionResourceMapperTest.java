package de.uni_leipzig.life.csv2fhir.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import de.uni_leipzig.life.csv2fhir.CodeSystemMapper;

public class BothDirectionResourceMapperTest {
    @Test
    public void departmentLabelsPreserveUnicodeAndPropertyEscapes() {
        CodeSystemMapper mapper = new CodeSystemMapper("EncounterLevel2_Department.map");
        assertEquals("0500", mapper.getHumanToCode("Hämatologie und Onkologie"));
        assertEquals("1000", mapper.getHumanToCode("Pädiatrie"));
        assertEquals("2300", mapper.getHumanToCode("Orthopädie"));
        assertEquals("0100", mapper.getHumanToCode("Innere Medizin"));
        assertEquals("2900", mapper.getHumanToCode("Psychiatrie"));
        assertEquals("Hämatologie und internistische Onkologie", mapper.getCodeToHuman("0500"));
        assertNull(mapper.getHumanToCode("Unbekannte Fachabteilung"));
    }

    @Test
    public void unitSynonymsPreserveMicroSign() {
        BothDirectionResourceMapper mapper = new BothDirectionResourceMapper("ucum/UCUM_Synonyms_manual.map");
        assertEquals("10*3/uL", mapper.getForwardValue("x10^3/µl"));
        assertEquals("/uL", mapper.getForwardValue("1/µl"));
        assertEquals("1/µl", mapper.getFirstBackwardKey("/uL"));
    }
}
