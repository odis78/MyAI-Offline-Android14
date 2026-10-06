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

    @Test public void naturalBackAliases() {
        assertEquals(AgentContracts.Action.BACK,
                AgentCommandParser.fromNaturalLanguage("верни").action());
        assertEquals(AgentContracts.Action.BACK,
                AgentCommandParser.fromNaturalLanguage("вернись").action());
        assertEquals(AgentContracts.Action.BACK,
                AgentCommandParser.fromNaturalLanguage("back").action());
        assertEquals(AgentContracts.Action.BACK,
                AgentCommandParser.fromNaturalLanguage("go back").action());
    }

    @Test public void naturalScrollAliases() {
        assertEquals(AgentContracts.Action.SCROLL_DOWN,
                AgentCommandParser.fromNaturalLanguage("скролл вниз").action());
        assertEquals(AgentContracts.Action.SCROLL_DOWN,
                AgentCommandParser.fromNaturalLanguage("scroll down").action());
        assertEquals(AgentContracts.Action.SCROLL_DOWN,
                AgentCommandParser.fromNaturalLanguage("прокрути журнал вниз").action());
        assertEquals(AgentContracts.Action.SCROLL_DOWN,
                AgentCommandParser.fromNaturalLanguage("пролистай экран вниз").action());
    }

    @Test public void naturalContacts() {
        AgentContracts.AgentCommand c =
                AgentCommandParser.fromNaturalLanguage("открой контакты");
        assertEquals(AgentContracts.Action.OPEN_APP, c.action());
        assertEquals("контакты", c.payload());
    }

    @Test public void naturalAppWithFiller() {
        AgentContracts.AgentCommand c =
                AgentCommandParser.fromNaturalLanguage("открой мне контакты");
        assertEquals(AgentContracts.Action.OPEN_APP, c.action());
        assertEquals("контакты", c.payload());
    }

    @Test public void modelOpenAppJson() {
        String json = "{\"type\":\"tool_call\",\"request_id\":\"42\",\"tool\":\"open_app\",\"arguments\":{\"name\":\"YouTube\"}}";
        AgentContracts.AgentCommand c = AgentCommandParser.fromModelJson(json);
        assertEquals("42", c.requestId());
        assertEquals(AgentContracts.Action.OPEN_APP, c.action());
        assertEquals("YouTube", c.payload());
    }

    @Test public void modelParsesNestedArgumentsAndEscapes() {
        String json = "{\"type\":\"tool_call\",\"request_id\":\"r-7\",\"tool\":\"open_app\",\"arguments\":{\"name\":\"You\\\"Tube\"}}";
        AgentContracts.AgentCommand command = AgentCommandParser.fromModelJson(json);
        assertEquals("r-7", command.requestId());
        assertEquals(AgentContracts.Action.OPEN_APP, command.action());
        assertEquals("You\"Tube", command.payload());
    }

    @Test public void modelRejectsInvalidNestedJson() {
        assertEquals(AgentContracts.Action.NONE,
                AgentCommandParser.fromModelJson("{\"type\":\"tool_call\",\"tool\":\"open_app\",\"arguments\":").action());
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
