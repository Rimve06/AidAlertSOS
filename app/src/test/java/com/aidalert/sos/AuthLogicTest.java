package com.aidalert.sos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.aidalert.sos.auth.PasswordRules;
import com.aidalert.sos.auth.PhoneFormat;
import com.aidalert.sos.auth.RegistrationFlow;
import com.aidalert.sos.auth.RegistrationFlow.Step;
import com.aidalert.sos.auth.Roles;
import com.aidalert.sos.data.Prefs;

import org.junit.Test;

/** Registration order, phone formatting, password policy and role separation. */
public class AuthLogicTest {

    @Test
    public void registrationStepsFollowTheRequiredOrder() {
        assertEquals(Step.PHONE, RegistrationFlow.next(false, false, false, false, false));
        assertEquals(Step.PHONE, RegistrationFlow.next(true, false, false, false, false));
        assertEquals(Step.EMAIL, RegistrationFlow.next(true, true, false, false, false));
        assertEquals(Step.VERIFY_EMAIL, RegistrationFlow.next(true, true, true, false, false));
        assertEquals(Step.SET_PASSWORD, RegistrationFlow.next(true, true, true, true, false));
        assertEquals(Step.DONE, RegistrationFlow.next(true, true, true, true, true));
    }

    @Test
    public void cannotFinishWithoutBothVerifications() {
        assertEquals(Step.PHONE, RegistrationFlow.next(true, false, true, true, true));
        assertEquals(Step.VERIFY_EMAIL, RegistrationFlow.next(true, true, true, false, true));
    }

    @Test
    public void phoneFormatting() {
        assertEquals("+8801712345678", PhoneFormat.toE164("01712345678"));
        assertEquals("+8801712345678", PhoneFormat.toE164("+880 1712-345678"));
        assertEquals("+8801712345678", PhoneFormat.toE164("8801712345678"));
        assertEquals("+8801712345678", PhoneFormat.toE164("008801712345678"));
        assertEquals("+8801712345678", PhoneFormat.toE164("1712345678"));
        assertEquals("", PhoneFormat.toE164("0171234"));
        assertEquals("", PhoneFormat.toE164("01212345678"));
        assertEquals("", PhoneFormat.toE164("abc"));
        assertEquals("", PhoneFormat.toE164(null));
        assertEquals("+14155552671", PhoneFormat.toE164("+1 415 555 2671"));
    }

    @Test
    public void passwordPolicy() {
        assertNotNull(PasswordRules.check("short1", "short1", "a@b.com"));
        assertNotNull(PasswordRules.check("abcdefgh", "abcdefgh", "a@b.com"));
        assertNotNull(PasswordRules.check("abcd1234", "abcd12345", "a@b.com"));
        assertNotNull(PasswordRules.check("rimve0606x1", "rimve0606x1", "rimve0606@gmail.com"));
        assertNotNull(PasswordRules.check("password1", "password1", "a@b.com"));
        assertNull(PasswordRules.check("tiger-Lamp-42", "tiger-Lamp-42", "rimve@gmail.com"));
    }

    @Test
    public void rolesComeOnlyFromTheServerAndTabsMustMatch() {
        assertEquals(Prefs.ROLE_CITIZEN, Roles.fromServer(null));
        assertEquals(Prefs.ROLE_CITIZEN, Roles.fromServer("admin"));
        assertEquals(Prefs.ROLE_CENTER, Roles.fromServer("CENTER"));
        assertNull(Roles.check(false, Prefs.ROLE_CITIZEN));
        assertNull(Roles.check(true, Prefs.ROLE_CENTER));
        assertNotNull(Roles.check(true, Prefs.ROLE_CITIZEN));
        assertNotNull(Roles.check(false, Prefs.ROLE_CENTER));
    }
}
