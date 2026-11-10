package com.aidalert.sos.community;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Pure Java rules for the purpose-based community board. The board is not a chat room: a post must be
 * a request for help, an offer of help, or useful local information; feedback goes to the control center.
 */
public final class PostRules {
    private PostRules() {}

    public static final String NEED = "HELP_NEEDED";
    public static final String OFFER = "HELP_OFFER";
    public static final String INFO = "INFO";
    public static final String FEEDBACK = "FEEDBACK";

    public static final int MIN_LEN = 15;
    public static final int MAX_LEN = 280;
    public static final int MAX_FEEDBACK_LEN = 500;
    public static final long MIN_GAP_MS = 30_000;

    private static final Set<String> SMALL_TALK = new HashSet<>(Arrays.asList(
            "hi", "hii", "hello", "hey", "yo", "sup", "ok", "okay", "thanks", "thank", "you", "lol", "haha",
            "salam", "assalamualaikum", "kemon", "acho", "bhai", "good", "morning", "night", "bye", "test",
            "testing", "anyone", "there", "who", "is", "here", "nice", "wow", "hmm"));

    public static String label(String type) {
        switch (type) {
            case NEED: return "Needs help";
            case OFFER: return "Offers help";
            case INFO: return "Local info";
            case FEEDBACK: return "Feedback";
            default: return type;
        }
    }

    public static boolean isPublicType(String type) {
        return NEED.equals(type) || OFFER.equals(type) || INFO.equals(type);
    }

    /** @return null if the post may be sent, otherwise what to tell the user */
    public static String check(String type, String area, String text) {
        boolean feedback = FEEDBACK.equals(type);
        if (!feedback && !isPublicType(type)) return "Choose what this post is for.";
        String t = text == null ? "" : text.trim();
        int max = feedback ? MAX_FEEDBACK_LEN : MAX_LEN;
        if (t.length() < MIN_LEN) return "Say a little more (at least " + MIN_LEN + " characters): what happened, where, what is needed.";
        if (t.length() > max) return "Too long. Keep it under " + max + " characters.";
        String[] words = t.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}\\s]", " ").trim().split("\\s+");
        if (words.length < 3) return "Write a full sentence about the need, offer or information.";
        boolean allSmallTalk = true;
        for (String w : words) if (!SMALL_TALK.contains(w)) { allSmallTalk = false; break; }
        if (allSmallTalk) return "This board is for help requests, help offers, local information or feedback. Greetings and small talk are not allowed.";
        if (!feedback) {
            if (t.toLowerCase(Locale.ROOT).matches("(?s).*(https?://|www\\.|\\.com\\b|\\.net\\b|\\.xyz\\b|t\\.me/|bit\\.ly).*")) {
                return "Links are not allowed in public posts.";
            }
            if (t.replaceAll("[^0-9\u09E6-\u09EF]", "").length() >= 7 && t.matches("(?s).*[0-9\u09E6-\u09EF][0-9\u09E6-\u09EF \\-]{5,}[0-9\u09E6-\u09EF].*")) {
                return "Do not post phone numbers publicly. Use a private message after the control center or a helper replies.";
            }
            String a = area == null ? "" : area.trim();
            if ((NEED.equals(type) || OFFER.equals(type)) && (a.length() < 2 || a.length() > 40)) {
                return "Add the area (2 to 40 characters), e.g. the upazila or neighbourhood.";
            }
        }
        return null;
    }

    /** @return seconds the user must still wait, or 0 if posting is allowed */
    public static int waitSeconds(long lastPostAt, long now) {
        long left = MIN_GAP_MS - (now - lastPostAt);
        return left <= 0 ? 0 : (int) ((left + 999) / 1000);
    }
}
