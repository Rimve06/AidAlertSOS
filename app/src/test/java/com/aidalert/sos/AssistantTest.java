package com.aidalert.sos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.aidalert.sos.ai.ChatRules;
import com.aidalert.sos.ai.OfflineGuide;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class AssistantTest {
    @Test
    public void selfHarmTextNeverGoesOnline() {
        assertFalse(ChatRules.mayGoOnline("I want to end my life"));
        assertFalse(ChatRules.mayGoOnline("আমি আত্মহত্যা করতে চাই"));
        assertTrue(ChatRules.mayGoOnline("How do I treat a burn?"));
    }

    @Test
    public void requestKeepsOnlyRecentSafeHistory() throws Exception {
        List<ChatRules.Turn> h = new ArrayList<>();
        for (int i = 0; i < 12; i++) h.add(new ChatRules.Turn(i % 2 == 0 ? "user" : "assistant", "msg" + i));
        h.add(new ChatRules.Turn("user", "I can't go on"));
        String body = ChatRules.requestBody("m1", h, "what now");
        assertTrue(body.contains("msg11"));
        assertFalse(body.contains("msg1\""));
        assertFalse(body.contains("can't go on"));
        assertTrue(body.contains("what now"));
        assertTrue(body.contains("\"model\":\"m1\""));
    }

    @Test
    public void replyIsParsed() {
        assertEquals("Call 999.", ChatRules.parseReply("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\" Call 999. \"}}]}"));
        assertEquals("", ChatRules.parseReply("not json"));
        assertEquals("", ChatRules.parseReply("{\"choices\":[]}"));
    }

    @Test
    public void offlineGuideFindsTopics() {
        assertEquals("snake", OfflineGuide.answer("my brother got a snakebite").topicId);
        assertEquals("cpr", OfflineGuide.answer("he is not breathing and unconscious").topicId);
        assertEquals("fire", OfflineGuide.answer("fire at home, what to do").topicId);
        assertEquals("", OfflineGuide.answer("what is the capital of France").topicId);
        assertTrue(OfflineGuide.answer("zzz").text.contains("999"));
    }
}
