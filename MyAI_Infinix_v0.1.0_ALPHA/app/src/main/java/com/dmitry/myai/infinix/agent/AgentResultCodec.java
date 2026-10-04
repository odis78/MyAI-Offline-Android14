package com.dmitry.myai.infinix.agent;

import org.json.JSONObject;

import java.util.Locale;

public final class AgentResultCodec {
    private AgentResultCodec() {}

    public static String toJson(AgentContracts.AgentResult result) {
        if (result == null) {
            return "{\"type\":\"tool_result\",\"success\":false,\"message\":\"null_result\"}";
        }
        try {
            JSONObject json = new JSONObject();
            json.put("type", "tool_result");
            json.put("request_id", result.requestId());
            json.put("tool", result.action().name().toLowerCase(Locale.ROOT));
            json.put("success", result.success());
            json.put("message", result.message());
            return json.toString();
        } catch (Exception e) {
            return "{\"type\":\"tool_result\",\"success\":false,\"message\":\"codec_error\"}";
        }
    }
}
