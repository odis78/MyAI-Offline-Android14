package com.dmitry.myai.infinix.agent;

public final class AgentResultCodec {
    private AgentResultCodec() {}

    public static String toJson(AgentContracts.AgentResult result) {
        if (result == null) {
            return "{\"type\":\"tool_result\",\"success\":false,\"message\":\"null_result\"}";
        }
        return "{"
                + "\"type\":\"tool_result\","
                + "\"request_id\":\"" + escape(result.requestId()) + "\","
                + "\"tool\":\"" + escape(result.action().name().toLowerCase()) + "\","
                + "\"success\":" + result.success() + ","
                + "\"message\":\"" + escape(result.message()) + "\""
                + "}";
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
