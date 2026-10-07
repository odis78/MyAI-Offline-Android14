package com.dmitry.myai.infinix.agent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AgentToolRegistry {
    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public synchronized void register(AgentTool tool) {
        if (tool == null || tool.name() == null || tool.name().isBlank()) {
            throw new IllegalArgumentException("Invalid agent tool");
        }
        tools.put(tool.name().trim().toLowerCase(), tool);
    }

    public synchronized AgentTool get(String name) {
        return name == null ? null : tools.get(name.trim().toLowerCase());
    }

    public synchronized Map<String, AgentTool> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(tools));
    }
}
