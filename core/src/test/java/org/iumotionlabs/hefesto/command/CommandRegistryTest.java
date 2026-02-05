package org.iumotionlabs.hefesto.command;

import org.iumotionlabs.hefesto.core.context.ExecutionContext;
import org.iumotionlabs.hefesto.help.Documentation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CommandRegistry Tests")
class CommandRegistryTest {

    private CommandRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new CommandRegistry();
    }

    @Test
    @DisplayName("register should add command to registry")
    void registerAddsCommand() {
        Command cmd = createTestCommand("test", "A test command");

        registry.register(cmd);

        assertTrue(registry.exists("test"));
        assertEquals(1, registry.size());
    }

    @Test
    @DisplayName("find should return command by name")
    void findReturnsByName() {
        Command cmd = createTestCommand("test", "A test command");
        registry.register(cmd);

        Optional<Command> found = registry.find("test");

        assertTrue(found.isPresent());
        assertEquals("test", found.get().name());
    }

    @Test
    @DisplayName("find should be case insensitive")
    void findIsCaseInsensitive() {
        registry.register(createTestCommand("Test", "A test command"));

        assertTrue(registry.find("test").isPresent());
        assertTrue(registry.find("TEST").isPresent());
        assertTrue(registry.find("Test").isPresent());
    }

    @Test
    @DisplayName("find should return command by alias")
    void findReturnsByAlias() {
        Command cmd = createTestCommandWithAlias("echo", "Echo command", "e");
        registry.register(cmd);

        assertTrue(registry.find("echo").isPresent());
        assertTrue(registry.find("e").isPresent());
    }

    @Test
    @DisplayName("find should return empty for unknown command")
    void findReturnsEmptyForUnknown() {
        Optional<Command> found = registry.find("nonexistent");

        assertFalse(found.isPresent());
    }

    @Test
    @DisplayName("all should return all registered commands")
    void allReturnsAllCommands() {
        registry.register(createTestCommand("cmd1", "Command 1"));
        registry.register(createTestCommand("cmd2", "Command 2"));

        var all = registry.all();

        assertEquals(2, all.size());
    }

    @Test
    @DisplayName("byCategory should group commands")
    void byCategoryGroupsCommands() {
        registry.register(createTestCommandInCategory("cmd1", "Command 1", "category1"));
        registry.register(createTestCommandInCategory("cmd2", "Command 2", "category1"));
        registry.register(createTestCommandInCategory("cmd3", "Command 3", "category2"));

        var byCategory = registry.byCategory();

        assertEquals(2, byCategory.size());
        assertEquals(2, byCategory.get("category1").size());
        assertEquals(1, byCategory.get("category2").size());
    }

    @Test
    @DisplayName("names should return all command names")
    void namesReturnsAllNames() {
        registry.register(createTestCommand("cmd1", "Command 1"));
        registry.register(createTestCommand("cmd2", "Command 2"));

        var names = registry.names();

        assertTrue(names.contains("cmd1"));
        assertTrue(names.contains("cmd2"));
        assertEquals(2, names.size());
    }

    private Command createTestCommand(String name, String description) {
        return new TestCommand(new CommandInfo(name, description));
    }

    private Command createTestCommandWithAlias(String name, String description, String... aliases) {
        return new TestCommand(new CommandInfo(name, description).withAliases(aliases));
    }

    private Command createTestCommandInCategory(String name, String description, String category) {
        return new TestCommand(new CommandInfo(name, description, category));
    }

    private record TestCommand(CommandInfo info) implements Command {
        @Override
        public CommandResult execute(ExecutionContext ctx, String[] args) {
            return CommandResult.success();
        }

        @Override
        public Optional<Documentation> documentation() {
            return Optional.empty();
        }
    }
}
