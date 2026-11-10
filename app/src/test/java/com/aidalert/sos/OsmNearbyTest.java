package com.aidalert.sos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.aidalert.sos.directory.Facility;
import com.aidalert.sos.directory.OsmNearby;

import org.junit.Test;

import java.util.List;

public class OsmNearbyTest {
    private static final String SAMPLE = "{\"elements\":["
            + "{\"type\":\"node\",\"id\":1,\"lat\":22.9,\"lon\":89.5,\"tags\":{\"amenity\":\"hospital\",\"name\":\"Khulna Medical\",\"phone\":\"+880 41 722 000; 041-111\"}},"
            + "{\"type\":\"way\",\"id\":2,\"center\":{\"lat\":22.8,\"lon\":89.6},\"tags\":{\"amenity\":\"fire_station\",\"name\":\"Fire Khulna\"}},"
            + "{\"type\":\"node\",\"id\":3,\"lat\":22.7,\"lon\":89.4,\"tags\":{\"amenity\":\"police\",\"contact:phone\":\"junk!\"}},"
            + "{\"type\":\"node\",\"id\":4,\"lat\":22.7,\"lon\":89.4,\"tags\":{\"amenity\":\"cafe\",\"name\":\"Cafe\"}},"
            + "{\"type\":\"node\",\"id\":5,\"tags\":{\"amenity\":\"clinic\",\"name\":\"No place\"}}"
            + "]}";

    @Test
    public void parsesKindsPhonesAndCenters() throws Exception {
        List<Facility> l = OsmNearby.parse(SAMPLE, 1000);
        assertEquals(4, l.size());      // the cafe is a map-only kind (FOOD) now
        assertEquals(Facility.FOOD, l.get(3).kind);
        assertEquals("osm:node/1", l.get(0).id);
        assertEquals(Facility.HOSPITAL, l.get(0).kind);
        assertEquals("+880 41 722 000", l.get(0).phone);
        assertFalse(l.get(0).phoneVerified);
        assertEquals(22.8, l.get(1).lat, 1e-9);
        assertFalse(l.get(1).hasPhone());
        assertEquals("Unnamed police station", l.get(2).name);
        assertFalse(l.get(2).hasPhone());
    }

    @Test
    public void queryAndCell() {
        String q = OsmNearby.query(22.8, 89.5, 15000);
        assertTrue(q.contains("around:15000,22.80000,89.50000"));
        assertTrue(q.contains("fire_station"));
        assertEquals("22.8,89.5", OsmNearby.cellKey(22.81, 89.54));
    }

    @Test
    public void bloodCentresAreRecognised() throws Exception {
        String json = "{\"elements\":[{\"type\":\"node\",\"id\":9,\"lat\":22.8,\"lon\":89.5,"
                + "\"tags\":{\"healthcare\":\"blood_donation\",\"name\":\"City Blood Bank\",\"phone\":\"01712345678\"}}]}";
        java.util.List<Facility> l = OsmNearby.parse(json, 1L);
        assertEquals(1, l.size());
        assertEquals(Facility.BLOOD, l.get(0).kind);
        org.junit.Assert.assertTrue(OsmNearby.query(22.8, 89.5, 8000).contains("blood_donation"));
    }

    @Test
    public void groupQueriesAreSmallAndBoxed() {
        String q = OsmNearby.groupQuery("medical", 22.8, 89.5, 6000);
        assertTrue(q.contains("hospital"));
        assertTrue(q.contains("node") && q.contains("way"));
        assertFalse(q.contains("around"));
        assertTrue(OsmNearby.groupQuery("shelter", 22.8, 89.5, 6000).contains("cyclone_shelter"));
        assertTrue(OsmNearby.groupQuery("blood", 22.8, 89.5, 6000).contains("blood_donation"));
        assertTrue(OsmNearby.groupQuery("blood", 22.8, 89.5, 6000).contains("blood_bank"));
        assertEquals(null, OsmNearby.groupQuery("nope", 22.8, 89.5, 6000));
        for (String g : OsmNearby.CORE_GROUPS) assertTrue(OsmNearby.groupQuery(g, 22.8, 89.5, 6000).startsWith("[out:json]"));
    }

    @Test
    public void shelterAndExtraKinds() throws Exception {
        String json = "{\"elements\":[{\"type\":\"node\",\"id\":9,\"lat\":22.9,\"lon\":89.5,\"tags\":{\"building\":\"cyclone_shelter\",\"name\":\"Shelter 1\"}},"
                + "{\"type\":\"node\",\"id\":10,\"lat\":22.9,\"lon\":89.5,\"tags\":{\"amenity\":\"pharmacy\"}},"
                + "{\"type\":\"node\",\"id\":11,\"lat\":22.9,\"lon\":89.5,\"tags\":{\"amenity\":\"shelter\",\"shelter_type\":\"bus_stop\"}}]}";
        List<Facility> l = OsmNearby.parse(json, 1L);
        assertEquals(2, l.size());          // a bus shelter is not a safe haven
        assertEquals(Facility.SHELTER, l.get(0).kind);
        assertEquals(Facility.PHARMACY, l.get(1).kind);
        assertEquals("Unnamed pharmacy", l.get(1).name);
    }
}
