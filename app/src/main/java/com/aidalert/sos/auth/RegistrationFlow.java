package com.aidalert.sos.auth;

/**
 * Pure Java state machine for registration (unit-tested on the PC).
 *
 * Order the user asked for: verify phone and email FIRST, set the password AFTER.
 *   PHONE         -> prove the phone with an SMS code (Firebase Phone Auth)
 *   EMAIL         -> attach an email address to the same account
 *   VERIFY_EMAIL  -> wait until the user opened the link in the verification mail
 *   SET_PASSWORD  -> choose the real password and the display name
 *   DONE          -> profile saved, the session is kept until logout
 */
public final class RegistrationFlow {
    private RegistrationFlow() {}

    public enum Step { PHONE, EMAIL, VERIFY_EMAIL, SET_PASSWORD, DONE }

    /**
     * @param signedIn      a Firebase user exists on this phone
     * @param phoneVerified the user has a phone number linked (only set after a correct SMS code)
     * @param hasEmail      an email address is attached to the account
     * @param emailVerified Firebase says the email link was opened
     * @param profileDone   users/{uid} exists with setupDone=true
     */
    public static Step next(boolean signedIn, boolean phoneVerified, boolean hasEmail,
                            boolean emailVerified, boolean profileDone) {
        if (!signedIn || !phoneVerified) return Step.PHONE;
        if (!hasEmail) return Step.EMAIL;
        if (!emailVerified) return Step.VERIFY_EMAIL;
        if (!profileDone) return Step.SET_PASSWORD;
        return Step.DONE;
    }
}
