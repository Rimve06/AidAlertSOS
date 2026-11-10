package com.aidalert.sos.learn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Offline emergency guides. Short, conservative first-aid and safety steps based on standard Red Cross / WHO style guidance.
 * They are reminders, not training: the app tells people to call 999 (ambulance 102 where it applies) first.
 */
public final class LearnContent {
    private LearnContent() {}

    public static final class Topic {
        public final String id, title, bangla, group, callFirst;
        public final String[] steps, donts;

        Topic(String id, String title, String bangla, String group, String callFirst, String[] steps, String[] donts) {
            this.id = id;
            this.title = title;
            this.bangla = bangla;
            this.group = group;
            this.callFirst = callFirst;
            this.steps = steps;
            this.donts = donts;
        }

        public boolean matches(String q) {
            if (q == null || q.trim().isEmpty()) return true;
            String l = q.trim().toLowerCase(Locale.ROOT);
            return title.toLowerCase(Locale.ROOT).contains(l) || bangla.contains(l) || group.toLowerCase(Locale.ROOT).contains(l);
        }
    }

    public static final String DISCLAIMER = "These are short reminders, not a replacement for first-aid training or for medical care. "
            + "In a real emergency call 999 first (ambulance through 999, fire service 102).";

    private static String[] a(String... s) {
        return s;
    }

    public static final List<Topic> ALL = new ArrayList<>();

    static {
        ALL.add(new Topic("cpr", "CPR for an adult who is not breathing", "সিপিআর", "First aid",
                "Call 999 now, or ask someone else to. Ask for an AED (defibrillator) if one is nearby.",
                a("Make sure the place is safe for you.",
                        "Tap the shoulders firmly and shout. If there is no response and no normal breathing (not just gasping), start CPR.",
                        "Lay the person on their back on a firm surface. Kneel beside the chest.",
                        "Put the heel of one hand on the centre of the chest, the other hand on top, arms straight.",
                        "Push hard and fast: about 5 to 6 cm deep, 100 to 120 pushes a minute. Let the chest come back up fully each time.",
                        "If you are trained, give 2 rescue breaths after every 30 pushes. If you are not trained or unwilling, keep pushing without breaths.",
                        "If an AED arrives, switch it on and follow its spoken instructions.",
                        "Keep going until help takes over, the person starts to breathe normally, or you cannot continue."),
                a("Do not stop to check the pulse again and again.", "Do not give food, water or medicine.")));
        ALL.add(new Topic("choking", "Choking adult", "গলায় খাবার আটকানো", "First aid",
                "If the person cannot breathe, cough or speak, or turns blue, call 999.",
                a("Ask: are you choking? If they can cough, encourage coughing.",
                        "If they cannot cough or breathe: lean them forward and give up to 5 firm back blows between the shoulder blades with the heel of your hand.",
                        "If that fails, give up to 5 abdominal thrusts: stand behind, fist just above the belly button, pull sharply inwards and upwards.",
                        "Repeat 5 back blows and 5 thrusts until the object comes out or help arrives.",
                        "If the person becomes unresponsive, lay them down, call 999 and begin CPR."),
                a("Do not slap the back of someone who is coughing well.", "Do not put your fingers in the mouth unless you can see the object.")));
        ALL.add(new Topic("bleeding", "Severe bleeding", "রক্তক্ষরণ", "First aid",
                "Call 999 for bleeding that is heavy or will not stop.",
                a("Press firmly on the wound with a clean cloth or your hand.",
                        "If blood soaks through, add more cloth on top. Do not lift the first one.",
                        "Keep pressing for at least 10 minutes without peeking.",
                        "Raise the injured arm or leg above the heart if there is no broken bone.",
                        "Bandage firmly. Keep the person lying down and warm and watch for faintness.",
                        "For a deep cut with an object stuck in it, press around the object and do not remove it."),
                a("Do not use a tourniquet unless bleeding from a limb cannot be controlled any other way and you know how.",
                        "Do not wash a heavily bleeding wound first.")));
        ALL.add(new Topic("burns", "Burns and scalds", "পোড়া", "First aid",
                "Call 999 for burns to the face, hands, genitals, large burns, deep burns, chemical or electrical burns.",
                a("Move away from the heat source. Remove rings, watches and loose clothing near the burn unless stuck to the skin.",
                        "Cool the burn under cool (not ice-cold) running water for 20 minutes.",
                        "Cover loosely with clean cling film or a clean non-fluffy cloth.",
                        "Keep the person warm. Give small sips of water if awake and able to swallow.",
                        "Get medical care for anything bigger than the person's palm or any blister on a sensitive area."),
                a("Do not use ice, toothpaste, butter, ghee or ointment.", "Do not burst blisters.", "Do not pull off clothing stuck to the burn.")));
        ALL.add(new Topic("fracture", "Broken bone or bad sprain", "হাড় ভাঙা", "First aid",
                "Call 999 for a suspected neck or back injury, an open bone, or heavy bleeding.",
                a("Keep the person still. Support the injured part in the position you found it.",
                        "Control any bleeding with a clean cloth. Cover an open wound without pressing on the bone.",
                        "Support the limb with a splint or folded cloth and padding, or with a sling.",
                        "Apply a cold pack wrapped in cloth for up to 20 minutes to reduce swelling.",
                        "Get the person to a hospital. If neck or back injury is possible, do not move them."),
                a("Do not try to straighten the limb or push bone back.", "Do not let the person eat or drink if surgery may be needed.")));
        ALL.add(new Topic("snake", "Snakebite", "সাপে কাটা", "First aid",
                "Take the person to a hospital with a snakebite or emergency department at once. Call 999 for transport.",
                a("Keep the person calm and as still as possible. Movement spreads venom faster.",
                        "Remove rings, bracelets and tight clothing near the bite before swelling starts.",
                        "Keep the bitten limb still, with a splint, at about heart level.",
                        "Note the time of the bite and, if safe, what the snake looked like (a photo from a distance is enough).",
                        "Go to a hospital immediately. Antivenom is given only in hospitals."),
                a("Do not cut the bite, suck it, burn it, or tie a tight band.", "Do not use herbs or traditional healers instead of hospital care.",
                        "Do not try to catch or kill the snake.")));
        ALL.add(new Topic("drowning", "Drowning", "পানিতে ডোবা", "First aid",
                "Call 999 at once. Anyone pulled from water needs a hospital check, even if they seem well.",
                a("Do not jump in unless you are trained. Reach with a stick or cloth, or throw something that floats.",
                        "Get the person out and lay them on their back. Check for normal breathing.",
                        "If not breathing and you are trained: give 5 rescue breaths first, then CPR with 30 pushes and 2 breaths.",
                        "If not trained, push hard and fast on the centre of the chest as in CPR.",
                        "If breathing, lay them on their side, keep them warm and watch them until help comes."),
                a("Do not press on the stomach to push water out.", "Do not hold a child upside down.")));
        ALL.add(new Topic("electric", "Electric shock", "বিদ্যুৎস্পৃষ্ট", "First aid",
                "Call 999 for every electric shock.",
                a("Do not touch the person while they may still be in contact with power.",
                        "Switch off the power at the main switch or unplug if you can do it safely.",
                        "If you cannot switch it off, push the person away from the source with something dry and non-metal, such as a wooden stick.",
                        "Check breathing. Start CPR if they are not breathing normally.",
                        "Cool any burns with running water and cover loosely."),
                a("Do not go near fallen wires: stay at least 10 metres away.", "Do not use anything wet or metal to move the person.")));
        ALL.add(new Topic("heat", "Heatstroke", "হিটস্ট্রোক", "First aid",
                "Call 999 if the person is confused, faints, has a fit or has hot dry skin.",
                a("Move the person to shade or a cool room. Remove extra clothing.",
                        "Cool the body quickly: wet the skin and fan, put cool wet cloths on the neck, armpits and groin.",
                        "If fully awake, give small sips of cool water.",
                        "Keep cooling until help arrives."),
                a("Do not give drinks to someone who is drowsy or vomiting.", "Do not give fever medicine: it does not help heatstroke.")));
        ALL.add(new Topic("seizure", "Seizure (fit)", "খিঁচুনি", "First aid",
                "Call 999 if it lasts more than 5 minutes, repeats, the person is injured, pregnant or does not wake up.",
                a("Clear the space around the person. Cushion the head.",
                        "Time the seizure.",
                        "When the shaking stops, roll the person on their side (recovery position).",
                        "Stay with them until fully awake. Speak calmly."),
                a("Do not hold the person down.", "Do not put anything in the mouth.", "Do not give food or drink until fully awake.")));
        ALL.add(new Topic("poison", "Poisoning or swallowed chemicals", "বিষক্রিয়া", "First aid",
                "Call 999 and go to a hospital. Take the container or a photo of the label.",
                a("Move the person away from the poison. If fumes, get them into fresh air.",
                        "Find out what, how much and when. Keep the container or packaging.",
                        "If the skin or eyes are touched, rinse with running water for at least 15 minutes.",
                        "If the person is drowsy, lay them on their side and keep checking breathing."),
                a("Do not make the person vomit.", "Do not give salt water, milk or home remedies.")));
        ALL.add(new Topic("stroke", "Stroke and heart attack", "স্ট্রোক ও হার্ট অ্যাটাক", "First aid",
                "Call 999 immediately. Every minute counts.",
                a("Stroke signs: face drooping on one side, arm weakness, slurred or confused speech, sudden loss of balance or vision. Note the time they started.",
                        "Heart attack signs: pressure or pain in the chest, arm, jaw or back, sweating, breathlessness, nausea.",
                        "Help the person sit in a comfortable position. Loosen tight clothing.",
                        "If the person is awake, not allergic and a doctor has not told them otherwise, one adult aspirin tablet chewed may help a suspected heart attack. Do not give it for a suspected stroke.",
                        "Be ready to start CPR if they stop responding and breathing."),
                a("Do not let the person walk or drive themselves to the hospital.", "Do not give food or drink.")));
        ALL.add(new Topic("dengue", "Dengue", "ডেঙ্গু", "Health",
                "Go to a hospital now for severe belly pain, repeated vomiting, bleeding, black stool, drowsiness, cold skin or trouble breathing.",
                a("Common signs: sudden high fever, headache, pain behind the eyes, muscle and joint pain, rash.",
                        "See a doctor early if you have these signs during dengue season.",
                        "Rest and drink plenty of fluids such as water, oral rehydration solution, soup.",
                        "Use paracetamol for fever and pain, only as the doctor or the label allows.",
                        "The danger period is often when the fever falls (day 3 to 7): watch for the warning signs above.",
                        "Prevent: empty standing water, cover water containers, use nets and repellent."),
                a("Do not take aspirin, ibuprofen or other anti-inflammatory painkillers.", "Do not delay care because the fever went away.")));
        ALL.add(new Topic("fire", "Fire at home or in a building", "আগুন", "Disaster",
                "Get out, stay out, call 999 or fire service 102.",
                a("Shout to warn everyone and leave at once. Do not collect belongings.",
                        "Stay low where the air is cleaner. Cover nose and mouth with a wet cloth.",
                        "Feel a closed door with the back of your hand. If hot, do not open it.",
                        "Use stairs, never the lift. Close doors behind you.",
                        "If clothes catch fire: stop, drop, cover your face and roll.",
                        "If trapped, stay by a window, signal for help and block gaps under the door with wet cloth."),
                a("Do not go back inside.", "Do not use water on an oil or electrical fire.")));
        ALL.add(new Topic("earthquake", "Earthquake", "ভূমিকম্প", "Disaster",
                "After the shaking, call 999 for injured or trapped people.",
                a("Drop to your hands and knees, cover your head and neck, hold on to a sturdy table.",
                        "Stay away from windows, mirrors and heavy furniture that can fall.",
                        "Outdoors, move to an open space away from buildings, poles and wires.",
                        "After shaking stops, expect aftershocks. Leave damaged buildings carefully.",
                        "If you smell gas, open windows and leave. Do not use matches or switches."),
                a("Do not run out during the shaking.", "Do not use the lift.")));
        ALL.add(new Topic("cyclone", "Cyclone and flood", "ঘূর্ণিঝড় ও বন্যা", "Disaster",
                "Follow official warnings from the Bangladesh Meteorological Department and local authorities. Call 999 for rescue.",
                a("Move early to a cyclone shelter or a strong building when told to. Take documents, medicine, torch, charger, dry food and water.",
                        "Switch off electricity and gas if water enters the house.",
                        "Move to higher ground. Keep children and elderly people close.",
                        "Drink only boiled or treated water after a flood.",
                        "Do not return home until authorities say it is safe."),
                a("Do not walk or drive through moving flood water.", "Do not touch fallen wires.", "Do not shelter under trees.")));
        ALL.add(new Topic("lightning", "Lightning", "বজ্রপাত", "Disaster",
                "Call 999 for anyone struck. A person struck by lightning carries no charge and is safe to touch.",
                a("Go inside a building or a vehicle with a metal roof at the first thunder.",
                        "If outside with no shelter, crouch low on the balls of your feet, head down, away from trees, water and open fields.",
                        "Stay away from rivers, ponds and boats. Keep away from metal poles.",
                        "If someone is struck, check breathing and start CPR if needed."),
                a("Do not stand under a tree.", "Do not hold metal tools or an umbrella in the open.")));
        ALL.add(new Topic("road", "Road accident", "সড়ক দুর্ঘটনা", "Safety",
                "Call 999. Give the exact place.",
                a("Make the area safe: hazard lights, warn other drivers. Keep away from fuel leaks.",
                        "Check each person. Control heavy bleeding first.",
                        "Do not move an injured person unless there is danger such as fire.",
                        "If a motorcyclist is hurt, do not remove the helmet unless they cannot breathe.",
                        "Keep the injured person warm and talk to them until help arrives."),
                a("Do not give food or drink.", "Do not crowd the injured person.")));
        ALL.add(new Topic("violence", "Harassment or violence", "হয়রানি বা সহিংসতা", "Safety",
                "In danger call 999. Women and child helpline 109. Child helpline 1098.",
                a("Go to a public, lit place with people. Shop, mosque, bus stand or a police booth.",
                        "Use the silent SOS in this app (hold Volume Down) when it is not safe to speak.",
                        "Tell a trusted person where you are.",
                        "Keep messages, photos and notes with times as a record.",
                        "Report to the police or call 109. You are not at fault."),
                a("Do not confront a violent person alone.", "Do not delete evidence.")));
    }

    public static Topic byId(String id) {
        for (Topic t : ALL) if (t.id.equals(id)) return t;
        return null;
    }
}
