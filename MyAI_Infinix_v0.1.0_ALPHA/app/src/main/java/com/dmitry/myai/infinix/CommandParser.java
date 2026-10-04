package com.dmitry.myai.infinix;

import java.util.Locale;

public final class CommandParser {
    private CommandParser() {}

    public static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.toLowerCase(Locale.ROOT).replace('ё', 'е').trim();
        s = s.replaceAll("[\\p{Punct}]+", " ");
        s = s.replaceAll("\\s+", " ").trim();
        s = stripPolitePrefix(s);\n        if (s.startsWith("открыть ")) s = "открой " + s.substring(8).trim();\n        if (s.startsWith("запустить ")) s = "запусти " + s.substring(10).trim();\n        return s;
    }

    private static String stripPolitePrefix(String s) {
        String[] prefixes = {
                "пожалуйста ",
                "пожалуйста, ",
                "можешь ",
                "можешь ли ",
                "можно ",
                "можно ли ",
                "прошу ",
                "давай "
        };
        boolean changed;
        do {
            changed = false;
            for (String prefix : prefixes) {
                if (s.startsWith(prefix)) {
                    s = s.substring(prefix.length()).trim();
                    changed = true;
                    break;
                }
            }
        } while (changed);
        return s;
    }

    public static String extractApp(String command) {
        String[] prefixes = {"открой ", "запусти ", "открыть ", "запустить "};
        for (String prefix : prefixes) {
            if (command.startsWith(prefix)) {
                String value = command.substring(prefix.length()).trim();
                if (!value.isEmpty()) return value;
            }
        }
        return null;
    }

    public static boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) return true;
        }
        return false;
    }
}
