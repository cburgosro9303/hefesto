package org.iumotionlabs.hefesto.command;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CommandParser Tests")
class CommandParserTest {

    @Test
    @DisplayName("tokenize should split simple arguments")
    void tokenizeSplitsSimpleArguments() {
        String[] tokens = CommandParser.tokenize("echo hello world");

        assertArrayEquals(new String[]{"echo", "hello", "world"}, tokens);
    }

    @Test
    @DisplayName("tokenize should handle double-quoted strings")
    void tokenizeHandlesDoubleQuotes() {
        String[] tokens = CommandParser.tokenize("echo \"hello world\"");

        assertArrayEquals(new String[]{"echo", "hello world"}, tokens);
    }

    @Test
    @DisplayName("tokenize should handle single-quoted strings")
    void tokenizeHandlesSingleQuotes() {
        String[] tokens = CommandParser.tokenize("echo 'hello world'");

        assertArrayEquals(new String[]{"echo", "hello world"}, tokens);
    }

    @Test
    @DisplayName("tokenize should handle mixed quotes")
    void tokenizeHandlesMixedQuotes() {
        String[] tokens = CommandParser.tokenize("echo \"hello\" 'world'");

        assertArrayEquals(new String[]{"echo", "hello", "world"}, tokens);
    }

    @Test
    @DisplayName("tokenize should return empty array for null input")
    void tokenizeHandlesNullInput() {
        String[] tokens = CommandParser.tokenize(null);

        assertEquals(0, tokens.length);
    }

    @Test
    @DisplayName("tokenize should return empty array for blank input")
    void tokenizeHandlesBlankInput() {
        String[] tokens = CommandParser.tokenize("   ");

        assertEquals(0, tokens.length);
    }

    @Test
    @DisplayName("parse should extract long flags with equals")
    void parseExtractsLongFlagsWithEquals() {
        var parsed = CommandParser.parse(new String[]{"--name=value"});

        assertEquals("value", parsed.getFlag("name", ""));
    }

    @Test
    @DisplayName("parse should extract long flags with space")
    void parseExtractsLongFlagsWithSpace() {
        var parsed = CommandParser.parse(new String[]{"--name", "value"});

        assertEquals("value", parsed.getFlag("name", ""));
    }

    @Test
    @DisplayName("parse should handle boolean flags")
    void parseHandlesBooleanFlags() {
        var parsed = CommandParser.parse(new String[]{"--verbose", "--debug"});

        assertTrue(parsed.getBoolean("verbose"));
        assertTrue(parsed.getBoolean("debug"));
        assertFalse(parsed.getBoolean("quiet"));
    }

    @Test
    @DisplayName("parse should extract short flags")
    void parseExtractsShortFlags() {
        var parsed = CommandParser.parse(new String[]{"-v", "-n", "value"});

        assertTrue(parsed.getBoolean("v"));
        assertEquals("value", parsed.getFlag("n", ""));
    }

    @Test
    @DisplayName("parse should handle combined short flags")
    void parseHandlesCombinedShortFlags() {
        var parsed = CommandParser.parse(new String[]{"-abc"});

        assertTrue(parsed.getBoolean("a"));
        assertTrue(parsed.getBoolean("b"));
        assertTrue(parsed.getBoolean("c"));
    }

    @Test
    @DisplayName("parse should extract positional arguments")
    void parseExtractsPositionalArguments() {
        var parsed = CommandParser.parse(new String[]{"hello", "world", "--flag"});

        assertEquals(2, parsed.positionalCount());
        assertEquals("hello", parsed.positional(0));
        assertEquals("world", parsed.positional(1));
        assertTrue(parsed.getBoolean("flag"));
    }

    @Test
    @DisplayName("parse should handle mixed flags and positional")
    void parseHandlesMixedFlagsAndPositional() {
        // Note: -v consumes the next non-flag argument as its value
        var parsed = CommandParser.parse(new String[]{"--name", "test", "arg1", "-v"});

        assertEquals("test", parsed.getFlag("name", ""));
        assertTrue(parsed.getBoolean("v"));
        assertEquals(1, parsed.positionalCount());
        assertEquals("arg1", parsed.positional(0));
    }

    @Test
    @DisplayName("getFlagAsInt should parse integer values")
    void getFlagAsIntParsesIntegerValues() {
        var parsed = CommandParser.parse(new String[]{"--count", "42"});

        assertEquals(42, parsed.getFlagAsInt("count", 0));
    }

    @Test
    @DisplayName("getFlagAsInt should return default for non-integer")
    void getFlagAsIntReturnsDefaultForNonInteger() {
        var parsed = CommandParser.parse(new String[]{"--count", "abc"});

        assertEquals(0, parsed.getFlagAsInt("count", 0));
    }

    @Test
    @DisplayName("getPositional should return empty for out of bounds")
    void getPositionalReturnsEmptyForOutOfBounds() {
        var parsed = CommandParser.parse(new String[]{"arg1"});

        assertTrue(parsed.getPositional(0).isPresent());
        assertFalse(parsed.getPositional(1).isPresent());
    }
}
