package com.dmitry.myai.infinix;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CommandParserTest {
    @Test
    public void politePrefixIsRemoved() {
        assertEquals("открой youtube",
                CommandParser.normalize("Пожалуйста, открой YouTube!"));
        assertEquals("открой youtube",
                CommandParser.normalize("Можешь открыть YouTube?"));
    }

    @Test
    public void appPayloadIsExtracted() {
        assertEquals("youtube", CommandParser.extractApp("открой youtube"));
        assertEquals("камера", CommandParser.extractApp("запусти камера"));
        assertNull(CommandParser.extractApp("сделай мне кофе"));
    }

    @Test
    public void punctuationAndYoAreNormalized() {
        assertEquals("открой галерею", CommandParser.normalize("Открой, галерею!"));
        assertEquals("открой еле", CommandParser.normalize("Открой ёле"));
    }
}
