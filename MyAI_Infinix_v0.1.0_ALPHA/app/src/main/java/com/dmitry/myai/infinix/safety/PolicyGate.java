package com.dmitry.myai.infinix.safety;

import com.dmitry.myai.infinix.tools.ToolCall;

public final class PolicyGate {
    public enum Mode { READ_ONLY, NORMAL, FULL_CONTROL }
    private Mode mode=Mode.NORMAL;
    public void setMode(Mode mode){this.mode=mode==null?Mode.NORMAL:mode;}
    public Mode getMode(){return mode;}
    public boolean allowed(ToolCall call){
        if(call==null)return false;
        String t=call.tool;
        if(mode==Mode.READ_ONLY)return t.equals("get_screen_state")||t.equals("wait_for");
        return t.equals("get_screen_state")||t.equals("find_node")||t.equals("click")||
               t.equals("type_text")||t.equals("wait_for")||t.equals("open_app")||
               t.equals("back")||t.equals("home")||t.equals("recents");
    }
}