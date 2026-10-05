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
        if (CommandParser.containsAny(command, "назад", "вернись назад", "вернуться назад", "верни", "back")) {
            return new AgentContracts.AgentCommand(id, AgentContracts.Action.BACK, "", false);
        }
        if (CommandParser.containsAny(command, "прокрути вниз", "прокрутка вниз", "пролистай вниз", "скролл вниз", "scroll down", "scroll_down", "вниз")) {
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

    public static AgentContracts.AgentCommand fromModelJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return AgentContracts.AgentCommand.none("model");
        }

        try {
            String jsonText = raw.trim();
            if (!jsonText.startsWith("{") || !jsonText.endsWith("}")) {
                return AgentContracts.AgentCommand.none("model-invalid");
            }
            JSONObject input = new JSONObject(jsonText);
            String requestId = input.optString("request_id", "").trim();
            if (requestId.isEmpty()) requestId = UUID.randomUUID().toString();

            if (!"tool_call".equalsIgnoreCase(input.optString("type", ""))) {
                return AgentContracts.AgentCommand.none(requestId);
            }

            String tool = normalize(input.optString("tool", ""));
            return switch (tool) {
                case "home" -> new AgentContracts.AgentCommand(
                        requestId, AgentContracts.Action.HOME, "", false);
                case "back" -> new AgentContracts.AgentCommand(
                        requestId, AgentContracts.Action.BACK, "", false);
                case "scroll_down" -> new AgentContracts.AgentCommand(
                        requestId, AgentContracts.Action.SCROLL_DOWN, "", false);
                case "open_settings" -> new AgentContracts.AgentCommand(
                        requestId, AgentContracts.Action.OPEN_SETTINGS, "", false);
                case "open_app" -> {
                    JSONObject arguments = input.optJSONObject("arguments");
                    String name = arguments == null ? "" : arguments.optString("name", "").trim();
                    if (name.isEmpty()) {
                        yield AgentContracts.AgentCommand.none(requestId);
                    }
                    yield new AgentContracts.AgentCommand(
                            requestId, AgentContracts.Action.OPEN_APP, name, false);
                }
                default -> AgentContracts.AgentCommand.none(requestId);
            };
        } catch (Exception e) {
            return AgentContracts.AgentCommand.none("model-invalid");
        }
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT).trim().replace('-', '_').replace(' ', '_');
    }
}
