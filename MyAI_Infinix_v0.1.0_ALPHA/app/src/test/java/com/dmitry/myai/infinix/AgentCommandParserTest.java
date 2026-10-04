package com.dmitry.myai.infinix;

import com.dmitry.myai.infinix.agent.AgentCommandParser;
import com.dmitry.myai.infinix.agent.AgentContracts;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AgentCommandParserTest {
    @Test
    public void naturalLanguageAcceptsPolitePrefix() {
        AgentContracts.AgentCommand command =
                AgentCommandParser.fromNaturalLanguage("Пожалуйста, открой YouTube!");
        assertEquals(AgentContracts.Action.OPEN_APP, command.action());
        assertEquals("youtube", command.payload());
    }

    @Test
    public void modelToolProtocolParsesOpenApp() {
        String json = "{"
                + "\"type\":\"tool_call\","
                + "\"request_id\":\"req-42\","
                + "\"tool\":\"open_app\","
                + "\"arguments\":{\"name\":\"YouTube\"}"
                + "}";
        AgentContracts.AgentCommand command = AgentCommandParser.fromModelJson(json);
        assertEquals("req-42", command.requestId());
        assertEquals(AgentContracts.Action.OPEN_APP, command.action());
        assertEquals("YouTube", command.payload());
    }

    @Test
    public void malformedModelJsonIsRejected() {
        AgentContracts.AgentCommand command =
                AgentCommandParser.fromModelJson("{not-json");
        assertEquals(AgentContracts.Action.NONE, command.action());
        assertTrue(command.requestId().startsWith("model-invalid"));
    }

    @Test
    public void unknownToolIsRejected() {
        AgentContracts.AgentCommand command =
                AgentCommandParser.fromModelJson(
                        "{\"type\":\"tool_call\",\"request_id\":\"x\",\"tool\":\"delete_everything\"}");
        assertEquals(AgentContracts.Action.NONE, command.action());
        assertEquals("x", command.requestId());
    }
}
