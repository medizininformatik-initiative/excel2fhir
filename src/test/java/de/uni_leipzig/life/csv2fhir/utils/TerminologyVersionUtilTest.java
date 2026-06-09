package de.uni_leipzig.life.csv2fhir.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TerminologyVersionUtilTest {

    @Test
    public void usesEventYearWithinSupportedRange() throws Exception {
        assertEquals("2019", TerminologyVersionUtil.getIcd10GmVersion(DateUtil.parseDateTimeType("23.03.2019 00:00")));
        assertEquals("2019", TerminologyVersionUtil.getOpsVersion(DateUtil.parseDateTimeType("23.03.2019 00:00")));
        assertEquals("2019", TerminologyVersionUtil.getAtcVersion(DateUtil.parseDateTimeType("23.03.2019 00:00")));
    }

    @Test
    public void clampsEventYearToAvailableValueSetVersions() throws Exception {
        assertEquals("2018", TerminologyVersionUtil.getAtcVersion(DateUtil.parseDateTimeType("23.03.2017 00:00")));
        assertEquals("2025", TerminologyVersionUtil.getIcd10GmVersion(DateUtil.parseDateTimeType("23.03.2026 00:00")));
    }
}
