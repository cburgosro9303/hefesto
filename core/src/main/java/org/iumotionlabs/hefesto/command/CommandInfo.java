package org.iumotionlabs.hefesto.command;

import org.iumotionlabs.hefesto.help.Documentation;

import java.util.List;
import java.util.Optional;

/**
 * Metadata record for a command.
 */
public record CommandInfo(
    String name,
    String description,
    String category,
    List<String> aliases,
    Optional<Documentation> documentation
) {
    public CommandInfo(String name, String description, String category) {
        this(name, description, category, List.of(), Optional.empty());
    }

    public CommandInfo(String name, String description) {
        this(name, description, "general", List.of(), Optional.empty());
    }

    /**
     * Returns a new CommandInfo with additional aliases.
     */
    public CommandInfo withAliases(String... aliases) {
        return new CommandInfo(name, description, category, List.of(aliases), documentation);
    }

    /**
     * Returns a new CommandInfo with documentation.
     */
    public CommandInfo withDocumentation(Documentation docs) {
        return new CommandInfo(name, description, category, aliases, Optional.of(docs));
    }

    /**
     * Checks if this command matches the given name or alias.
     */
    public boolean matches(String commandName) {
        if (name.equalsIgnoreCase(commandName)) {
            return true;
        }
        return aliases.stream()
            .anyMatch(alias -> alias.equalsIgnoreCase(commandName));
    }
}
