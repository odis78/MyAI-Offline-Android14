package com.dmitry.myai.infinix.agent;

import com.dmitry.myai.infinix.CommandParser;

import java.util.Locale;
import java.util.UUID;

public final class AgentCommandParser {
    private AgentCommandParser() {}

    public static AgentContracts.AgentCommand fromNaturalLanguage(String raw) {
        String command = CommandParser.normalize(raw);
        String id = UUID.randomUUID().toString();

        if (CommandParser.containsAny(command, "домой", "на главный экран", "рабочий стол")) {
            return new AgentContracts.AgentCommand(id, AgentContracts.Action.HOME, "", false);
        }
        if (CommandParser.containsAny(command, "назад", "вернись назад", "вернуться назад")) {
            return new AgentContracts.AgentCommand(id, AgentContracts.Action.BACK, "", false);
        }
        if (CommandParser.containsAny(command, "прокрути вниз", "прокрутка вниз", "пролистай вниз", "вниз")) {
            return new AgentContracts.AgentCommand(id, AgentContracts.Action.SCROLL_DOWN, "", false);
        }
        if (CommandParser.containsAny(command, "настройки", "открой настройки")) {
            return new AgentContracts.AgentCommand(id, AgentContracts.Action.OPEN_SETTINGS, "", false);
        }

        String app = CommandParser.extractApp(command);
        if (app != null) {
            return new AgentContracts.AgentCommand(id, AgentContracts.Action.OPEN_APP, app, false);
        }

        return AgentContracts.AgentCommand.none(id);
    }

    /**
     * Parses a deliberately small, strict model-to-agent protocol.
     * Example:
     * {"type":"tool_call","request_id":"123","tool":"open_app","arguments":{"name":"YouTube"}}
     */
    public static AgentContracts.AgentCommand fromModelJson(String raw) {
        if (raw == null || raw.isBlank()) return AgentContracts.AgentCommand.none("model");

        String input = raw.trim();
        if (!input.startsWith("{") || !input.endsWith("}")) {
            return AgentContracts.AgentCommand.none("model-invalid");
        }

        String type = jsonString(input, "type");
        if (!"tool_call".equals(type)) {
            return AgentContracts.AgentCommand.none(jsonStringOrDefault(input, "request_id", "model"));
        }

        String id = jsonStringOrDefault(input, "request_id", UUID.randomUUID().toString());
        String tool = normalize(jsonString(input, "tool"));

        return switch (tool) {
            case "home" -> new AgentContracts.AgentCommand(id, AgentContracts.Action.HOME, "", false);
            case "back" -> new AgentContracts.AgentCommand(id, AgentContracts.Action.BACK, "", false);
            case "scroll_down" -> new AgentContracts.AgentCommand(id, AgentContracts.Action.SCROLL_DOWN, "", false);
            case "open_settings" -> new AgentContracts.AgentCommand(id, AgentContracts.Action.OPEN_SETTINGS, "", false);
            case "open_app" -> {
                String name = jsonStringFromArguments(input, "name");
                if (name.isEmpty()) yield AgentContracts.AgentCommand.none(id);
                yield new AgentContracts.AgentCommand(id, AgentContracts.Action.OPEN_APP, name, false);
            }
            default -> AgentContracts.AgentCommand.none(id);
        };
    }

    private static String jsonString(String json, String key) {
        String marker = "\"" + key + "\"";
        int keyPos = json.indexOf(marker);
        if (keyPos < 0) return "";
        int colon = json.indexOf(':', keyPos + marker.length());
        if (colon < 0) return "";
        int quote = json.indexOf('"', colon + 1);
        if (quote < 0) return "";
        StringBuilder out = new StringBuilder();
        boolean escaped = false;
        for (int i = quote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                if (c == '"' || c == '\\' || c == '/') out.append(c);
                else if (c == 'n') out.append('\\n');
                else if (c == 'r') out.append('\\r');
                else if (c == 't') out.append('\\t');
                else out.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return out.toString();
            } else {
                out.append(c);
            }
        }
        return "";
    }

    private static String jsonStringOrDefault(String json, String key, String fallback) {
        String value = jsonString(json, key);
        return value.isEmpty() ? fallback : value;
    }

    private static String jsonStringFromArguments(String json, String key) {
        int argsPos = json.indexOf("\"arguments\"");
        if (argsPos < 0) return "";
        int argsEnd = json.indexOf('}', argsPos);
        if (argsEnd < 0) argsEnd = json.length();
        return jsonString(json.substring(argsPos, argsEnd + 1), key).trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim().replace('-', '_').replace(' ', '_');
    }
}
