package de.uni_leipzig.life.csv2fhir.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.math.BigDecimal;

import org.junit.Assert;
import org.junit.Test;

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
        Assert.assertThrows(Exception.class, () -> {
            DecimalUtil.parseDecimal("aaa");
        });

    }
}
