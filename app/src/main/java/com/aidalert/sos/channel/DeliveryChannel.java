package com.aidalert.sos.channel;

import android.content.Context;

import com.aidalert.sos.data.Incident;

/** One way of getting an SOS to the control center. Implementations are interchangeable (polymorphism). */
public interface DeliveryChannel {
    String name();

    /** Cheap check: is this channel usable right now? */
    boolean isAvailable(Context c);

    /**
     * @param important true for the first alert, a type change or the end of an SOS; false for plain
     *                  position updates, which a channel may throttle.
     */
    void deliver(Context c, Incident i, boolean important) throws DeliveryException;
}
