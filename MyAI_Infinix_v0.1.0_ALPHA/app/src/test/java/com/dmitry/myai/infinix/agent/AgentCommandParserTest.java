package com.dmitry.myai.infinix.agent;

import org.junit.Test;

import static org.junit.Assert.*;

public class AgentCommandParserTest {
    @Test public void naturalPoliteOpenYoutube() {
        AgentContracts.AgentCommand c =
                AgentCommandParser.fromNaturalLanguage("Пожалуйста, открой YouTube");
        assertEquals(AgentContracts.Action.OPEN_APP, c.action());
        assertEquals("youtube", c.payload());
    }

    @Test public void naturalHome() {
        AgentContracts.AgentCommand c =
                AgentCommandParser.fromNaturalLanguage("можешь, пожалуйста, домой");
        assertEquals(AgentContracts.Action.HOME, c.action());
    }

    @Test public void modelOpenAppJson() {
        String json = "{\"type\":\"tool_call\",\"request_id\":\"42\",\"tool\":\"open_app\",\"arguments\":{\"name\":\"YouTube\"}}";
        AgentContracts.AgentCommand c = AgentCommandParser.fromModelJson(json);
        assertEquals("42", c.requestId());
        assertEquals(AgentContracts.Action.OPEN_APP, c.action());
        assertEquals("YouTube", c.payload());
    }

    @Test public void modelRejectsUnknownTool() {
        String json = "{\"type\":\"tool_call\",\"tool\":\"delete_everything\"}";
        assertEquals(AgentContracts.Action.NONE, AgentCommandParser.fromModelJson(json).action());
    }

    @Test public void malformedModelJsonIsSafe() {
        assertEquals(AgentContracts.Action.NONE,
                AgentCommandParser.fromModelJson("not json").action());
    }

    @Test public void resultCodecContainsRequestIdAndStatus() {
        AgentContracts.AgentResult r =
                new AgentContracts.AgentResult("abc", AgentContracts.Action.HOME, true, "ok");
        String json = AgentResultCodec.toJson(r);
        assertTrue(json.contains("\"request_id\":\"abc\""));
        assertTrue(json.contains("\"success\":true"));
    }
}
