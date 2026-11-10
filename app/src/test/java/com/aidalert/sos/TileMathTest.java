package com.aidalert.sos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.aidalert.sos.view.TileMath;

import org.junit.Test;

public class TileMathTest {
    @Test
    public void khulnaFallsInTheRightTile() {
        // Khulna (22.8456, 89.5403) at zoom 10 is tile 766/445 (checked with an independent calculation)
        assertEquals(766, (int) Math.floor(TileMath.lonToX(89.5403, 10)));
        assertEquals(445, (int) Math.floor(TileMath.latToY(22.8456, 10)));
    }

    @Test
    public void conversionsRoundTrip() {
        double x = TileMath.lonToX(90.4, 12), y = TileMath.latToY(23.78, 12);
        assertEquals(90.4, TileMath.xToLon(x, 12), 1e-9);
        assertEquals(23.78, TileMath.yToLat(y, 12), 1e-9);
    }

    @Test
    public void urlsAndWrapping() {
        assertEquals("https://tile.openstreetmap.org/3/7/2.png", TileMath.url(3, -1, 2));
        assertEquals(0, TileMath.wrapX(8, 3));
        assertTrue(TileMath.validY(0, 3));
        assertEquals("5_3_4.png", TileMath.key(5, 3, 4));
    }
}
