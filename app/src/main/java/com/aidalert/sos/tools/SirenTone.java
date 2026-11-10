package com.aidalert.sos.tools;

/** Pure Java: one cycle of a police-style siren (pitch glides between 700 and 1500 Hz) as 16-bit PCM. */
public final class SirenTone {
    private SirenTone() {}

    public static final double LOW_HZ = 700, HIGH_HZ = 1500, CYCLE_S = 1.2;

    public static short[] cycle(int sampleRate) {
        int n = (int) Math.round(sampleRate * CYCLE_S);
        short[] out = new short[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / n;                       // 0..1 across the cycle
            double tri = t < 0.5 ? t * 2 : (1 - t) * 2;      // 0 -> 1 -> 0
            double f = LOW_HZ + (HIGH_HZ - LOW_HZ) * tri;
            phase += 2 * Math.PI * f / sampleRate;
            out[i] = (short) Math.round(Math.sin(phase) * 0.9 * Short.MAX_VALUE);
        }
        return out;
    }
}
