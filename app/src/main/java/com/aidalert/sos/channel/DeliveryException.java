package com.aidalert.sos.channel;

/** Checked exception: one delivery channel could not get the alert out (the next channel is tried). */
public class DeliveryException extends Exception {
    public DeliveryException(String message) {
        super(message);
    }

    public DeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
