package com.dmitry.myai.infinix;

import java.util.Locale;

public final class CommandParser {
    private CommandParser() {}

    public static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.toLowerCase(Locale.ROOT).replace('ё', 'е').trim();
        s = s.replaceAll("[\\p{Punct}]+", " ");
        s = s.replaceAll("\\s+", " ").trim();
        s = stripPolitePrefix(s);
        if (s.startsWith("открыть ")) s = "открой " + s.substring(8).trim();
        if (s.startsWith("запустить ")) s = "запусти " + s.substring(10).trim();
        return s;
    }

    private static String stripPolitePrefix(String s) {
        String[] prefixes = {
                "пожалуйста ",
                "можешь ли ",
                "можешь ",
                "можно ли ",
                "можно ",
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
        if (text == null) return false;
        for (String value : values) {
            if (value != null && text.contains(value)) return true;
        }
        return false;
    }
}
