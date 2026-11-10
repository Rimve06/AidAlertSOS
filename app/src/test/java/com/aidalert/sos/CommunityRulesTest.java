package com.aidalert.sos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.aidalert.sos.community.PostRules;

import org.junit.Test;

/** The community board accepts purposeful posts only. */
public class CommunityRulesTest {

    @Test
    public void validHelpRequestIsAccepted() {
        assertNull(PostRules.check(PostRules.NEED, "Companiganj, Sylhet",
                "Flood water is inside our house, two elderly people need a boat tonight."));
    }

    @Test
    public void smallTalkIsRejected() {
        assertNotNull(PostRules.check(PostRules.INFO, "", "hi"));
        assertNotNull(PostRules.check(PostRules.INFO, "", "hello hello hello hello"));
        assertNotNull(PostRules.check(PostRules.INFO, "", "hi anyone there thanks bhai ok"));
        assertNotNull(PostRules.check(PostRules.FEEDBACK, "", "ok thanks you good nice"));
    }

    @Test
    public void tooShortOrTooLongIsRejected() {
        assertNotNull(PostRules.check(PostRules.INFO, "", "need help now"));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 60; i++) sb.append("water ");
        assertNotNull(PostRules.check(PostRules.INFO, "Dhaka", sb.toString()));
        assertNull(PostRules.check(PostRules.FEEDBACK, "", sb.toString().trim() + " and more details about the delay"));
    }

    @Test
    public void linksAndPhoneNumbersAreRejectedInPublicPostsOnly() {
        assertNotNull(PostRules.check(PostRules.INFO, "", "Relief camp details at http://example.com please read"));
        assertNotNull(PostRules.check(PostRules.OFFER, "Dhaka", "I have a boat, call me on 01712 345678 anytime"));
        assertNull(PostRules.check(PostRules.FEEDBACK, "", "The hotline number 01712345678 never answered during the flood"));
    }

    @Test
    public void helpPostsNeedAnArea() {
        assertNotNull(PostRules.check(PostRules.NEED, "", "We need drinking water for forty families here"));
        assertNull(PostRules.check(PostRules.INFO, "", "Road to the upazila hospital is blocked by fallen trees"));
    }

    @Test
    public void unknownTypeAndRateLimit() {
        assertNotNull(PostRules.check("CHAT", "Dhaka", "This is a long enough message about something"));
        assertEquals(0, PostRules.waitSeconds(0, 1_000_000));
        assertEquals(30, PostRules.waitSeconds(1000, 1000));
        assertEquals(10, PostRules.waitSeconds(1000, 21_000));
    }
}
