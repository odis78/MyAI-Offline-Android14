package com.dmitry.myai.infinix.agent;

public final class AgentContracts {
    private AgentContracts() {}

    public enum Mode { OFFLINE, ONLINE, AUTO }

    public enum Action {
        NONE,
        HOME,
        BACK,
        SCROLL_DOWN,
        READ_SCREEN,
        OPEN_APP,
        OPEN_SETTINGS
    }

    public record AgentCommand(
            String requestId,
            Action action,
            String payload,
            boolean requiresConfirmation
    ) {
        public AgentCommand {
            if (requestId == null || requestId.isBlank()) requestId = "local";
            if (action == null) action = Action.NONE;
            if (payload == null) payload = "";
        }

        public static AgentCommand none(String requestId) {
            return new AgentCommand(requestId, Action.NONE, "", false);
        }
    }

    public record AgentResult(
            String requestId,
            Action action,
            boolean success,
            String message
    ) {
        public AgentResult {
            if (requestId == null || requestId.isBlank()) requestId = "local";
            if (action == null) action = Action.NONE;
            if (message == null) message = "";
        }
    }
}
