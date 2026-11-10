package com.aidalert.sos.support;

import java.util.Locale;

/**
 * Looks for signs of suicidal thinking in what the user types, on the phone only. This is a keyword
 * screen, not a diagnosis: it can miss things and it can fire on harmless text, so a person is always
 * asked before anything is sent. Pure Java.
 */
public final class RiskRules {
    private RiskRules() {}

    public static final int NONE = 0, CONCERN = 1, HIGH = 2;

    private static final String[] HIGH_WORDS = {
            "kill myself", "killing myself", "end my life", "end it all", "take my life", "taking my life",
            "hang myself", "jump off", "jump from", "slit my", "cut my wrist", "overdose", "swallow all", "tonight i will die",
            "i will die tonight", "going to die tonight", "goodbye forever", "this is my last message", "my suicide note",
            "better off without me", "have a plan to die", "suicide plan", "going to kill myself",
            "আত্মহত্যা করব", "আত্মহত্যা করবো", "নিজেকে শেষ", "জীবন শেষ করে", "মরে যাব আজ",
            "sesh kore dibo", "suicide korbo", "atmohotta korbo", "nijeke sesh", "jibon sesh kore"
    };
    private static final String[] CONCERN_WORDS = {
            "want to die", "wanna die", "wish i were dead", "wish i was dead", "better off dead", "suicid", "no reason to live",
            "can't go on", "cant go on", "tired of living", "hate my life", "give up on life", "nothing matters", "worthless",
            "hopeless", "i am a burden", "i'm a burden", "no one would miss me", "cannot take it anymore", "can't take it anymore",
            "মরে যেতে চাই", "মরতে চাই", "আর বাঁচতে চাই না", "আত্মহত্যা", "বেঁচে থাকতে চাই না",
            "morte chai", "mora uchit", "ar bachte chai na", "bachte ichha nai", "atmohotta"
    };
    private static final String[] NEGATIONS = {"don't ", "dont ", "do not ", "never ", "not ", "no longer ", "isn't ", "won't ", "wont ", "না "};

    public static int level(String text) {
        if (text == null) return NONE;
        String l = text.toLowerCase(Locale.ROOT);
        if (find(l, HIGH_WORDS)) return HIGH;
        if (find(l, CONCERN_WORDS)) return CONCERN;
        return NONE;
    }

    private static boolean find(String l, String[] words) {
        for (String w : words) {
            int from = 0;
            while (true) {
                int i = l.indexOf(w, from);
                if (i < 0) break;
                if (!negated(l, i)) return true;
                from = i + w.length();
            }
        }
        return false;
    }

    private static boolean negated(String l, int index) {
        int start = Math.max(0, index - 14);
        String before = l.substring(start, index);
        for (String n : NEGATIONS) if (before.contains(n)) return true;
        return false;
    }

    /** Remembers the conversation's risk. A person is asked about contacting someone when needed. */
    public static final class Tracker {
        private int score;

        /** @return NONE, CONCERN (keep supporting) or HIGH (offer to alert a person now). */
        public int add(String text) {
            int lv = level(text);
            if (lv == HIGH) {
                score += 3;
                return HIGH;
            }
            if (lv == CONCERN) {
                score += 1;
                return score >= 3 ? HIGH : CONCERN;
            }
            return score >= 3 ? HIGH : NONE;
        }

        public int score() {
            return score;
        }

        public void reset() {
            score = 0;
        }
    }
}
