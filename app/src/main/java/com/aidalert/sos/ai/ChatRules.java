package com.aidalert.sos.ai;

import com.aidalert.sos.support.RiskRules;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Pure Java rules for the AI assistant: what may be sent to the cloud, and how the request and reply are shaped. */
public final class ChatRules {
    private ChatRules() {}

    public static final String ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";
    public static final String MODELS_ENDPOINT = "https://api.groq.com/openai/v1/models";
    public static final int HISTORY_TURNS = 8;

    /** Model Groq serves today for short chat answers (checked against Groq's deprecation list, Nov 2026). */
    public static final String DEFAULT_MODEL = "openai/gpt-oss-20b";

    /** Models Groq has shut down. A phone that saved one of these earlier must not keep sending it. */
    private static final List<String> RETIRED = Arrays.asList(
            "llama-3.1-8b-instant", "llama-3.3-70b-versatile", "llama3-8b-8192", "llama3-70b-8192",
            "qwen/qwen3-32b", "qwen/qwen3.6-27b", "meta-llama/llama-4-scout-17b-16e-instruct",
            "meta-llama/llama-4-maverick-17b-128e-instruct", "gemma2-9b-it", "mixtral-8x7b-32768",
            "moonshotai/kimi-k2-instruct", "moonshotai/kimi-k2-instruct-0905", "deepseek-r1-distill-llama-70b");

    public static boolean isRetired(String model) {
        return model == null || model.trim().isEmpty() || RETIRED.contains(model.trim());
    }

    /** Models to try in order: the chosen one (unless retired), then the current Groq models. No duplicates. */
    public static List<String> candidates(String chosen) {
        List<String> out = new ArrayList<>();
        if (!isRetired(chosen)) out.add(chosen.trim());
        for (String m : new String[]{DEFAULT_MODEL, "openai/gpt-oss-120b", "qwen/qwen3.8-27b"}) {
            if (!out.contains(m)) out.add(m);
        }
        return out;
    }

    /** Chat-capable model ids from Groq's /models answer (speech, voice, guard and embedding models left out). */
    public static List<String> chatModelsFrom(String json) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray data = new JSONObject(json).optJSONArray("data");
            if (data == null) return out;
            for (int i = 0; i < data.length(); i++) {
                JSONObject o = data.optJSONObject(i);
                if (o == null) continue;
                String id = o.optString("id");
                String l = id.toLowerCase(java.util.Locale.ROOT);
                if (id.isEmpty() || !o.optBoolean("active", true)) continue;
                if (l.contains("whisper") || l.contains("orpheus") || l.contains("tts") || l.contains("guard")
                        || l.contains("safeguard") || l.contains("embed") || l.contains("compound") || isRetired(id)) continue;
                out.add(id);
            }
        } catch (JSONException e) {
            return new ArrayList<>();
        }
        return out;
    }

    /** Best model among the available ids: the known-good ones first, otherwise the first one. */
    public static String pickModel(List<String> available) {
        for (String m : new String[]{DEFAULT_MODEL, "openai/gpt-oss-120b", "qwen/qwen3.8-27b"}) {
            if (available.contains(m)) return m;
        }
        return available.isEmpty() ? "" : available.get(0);
    }

    /** True when the error text means "this model is not available" rather than a key or network problem. */
    public static boolean isModelProblem(String error) {
        if (error == null) return false;
        String l = error.toLowerCase(java.util.Locale.ROOT);
        return l.startsWith("http 404") || l.contains("model_not_found") || l.contains("decommissioned")
                || (l.startsWith("http 400") && l.contains("model"));
    }

    public static final String SYSTEM_PROMPT = "You are AidAlert, a calm emergency and safety assistant for people in Bangladesh. "
            + "Answer in the language the user writes in (Bangla or English). Keep answers short: at most 6 short steps. "
            + "Bangladesh numbers: national emergency 999, fire service 102, women and children violence helpline 109, "
            + "child helpline 1098. Always tell the user to call 999 first when life is in danger. "
            + "Never invent hospitals, phone numbers or addresses. You are not a doctor: give basic first aid only and say when "
            + "a hospital is needed. Do not give medicine doses. If the user talks about hurting themselves, be kind and tell them "
            + "to use the Support companion in this app or call 999.";

    /** Text about self-harm stays on the phone: it is never sent to an online service. */
    public static boolean mayGoOnline(String userText) {
        return RiskRules.level(userText) == RiskRules.NONE;
    }

    /** One chat turn kept in history. role is "user" or "assistant". */
    public static final class Turn {
        public final String role, text;

        public Turn(String role, String text) {
            this.role = role;
            this.text = text;
        }
    }

    /** JSON body for the Groq chat endpoint: system prompt, the last few turns, then the new question. */
    public static String requestBody(String model, List<Turn> history, String userText) throws JSONException {
        JSONArray msgs = new JSONArray();
        msgs.put(new JSONObject().put("role", "system").put("content", SYSTEM_PROMPT));
        int from = Math.max(0, history.size() - HISTORY_TURNS);
        for (int i = from; i < history.size(); i++) {
            Turn t = history.get(i);
            // history that was typed in a worrying moment is never re-sent either
            if (!mayGoOnline(t.text)) continue;
            msgs.put(new JSONObject().put("role", t.role).put("content", t.text));
        }
        msgs.put(new JSONObject().put("role", "user").put("content", userText));
        JSONObject body = new JSONObject().put("model", model).put("messages", msgs)
                .put("temperature", 0.3).put("max_tokens", 900);
        // gpt-oss models think before answering; a low effort keeps the answer fast and inside the token limit
        if (model.startsWith("openai/gpt-oss")) body.put("reasoning_effort", "low");
        return body.toString();
    }

    /** The assistant's text from a chat-completions answer, or "" when the answer has none. */
    public static String parseReply(String json) {
        try {
            JSONArray choices = new JSONObject(json).optJSONArray("choices");
            if (choices == null || choices.length() == 0) return "";
            JSONObject m = choices.optJSONObject(0).optJSONObject("message");
            return m == null ? "" : m.optString("content", "").trim();
        } catch (JSONException e) {
            return "";
        }
    }
}
