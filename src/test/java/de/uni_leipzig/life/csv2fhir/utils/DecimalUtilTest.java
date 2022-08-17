package de.uni_leipzig.life.csv2fhir.utils;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.Assertions;
import org.testng.annotations.Test;

public class DecimalUtilTest {

    @Test
    public void parseComparatorTest() {
        String comp = DecimalUtil.parseComparator(" <= 6 ");
        assertEquals(comp, "<=");
        comp = DecimalUtil.parseComparator(" >= 6 ");
        assertEquals(comp, ">=");
        comp = DecimalUtil.parseComparator(" < 6 ");
        assertEquals(comp, "<");
        comp = DecimalUtil.parseComparator(" > 6 ");
        assertEquals(comp, ">");
        comp = DecimalUtil.parseComparator(" 6 ");
        assertNull(comp);
    }

    @Test
    public void parseDecimalTest() throws Exception {
        BigDecimal dec = DecimalUtil.parseDecimal("6.2");
        assertEquals(dec, new BigDecimal("6.2"));
        dec = DecimalUtil.parseDecimal("6,2");
        assertEquals(dec, new BigDecimal("6.2"));
        Assertions.assertThrows(Exception.class, () -> {
            DecimalUtil.parseDecimal("aaa");
        });

    }
}
