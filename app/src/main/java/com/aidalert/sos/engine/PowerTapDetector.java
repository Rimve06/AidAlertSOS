package com.aidalert.sos.engine;

/**
 * Counts consecutive power-button presses.
 *
 * Rules (from the project brief):
 *  - N presses in a row trigger the SOS,
 *  - the presses must be sequential: if the user does anything else in between (touch, another key,
 *    unlock) the count resets to zero (onOtherInput),
 *  - a pause longer than maxGapMs between two presses also resets the count.
 *
 * Pure Java on purpose, so the rule is unit tested on a PC without an emulator.
 */
public class PowerTapDetector {
    private final int required;
    private final long maxGapMs;
    private int count;
    private long lastPressMs;

    public PowerTapDetector(int required, long maxGapMs) {
        this.required = required;
        this.maxGapMs = maxGapMs;
    }

    /** @return true when this press completed the sequence (the counter then restarts). */
    public synchronized boolean onPowerPress(long nowMs) {
        if (count > 0 && nowMs - lastPressMs > maxGapMs) {
            count = 0;
        }
        count++;
        lastPressMs = nowMs;
        if (count >= required) {
            count = 0;
            return true;
        }
        return false;
    }

    /** Any other input (touch, key, unlock) breaks the sequence. */
    public synchronized void onOtherInput() {
        count = 0;
    }

    public synchronized int getCount() {
        return count;
    }
}
