package com.dmitry.myai.infinix.tools;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Strict, dependency-free Android JSON parser for the local tool protocol. */
public final class ToolProtocol {
    private static final int MAX_COMMAND_LENGTH = 16 * 1024;

    private ToolProtocol() {}

    public static ToolCall parse(String json) {
        if (json == null) throw new IllegalArgumentException("null command");
        if (json.length() > MAX_COMMAND_LENGTH) {
            throw new IllegalArgumentException("command is too large");
        }

        try {
            JSONObject object = new JSONObject(json);
            Object rawTool = object.opt("tool");
            if (!(rawTool instanceof String) || ((String) rawTool).trim().isEmpty()) {
                throw new IllegalArgumentException("missing tool");
            }

            Map<String, String> args = new LinkedHashMap<>();
            if (object.has("args")) {
                Object rawArgs = object.opt("args");
                if (!(rawArgs instanceof JSONObject)) {
                    throw new IllegalArgumentException("args must be a JSON object");
                }
                JSONObject argsObject = (JSONObject) rawArgs;
                Iterator<String> keys = argsObject.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    Object value = argsObject.opt(key);
                    if (!(value instanceof String)) {
                        throw new IllegalArgumentException(
                            "argument '" + key + "' must be a JSON string");
                    }
                    args.put(key, (String) value);
                }
            }
            return new ToolCall(((String) rawTool).trim(), args);
        } catch (JSONException e) {
            throw new IllegalArgumentException("invalid JSON command: " + e.getMessage(), e);
        }
    }
}
