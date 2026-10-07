package com.dmitry.myai.infinix.ai;

import com.dmitry.myai.infinix.agent.AgentContracts;

public interface AiProvider {
    AgentContracts.Mode mode();
    boolean isAvailable();
    void complete(String sessionId, String userText, String context, Callback callback);

    interface Callback {
        void onSuccess(String assistantText, String toolCallJson);
        void onFailure(String message);
    }
}
