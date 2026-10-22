package com.aidalert.sos.channel;

import android.content.Context;

import com.aidalert.sos.data.Incident;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Shared throttling logic (template method); subclasses only implement send(). */
public abstract class AbstractChannel implements DeliveryChannel {
    private final Map<String, Long> lastSent = new ConcurrentHashMap<>();
    private final long minIntervalMs;

    protected AbstractChannel(long minIntervalMs) {
        this.minIntervalMs = minIntervalMs;
    }

    @Override
    public final void deliver(Context c, Incident i, boolean important) throws DeliveryException {
        long now = System.currentTimeMillis();
        Long last = lastSent.get(i.id);
        if (!important && last != null && now - last < minIntervalMs) return; // throttled
        send(c.getApplicationContext(), i);
        lastSent.put(i.id, now);
    }

    /** Do the actual transmission or throw DeliveryException. */
    protected abstract void send(Context app, Incident i) throws DeliveryException;
}
