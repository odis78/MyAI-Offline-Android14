package com.dmitry.myai.infinix.agent;

public interface AgentTool {
    String name();
    String description();
    AgentContracts.AgentResult execute(AgentContracts.AgentCommand command);
}
