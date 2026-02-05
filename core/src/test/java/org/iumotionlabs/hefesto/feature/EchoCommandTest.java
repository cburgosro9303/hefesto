package org.iumotionlabs.hefesto.feature;

import org.iumotionlabs.hefesto.command.CommandResult;
import org.iumotionlabs.hefesto.core.adapter.console.ConsoleInputAdapter;
import org.iumotionlabs.hefesto.core.adapter.console.ConsoleOutputAdapter;
import org.iumotionlabs.hefesto.core.context.ExecutionContext;
import org.iumotionlabs.hefesto.feature.echo.EchoCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EchoCommand Tests")
class EchoCommandTest {

    private EchoCommand command;
    private ExecutionContext ctx;
    private ByteArrayOutputStream outputStream;

    @BeforeEach
    void setUp() {
        command = new EchoCommand();
        outputStream = new ByteArrayOutputStream();
        var output = new ConsoleOutputAdapter(new PrintStream(outputStream), false);
        ctx = new ExecutionContext(new ConsoleInputAdapter(), output);
    }

    @Test
    @DisplayName("execute should print text")
    void executePrintsText() {
        CommandResult result = command.execute(ctx, new String[]{"Hello", "World"});

        assertInstanceOf(CommandResult.Success.class, result);
        assertTrue(outputStream.toString().contains("Hello World"));
    }

    @Test
    @DisplayName("execute with --uppercase should convert to uppercase")
    void executeWithUppercase() {
        // Positional args before flags
        CommandResult result = command.execute(ctx, new String[]{"hello", "--uppercase"});

        assertInstanceOf(CommandResult.Success.class, result);
        assertTrue(outputStream.toString().contains("HELLO"));
    }

    @Test
    @DisplayName("execute with -u should convert to uppercase")
    void executeWithShortUppercase() {
        // Positional args before flags
        CommandResult result = command.execute(ctx, new String[]{"hello", "-u"});

        assertInstanceOf(CommandResult.Success.class, result);
        assertTrue(outputStream.toString().contains("HELLO"));
    }

    @Test
    @DisplayName("execute with --lowercase should convert to lowercase")
    void executeWithLowercase() {
        // Positional args before flags
        CommandResult result = command.execute(ctx, new String[]{"HELLO", "--lowercase"});

        assertInstanceOf(CommandResult.Success.class, result);
        assertTrue(outputStream.toString().contains("hello"));
    }

    @Test
    @DisplayName("execute with --repeat should repeat text")
    void executeWithRepeat() {
        CommandResult result = command.execute(ctx, new String[]{"test", "--repeat", "3"});

        assertInstanceOf(CommandResult.Success.class, result);
        String output = outputStream.toString();
        assertTrue(output.contains("test test test"));
    }

    @Test
    @DisplayName("execute with --separator should use custom separator")
    void executeWithSeparator() {
        CommandResult result = command.execute(ctx, new String[]{"-r", "3", "-s", "|", "test"});

        assertInstanceOf(CommandResult.Success.class, result);
        assertTrue(outputStream.toString().contains("test|test|test"));
    }

    @Test
    @DisplayName("execute with no args should print empty line")
    void executeWithNoArgs() {
        CommandResult result = command.execute(ctx, new String[]{});

        assertInstanceOf(CommandResult.Success.class, result);
    }

    @Test
    @DisplayName("info should have correct metadata")
    void infoHasCorrectMetadata() {
        var info = command.info();

        assertEquals("echo", info.name());
        assertTrue(info.aliases().contains("e"));
        assertEquals("text", info.category());
    }
}
