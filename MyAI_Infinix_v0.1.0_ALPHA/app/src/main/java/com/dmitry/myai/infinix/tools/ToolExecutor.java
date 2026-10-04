package com.dmitry.myai.infinix.tools;

import com.dmitry.myai.infinix.bridge.MyAiAccessibilityService;
import com.dmitry.myai.infinix.safety.PolicyGate;

public final class ToolExecutor {
    private final PolicyGate policyGate;

    public ToolExecutor(PolicyGate policyGate) {
        this.policyGate = policyGate == null ? new PolicyGate() : policyGate;
    }

    public Result execute(ToolProtocol.Call call) {
        PolicyGate.Decision decision = policyGate.check(call);
        if (decision != PolicyGate.Decision.ALLOW) {
            return new Result(call == null ? "local" : call.requestId(), false,
                    decision == PolicyGate.Decision.CONFIRM ? "confirmation_required" : "policy_denied");
        }

        try {
            return switch (call.tool()) {
                case HOME -> new Result(call.requestId(), MyAiAccessibilityService.home(), "home");
                case BACK -> new Result(call.requestId(), MyAiAccessibilityService.back(), "back");
                case SCROLL -> new Result(call.requestId(), MyAiAccessibilityService.scrollDown(), "scroll_down");
                default -> new Result(call.requestId(), false, "tool_not_connected_to_bridge");
            };
        } catch (Exception e) {
            return new Result(call.requestId(), false, e.getClass().getSimpleName());
        }
    }

    public record Result(String requestId, boolean success, String message) {}
}
