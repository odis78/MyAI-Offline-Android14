package com.dmitry.myai.infinix.agent;

import com.dmitry.myai.infinix.ai.AiProvider;

import java.util.UUID;

public final class AgentEngine {
    private final AiProvider aiProvider;
    private final AgentToolRegistry tools;
    private int maxSteps = 8;

    public AgentEngine(AiProvider aiProvider, AgentToolRegistry tools) {
        this.aiProvider = aiProvider;
        this.tools = tools;
    }

    public void setMaxSteps(int value) {
        if (value < 1 || value > 32) {
            throw new IllegalArgumentException("maxSteps must be 1..32");
        }
        maxSteps = value;
    }

    public int getMaxSteps() {
        return maxSteps;
    }

    public boolean isOfflineReady() {
        return aiProvider != null
                && aiProvider.mode() == AgentContracts.Mode.OFFLINE
                && aiProvider.isAvailable();
    }

    public void start(String sessionId, String userText, String context, Callback callback) {
        if (callback == null) throw new IllegalArgumentException("callback == null");
        if (aiProvider == null || !aiProvider.isAvailable()) {
            callback.onFailure("No AI provider is available");
            return;
        }
        String id = sessionId == null || sessionId.isBlank()
                ? UUID.randomUUID().toString() : sessionId;
        aiProvider.complete(id, userText == null ? "" : userText,
                context == null ? "" : context, new AiProvider.Callback() {
                    @Override
                    public void onSuccess(String assistantText, String toolCallJson) {
                        if (toolCallJson == null || toolCallJson.isBlank()) {
                            callback.onAssistantText(assistantText == null ? "" : assistantText);
                            return;
                        }

                        AgentContracts.AgentCommand command =
                                AgentCommandParser.fromModelJson(toolCallJson);
                        if (command.action() == AgentContracts.Action.NONE) {
                            callback.onFailure("AI returned an invalid tool call");
                            return;
                        }

                        AgentTool tool = tools.get(command.action().name().toLowerCase());
                        if (tool == null) {
                            callback.onFailure("Tool is not registered: " + command.action().name());
                            return;
                        }

                        callback.onToolResult(tool.execute(command));
                    }

                    @Override
                    public void onFailure(String message) {
                        callback.onFailure(message);
                    }
                });
    }

    public interface Callback {
        void onAssistantText(String text);
        void onToolResult(AgentContracts.AgentResult result);
        void onFailure(String message);
    }
}
