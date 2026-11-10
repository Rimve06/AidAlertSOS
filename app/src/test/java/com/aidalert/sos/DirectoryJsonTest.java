package com.aidalert.sos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.aidalert.sos.directory.DirectoryJson;

import org.junit.Test;

/** Needs the org.json test dependency from app/build.gradle. */
public class DirectoryJsonTest {

    @Test
    public void hotlineNeedsSourceToCountAsVerified() throws Exception {
        String j = "{\"hotlines\":["
                + "{\"id\":\"a\",\"name\":\"A\",\"number\":\"999\",\"verified\":true,\"sourceUrl\":\"https://x\"},"
                + "{\"id\":\"b\",\"name\":\"B\",\"number\":\"102\",\"verified\":true},"
                + "{\"id\":\"c\",\"name\":\"C\",\"number\":\"not a number\"}]}";
        java.util.List<DirectoryJson.Hotline> list = DirectoryJson.parseHotlines(j);
        assertEquals(2, list.size());
        assertTrue(list.get(0).verified);
        assertFalse(list.get(1).verified);
    }

    @Test
    public void incompleteFacilitiesAreRejected() throws Exception {
        String j = "{\"source\":\"t\",\"facilities\":["
                + "{\"id\":\"1\",\"kind\":\"HOSPITAL\",\"name\":\"Good\",\"lat\":23.7,\"lon\":90.4,\"phone\":\"02-123456\"},"
                + "{\"id\":\"2\",\"kind\":\"HOSPITAL\",\"name\":\"No location\"},"
                + "{\"id\":\"3\",\"kind\":\"SCHOOL\",\"name\":\"Wrong kind\",\"lat\":1,\"lon\":1}]}";
        DirectoryJson.FacilityFile ff = DirectoryJson.parseFacilities(j);
        assertEquals(1, ff.facilities.size());
        assertEquals(2, ff.rejected);
        assertFalse(ff.facilities.get(0).phoneVerified); // phone present but not marked verified
    }
}
