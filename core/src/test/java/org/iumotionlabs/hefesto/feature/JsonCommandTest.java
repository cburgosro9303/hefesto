package org.iumotionlabs.hefesto.feature;

import org.iumotionlabs.hefesto.command.CommandResult;
import org.iumotionlabs.hefesto.core.adapter.console.ConsoleInputAdapter;
import org.iumotionlabs.hefesto.core.adapter.console.ConsoleOutputAdapter;
import org.iumotionlabs.hefesto.core.context.ExecutionContext;
import org.iumotionlabs.hefesto.feature.json.JsonCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JsonCommand Tests")
class JsonCommandTest {

    private JsonCommand command;
    private ExecutionContext ctx;
    private ByteArrayOutputStream outputStream;

    @BeforeEach
    void setUp() {
        command = new JsonCommand();
        outputStream = new ByteArrayOutputStream();
        var output = new ConsoleOutputAdapter(new PrintStream(outputStream), false);
        ctx = new ExecutionContext(new ConsoleInputAdapter(), output);
    }

    @Test
    @DisplayName("execute should format JSON with indentation")
    void executeFormatsJson() {
        CommandResult result = command.execute(ctx, new String[]{"{\"name\":\"test\",\"value\":42}"});

        assertInstanceOf(CommandResult.Success.class, result);
        String output = outputStream.toString();
        assertTrue(output.contains("\"name\""));
        assertTrue(output.contains("\"test\""));
        assertTrue(output.contains("\n")); // Should be formatted
    }

    @Test
    @DisplayName("execute with --compact should output minified JSON")
    void executeWithCompactMinifiesJson() {
        CommandResult result = command.execute(ctx, new String[]{"{\"name\":\"test\"}", "--compact"});

        assertInstanceOf(CommandResult.Success.class, result);
        String output = outputStream.toString().trim();
        assertEquals("{\"name\":\"test\"}", output);
    }

    @Test
    @DisplayName("execute with --validate should only validate")
    void executeWithValidateOnlyValidates() {
        CommandResult result = command.execute(ctx, new String[]{"{\"valid\":true}", "--validate"});

        assertInstanceOf(CommandResult.Success.class, result);
        assertTrue(outputStream.toString().contains("valido"));
    }

    @Test
    @DisplayName("execute with invalid JSON should return failure")
    void executeWithInvalidJsonReturnFailure() {
        CommandResult result = command.execute(ctx, new String[]{"{invalid"});

        assertInstanceOf(CommandResult.Failure.class, result);
    }

    @Test
    @DisplayName("execute with --validate and invalid JSON should report error")
    void executeWithValidateAndInvalidJson() {
        CommandResult result = command.execute(ctx, new String[]{"--validate", "{invalid"});

        assertInstanceOf(CommandResult.Failure.class, result);
    }

    @Test
    @DisplayName("execute with no args should return failure")
    void executeWithNoArgsReturnFailure() {
        CommandResult result = command.execute(ctx, new String[]{});

        assertInstanceOf(CommandResult.Failure.class, result);
    }

    @Test
    @DisplayName("execute should handle arrays")
    void executeHandlesArrays() {
        CommandResult result = command.execute(ctx, new String[]{"[1,2,3]"});

        assertInstanceOf(CommandResult.Success.class, result);
        assertTrue(outputStream.toString().contains("1"));
    }

    @Test
    @DisplayName("execute should handle nested objects")
    void executeHandlesNestedObjects() {
        CommandResult result = command.execute(ctx, new String[]{"{\"outer\":{\"inner\":\"value\"}}"});

        assertInstanceOf(CommandResult.Success.class, result);
        assertTrue(outputStream.toString().contains("inner"));
    }

    @Test
    @DisplayName("info should have correct metadata")
    void infoHasCorrectMetadata() {
        var info = command.info();

        assertEquals("json", info.name());
        assertTrue(info.aliases().contains("jq"));
        assertEquals("encoding", info.category());
    }
}
