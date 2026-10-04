package com.dmitry.myai.infinix.safety;

import com.dmitry.myai.infinix.tools.ToolProtocol;

public final class PolicyGate {
    public enum Mode { READ_ONLY, NORMAL, FULL_CONTROL }

    public enum Decision { ALLOW, DENY, CONFIRM }

    private volatile Mode mode = Mode.NORMAL;

    public void setMode(Mode mode) {
        this.mode = mode == null ? Mode.NORMAL : mode;
    }

    public Mode getMode() { return mode; }

    public Decision check(ToolProtocol.Call call) {
        if (call == null || call.tool() == null) return Decision.DENY;

        switch (mode) {
            case READ_ONLY:
                return switch (call.tool()) {
                    case GET_SCREEN_STATE, WAIT_FOR -> Decision.ALLOW;
                    default -> Decision.DENY;
                };
            case NORMAL:
                return switch (call.tool()) {
                    case GET_SCREEN_STATE, WAIT_FOR, OPEN_APP, BACK, HOME, RECENTS, SCROLL, CLICK, TYPE_TEXT
                            -> Decision.ALLOW;
                };
            case FULL_CONTROL:
                return Decision.ALLOW;
            default:
                return Decision.DENY;
        }
    }
}
