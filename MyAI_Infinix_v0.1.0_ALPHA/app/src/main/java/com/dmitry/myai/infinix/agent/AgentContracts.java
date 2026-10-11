package com.dmitry.myai.infinix.agent;

import com.dmitry.myai.infinix.safety.PolicyGate;
import com.dmitry.myai.infinix.tools.ToolCall;
import com.dmitry.myai.infinix.tools.ToolExecutor;
import com.dmitry.myai.infinix.tools.ToolProtocol;
import com.dmitry.myai.infinix.tools.ToolResult;

public final class AgentContracts {
    private AgentContracts() {}
    public enum Mode { OFFLINE, ONLINE, AUTO }
    public static final class AgentCommand {
        public final String name;
        public final String payload;
        public final boolean requiresConfirmation;

        public AgentCommand(String name, String payload, boolean requiresConfirmation) {
            this.name = name == null ? "" : name;
            this.payload = payload == null ? "" : payload;
            this.requiresConfirmation = requiresConfirmation;
        }
    }

    public static final class ToolAgent {
        private final ToolExecutor executor;
        private final PolicyGate policy;
        public ToolAgent(android.content.Context context) { policy=new PolicyGate(); executor=new ToolExecutor(context,policy); }
        public ToolResult dispatchJson(String json) {
            try { ToolCall call=ToolProtocol.parse(json); return executor.execute(call); }
            catch (Exception e) { return ToolResult.fail("protocol", e.getMessage()==null?"invalid tool call":e.getMessage()); }
        }
        public PolicyGate policy(){ return policy; }
    }
}