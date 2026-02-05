package org.iumotionlabs.hefesto.feature;

import org.iumotionlabs.hefesto.command.CommandResult;
import org.iumotionlabs.hefesto.core.adapter.console.ConsoleInputAdapter;
import org.iumotionlabs.hefesto.core.adapter.console.ConsoleOutputAdapter;
import org.iumotionlabs.hefesto.core.context.ExecutionContext;
import org.iumotionlabs.hefesto.feature.base64.Base64Command;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Base64Command Tests")
class Base64CommandTest {

    private Base64Command command;
    private ExecutionContext ctx;
    private ByteArrayOutputStream outputStream;

    @BeforeEach
    void setUp() {
        command = new Base64Command();
        outputStream = new ByteArrayOutputStream();
        var output = new ConsoleOutputAdapter(new PrintStream(outputStream), false);
        ctx = new ExecutionContext(new ConsoleInputAdapter(), output);
    }

    @Test
    @DisplayName("execute should encode text to base64")
    void executeEncodesText() {
        CommandResult result = command.execute(ctx, new String[]{"Hello World"});

        assertInstanceOf(CommandResult.Success.class, result);
        assertTrue(outputStream.toString().trim().contains("SGVsbG8gV29ybGQ="));
    }

    @Test
    @DisplayName("execute with --decode should decode base64")
    void executeDecodesBase64() {
        CommandResult result = command.execute(ctx, new String[]{"SGVsbG8gV29ybGQ=", "--decode"});

        assertInstanceOf(CommandResult.Success.class, result);
        assertTrue(outputStream.toString().contains("Hello World"));
    }

    @Test
    @DisplayName("execute with -d should decode base64")
    void executeDecodesBase64ShortFlag() {
        CommandResult result = command.execute(ctx, new String[]{"SGVsbG8=", "-d"});

        assertInstanceOf(CommandResult.Success.class, result);
        assertTrue(outputStream.toString().contains("Hello"));
    }

    @Test
    @DisplayName("execute with --url should use URL-safe encoding")
    void executeUsesUrlSafeEncoding() {
        // Characters that differ between standard and URL-safe: + and /
        CommandResult result = command.execute(ctx, new String[]{"subjects?_d", "--url"});

        assertInstanceOf(CommandResult.Success.class, result);
        String output = outputStream.toString().trim();
        // URL-safe encoding uses - instead of + and _ instead of /
        assertFalse(output.contains("+"));
        assertFalse(output.contains("/"));
    }

    @Test
    @DisplayName("execute with invalid base64 should return failure")
    void executeWithInvalidBase64ReturnFailure() {
        CommandResult result = command.execute(ctx, new String[]{"--decode", "not-valid-base64!!!"});

        assertInstanceOf(CommandResult.Failure.class, result);
    }

    @Test
    @DisplayName("execute with no args should return failure")
    void executeWithNoArgsReturnFailure() {
        CommandResult result = command.execute(ctx, new String[]{});

        assertInstanceOf(CommandResult.Failure.class, result);
    }

    @Test
    @DisplayName("info should have correct metadata")
    void infoHasCorrectMetadata() {
        var info = command.info();

        assertEquals("base64", info.name());
        assertTrue(info.aliases().contains("b64"));
        assertEquals("encoding", info.category());
    }
}
