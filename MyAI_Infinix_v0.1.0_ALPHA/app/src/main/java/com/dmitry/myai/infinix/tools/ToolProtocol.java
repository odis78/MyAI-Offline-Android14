package com.dmitry.myai.infinix.tools;

import org.json.JSONObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ToolProtocol {
    private ToolProtocol() {}

    public static final String TYPE_TOOL_CALL = "tool_call";
    public static final String TYPE_TOOL_RESULT = "tool_result";

    public enum Tool {
        GET_SCREEN_STATE,
        CLICK,
        TYPE_TEXT,
        WAIT_FOR,
        OPEN_APP,
        BACK,
        HOME,
        RECENTS,
        SCROLL
    }

    public static final class Call {
        private final String requestId;
        private final Tool tool;
        private final Map<String, String> arguments;

        public Call(String requestId, Tool tool, Map<String, String> arguments) {
            this.requestId = requestId == null || requestId.isBlank() ? "local" : requestId;
            this.tool = tool;
            this.arguments = arguments == null
                    ? Collections.emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
        }

        public String requestId() { return requestId; }
        public Tool tool() { return tool; }
        public Map<String, String> arguments() { return arguments; }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("type", TYPE_TOOL_CALL);
            json.put("request_id", requestId);
            json.put("tool", tool == null ? "" : tool.name().toLowerCase());
            JSONObject args = new JSONObject();
            for (Map.Entry<String, String> e : arguments.entrySet()) args.put(e.getKey(), e.getValue());
            json.put("arguments", args);
            return json;
        }
    }

    public static Call parseStrict(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("empty tool call");
        JSONObject json = new JSONObject(raw.trim());
        if (!TYPE_TOOL_CALL.equalsIgnoreCase(json.optString("type", ""))) {
            throw new IllegalArgumentException("invalid tool call type");
        }
        String id = json.optString("request_id", "model");
        String toolName = json.optString("tool", "").trim().toUpperCase().replace('-', '_');
        Tool tool = Tool.valueOf(toolName);
        JSONObject rawArgs = json.optJSONObject("arguments");
        Map<String, String> args = new LinkedHashMap<>();
        if (rawArgs != null) {
            java.util.Iterator<String> keys = rawArgs.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                args.put(key, rawArgs.optString(key, ""));
            }
        }
        return new Call(id, tool, args);
    }
}
