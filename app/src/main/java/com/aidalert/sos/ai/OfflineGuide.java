package com.aidalert.sos.ai;

import com.aidalert.sos.learn.LearnContent;

import java.util.Locale;

/**
 * Offline answers: finds the best matching Emergency Learning guide for the question. This is search over
 * written guides, not a language model, so it only answers topics the guides cover.
 */
public final class OfflineGuide {
    private OfflineGuide() {}

    public static final class Answer {
        public final String text;
        public final String topicId;     // "" when nothing matched

        Answer(String text, String topicId) {
            this.text = text;
            this.topicId = topicId;
        }
    }

    // topic id -> words people use (English, Bangla, Banglish)
    private static final String[][] SYNONYMS = {
            {"cpr", "cpr", "not breathing", "unconscious", "no pulse", "heart stopped", "faint", "collapsed", "সিপিআর", "শ্বাস"},
            {"choking", "choke", "choking", "stuck in throat", "swallowed", "গলায়", "আটকে"},
            {"bleeding", "bleed", "bleeding", "blood loss", "cut", "wound", "রক্ত"},
            {"burns", "burn", "scald", "boiling water", "পুড়ে", "পোড়া"},
            {"fracture", "broken bone", "fracture", "sprain", "ভেঙে", "হাড়"},
            {"snake", "snake", "snakebite", "সাপ"},
            {"drowning", "drown", "drowning", "water", "ডুবে", "পানিতে"},
            {"electric", "electric", "shock", "wire", "current", "বিদ্যুৎ"},
            {"heat", "heatstroke", "heat stroke", "too hot", "sunstroke", "হিট"},
            {"seizure", "seizure", "fit", "convulsion", "epilepsy", "খিঁচুনি"},
            {"poison", "poison", "swallowed chemical", "overdose", "pesticide", "বিষ"},
            {"stroke", "stroke", "heart attack", "chest pain", "face drooping", "স্ট্রোক", "বুকে ব্যথা"},
            {"dengue", "dengue", "mosquito", "fever with rash", "ডেঙ্গু"},
            {"fire", "fire", "smoke", "burning building", "আগুন"},
            {"earthquake", "earthquake", "quake", "tremor", "ভূমিকম্প"},
            {"cyclone", "cyclone", "flood", "storm", "signal", "ঘূর্ণিঝড়", "বন্যা"},
            {"lightning", "lightning", "thunder", "বজ্র"},
            {"road", "accident", "crash", "road accident", "দুর্ঘটনা"},
            {"violence", "harass", "violence", "abuse", "attacked", "threat", "হয়রানি"},
    };

    public static Answer answer(String question) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        String best = "";
        int bestScore = 0;
        for (String[] row : SYNONYMS) {
            int score = 0;
            for (int i = 1; i < row.length; i++) {
                if (q.contains(row[i])) score += row[i].length() >= 5 ? 2 : 1;
            }
            if (score > bestScore) {
                bestScore = score;
                best = row[0];
            }
        }
        if (best.isEmpty()) {
            for (LearnContent.Topic t : LearnContent.ALL) {
                if (q.contains(t.title.toLowerCase(Locale.ROOT))) {
                    best = t.id;
                    break;
                }
            }
        }
        LearnContent.Topic t = best.isEmpty() ? null : LearnContent.byId(best);
        if (t == null) {
            return new Answer("I am offline, so I can only answer from the saved emergency guides, and I did not find one for that.\n\n"
                    + "If someone is in danger, call 999 now (fire service 102). You can also open Emergency learning and browse "
                    + "the guides: CPR, bleeding, burns, snakebite, drowning, fire, earthquake and more.", "");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(t.title).append("\n\nCall first: ").append(t.callFirst).append("\n");
        int n = Math.min(t.steps.length, 6);
        for (int i = 0; i < n; i++) sb.append("\n").append(i + 1).append(". ").append(t.steps[i]);
        if (t.steps.length > n) sb.append("\n... more steps in the full guide.");
        sb.append("\n\nOffline answer from the saved guides.");
        return new Answer(sb.toString(), t.id);
    }
}
