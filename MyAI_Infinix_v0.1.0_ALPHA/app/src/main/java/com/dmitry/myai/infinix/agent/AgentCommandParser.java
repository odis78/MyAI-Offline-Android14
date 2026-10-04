package com.dmitry.myai.infinix.agent;

import com.dmitry.myai.infinix.CommandParser;
import org.json.JSONObject;

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

        try {
            JSONObject root = new JSONObject(raw.trim());
            if (!"tool_call".equals(root.optString("type"))) {
                return AgentContracts.AgentCommand.none(root.optString("request_id", "model"));
            }

            String id = root.optString("request_id", UUID.randomUUID().toString());
            String tool = normalize(root.optString("tool"));
            JSONObject args = root.optJSONObject("arguments");

            return switch (tool) {
                case "home" -> new AgentContracts.AgentCommand(id, AgentContracts.Action.HOME, "", false);
                case "back" -> new AgentContracts.AgentCommand(id, AgentContracts.Action.BACK, "", false);
                case "scroll_down" -> new AgentContracts.AgentCommand(id, AgentContracts.Action.SCROLL_DOWN, "", false);
                case "open_settings" -> new AgentContracts.AgentCommand(id, AgentContracts.Action.OPEN_SETTINGS, "", false);
                case "open_app" -> {
                    String name = args == null ? "" : args.optString("name", "").trim();
                    if (name.isEmpty()) yield AgentContracts.AgentCommand.none(id);
                    yield new AgentContracts.AgentCommand(id, AgentContracts.Action.OPEN_APP, name, false);
                }
                default -> AgentContracts.AgentCommand.none(id);
            };
        } catch (Exception ignored) {
            return AgentContracts.AgentCommand.none("model-invalid");
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim().replace('-', '_').replace(' ', '_');
    }
}
