package com.dmitry.myai.infinix.agent;

import org.json.JSONObject;

public final class AgentResultCodec {
    private AgentResultCodec() {}

    public static String toJson(AgentContracts.AgentResult result) {
        try {
            JSONObject o = new JSONObject();
            o.put("type", "tool_result");
            o.put("request_id", result.requestId());
            o.put("tool", result.action().name().toLowerCase());
            o.put("success", result.success());
            o.put("message", result.message());
            return o.toString();
        } catch (Exception e) {
            return "{"type":"tool_result","success":false,"message":"encoding_error"}";
        }
    }
}
