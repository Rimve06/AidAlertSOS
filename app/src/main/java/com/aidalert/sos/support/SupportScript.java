package com.aidalert.sos.support;

/**
 * Fixed, hand-written supportive replies. This is NOT an AI and NOT a therapist. It listens, helps the
 * user slow down, and points to people. Helpline details come from a public directory listing and can change:
 * check them before relying on them.
 */
public final class SupportScript {
    private SupportScript() {}

    public static final String[][] HELPLINES = {
            {"999 National Emergency Service", "999", "Immediate danger. Any hour."},
            {"Kaan Pete Roi (emotional support)", "+8809612119911", "Listed hours: 3 PM to 3 AM, every day."},
            {"Vent (Mindspace)", "09678678778", "Listed hours: 6 PM to 6 AM."},
            {"SHOJON (SAJIDA Foundation)", "09606119900", "Listed hours: 10 AM to 10 PM."},
            {"16263 Shastho Batayon", "16263", "Health advice line, 24 hours. Not a crisis line."}
    };

    public static final String WELCOME = "Hello. I am a support companion in this app. I am not a person and not a therapist, "
            + "but I can listen, and I can help you reach people. What is on your mind?\n\n"
            + "What you type here stays on this phone. If you tell me you may hurt yourself, I will ask whether you want "
            + "me to alert your emergency contact and the control center.";

    private static final String[] LISTEN = {
            "Thank you for telling me. That sounds heavy. Do you want to say more about what happened?",
            "I am here with you. What has been the hardest part today?",
            "That makes sense given what you are carrying. When did it start to feel this way?",
            "You do not have to solve everything tonight. What would make the next hour a little easier?",
            "I hear you. Is there someone you trust who knows how you are feeling?"
    };

    public static String listen(int turn) {
        return LISTEN[Math.floorMod(turn, LISTEN.length)];
    }

    public static final String CONCERN_REPLY = "I am really sorry you are in this much pain. Thoughts like these can feel permanent but they "
            + "often ease, especially when you are not alone with them.\n\nAre you thinking about ending your life? "
            + "You can answer honestly. I will not judge you.";

    public static final String HIGH_REPLY = "I am worried about your safety, and I want you to have a real person with you right now.";

    public static final String GROUNDING = "If it helps, try this for one minute: breathe in for 4 counts, hold 4, out for 6. "
            + "Then name 5 things you can see and 3 things you can hear. Put distance between you and anything you could use to hurt yourself.";

    public static final String GROUNDING_BN = "একটু থামুন। ৪ গুনে শ্বাস নিন, ৪ গুনে ধরে রাখুন, ৬ গুনে ছাড়ুন। তারপর চারপাশে ৫টি জিনিস দেখুন আর ৩টি শব্দ শুনুন। "
            + "নিজেকে আঘাত করতে পারে এমন কিছু থেকে দূরে সরে যান।";

    public static final String OVERWHELMED = "When everything feels like too much, shrink it. Pick just one small thing for the next 10 minutes: "
            + "a glass of water, washing your face, or stepping outside. Everything else can wait. What is one small thing you can do now?";

    public static final String SAD = "I am sorry you are feeling sad. You do not have to explain it perfectly. What has been weighing on you?";

    public static final String NEED_SOMEONE = "Reaching out is a strong thing to do. I can show helplines you can call, or alert your emergency "
            + "contact and the control center. Tap \"Helplines\" or \"Get a person now\" below.";

    public static String helplineText() {
        StringBuilder sb = new StringBuilder();
        for (String[] h : HELPLINES) sb.append(h[0]).append(": ").append(h[1]).append("\n   ").append(h[2]).append('\n');
        sb.append("\nNumbers and hours come from a public listing and may have changed.");
        return sb.toString();
    }
}
