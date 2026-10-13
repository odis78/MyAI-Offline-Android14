package com.dmitry.myai.infinix;

import java.util.Locale;

/**
 * Small offline Russian intent router. This is deliberately deterministic:
 * it broadens phrasing without sending ambiguous text to a phone-control tool.
 */
final class RussianCommandParser {
    static final class Command {
        final String tool;
        final String argsJson;

        Command(String tool, String argsJson) {
            this.tool = tool;
            this.argsJson = argsJson;
        }
    }

    private RussianCommandParser() {}

    static Command parse(String raw) {
        if (raw == null) return null;
        String text = raw.toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
        if (text.isEmpty()) return null;

        // Ignore polite / conversational prefixes without requiring an exact phrase.
        text = text.replaceFirst("^(пожалуйста |можешь |можно |давай |помоги |сделай |выполни |мне |сейчас )+", "");
        text = text.replaceFirst(" (пожалуйста|если можно)$", "");

        if (has(text, "домой", "главный экран", "домашний экран", "рабочий стол",
                "верни на главный", "перейди домой", "выйди домой")) {
            return command("home");
        }
        if (has(text, "прокрути вниз", "прокрутка вниз", "листай вниз", "листани вниз",
                "скроль вниз", "пролистай вниз", "ниже", "вниз")) {
            return command("scroll_down");
        }
        if (has(text, "прокрути вверх", "прокрутка вверх", "листай вверх", "листани вверх",
                "скроль вверх", "пролистай вверх", "выше", "вверх")) {
            return command("scroll_up");
        }
        if (has(text, "прочитай экран", "прочти экран", "что на экране", "посмотри экран",
                "посмотри что на экране", "прочитай что написано", "что написано на экране",
                "текст на экране", "содержимое экрана", "состояние экрана", "прочитай страницу")) {
            return command("get_screen_state");
        }
        if (has(text, "назад", "вернись", "вернуться", "предыдущая страница",
                "предыдущий экран", "верни предыдущий", "шаг назад")) {
            return command("back");
        }
        if (has(text, "недавние приложения", "последние приложения", "открытые приложения",
                "переключатель приложений", "список приложений")) {
            return command("recents");
        }

        if (has(text, "youtube", "ютуб", "ю туб")) {
            if (has(text, "открой", "запусти", "перейди", "включи", "покажи")) {
                return openApp("com.google.android.youtube");
            }
        }
        if (has(text, "chrome", "браузер", "интернет")) {
            if (has(text, "открой", "запусти", "перейди", "включи", "покажи")) {
                return openApp("com.android.chrome");
            }
        }
        if (has(text, "настройки", "settings")) {
            if (has(text, "открой", "запусти", "перейди", "покажи")) {
                return openApp("com.android.settings");
            }
        }
        return null;
    }

    private static Command command(String name) {
        return new Command(name, "{}");
    }

    private static Command openApp(String packageName) {
        return new Command("open_app", "{\\"package\\":\\"" + packageName + "\\"}");
    }

    private static boolean has(String text, String... phrases) {
        for (String phrase : phrases) {
            if (text.equals(phrase) || text.contains(phrase)) return true;
        }
        return false;
    }
}
