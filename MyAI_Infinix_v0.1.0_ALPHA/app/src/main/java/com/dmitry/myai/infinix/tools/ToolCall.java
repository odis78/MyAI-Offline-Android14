package com.dmitry.myai.infinix.tools;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ToolCall {
    public final String tool;
    public final Map<String, String> args;
    public ToolCall(String tool, Map<String, String> args) {
        this.tool = tool == null ? "" : tool;
        this.args = Collections.unmodifiableMap(new LinkedHashMap<>(args));
    }
}