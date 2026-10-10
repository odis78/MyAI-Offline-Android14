package com.dmitry.myai.infinix.safety;

import com.dmitry.myai.infinix.tools.ToolCall;

public final class PolicyGate {
    public enum Mode { READ_ONLY, NORMAL, FULL_CONTROL }
    private Mode mode = Mode.NORMAL;

    public void setMode(Mode mode) {
        this.mode = mode == null ? Mode.NORMAL : mode;
    }

    public Mode getMode() {
        return mode;
    }

    public boolean allowed(ToolCall call) {
        if (call == null) return false;
        String tool = call.tool;
        if (mode == Mode.READ_ONLY) {
            return tool.equals("get_screen_state") || tool.equals("find_node")
                || tool.equals("wait_for");
        }
        return tool.equals("get_screen_state") || tool.equals("find_node")
            || tool.equals("click") || tool.equals("type_text")
            || tool.equals("wait_for") || tool.equals("open_app")
            || tool.equals("scroll_down") || tool.equals("back")
            || tool.equals("home") || tool.equals("recents");
    }
}
