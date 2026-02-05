package org.iumotionlabs.hefesto.command;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Central registry for all commands.
 */
public final class CommandRegistry {

    private final Map<String, Command> commands = new LinkedHashMap<>();
    private final Map<String, String> aliasToName = new HashMap<>();

    public CommandRegistry() {
    }

    /**
     * Registers a command.
     */
    public void register(Command command) {
        String name = command.info().name().toLowerCase();
        commands.put(name, command);

        // Register aliases
        for (String alias : command.info().aliases()) {
            aliasToName.put(alias.toLowerCase(), name);
        }
    }

    /**
     * Finds a command by name or alias.
     */
    public Optional<Command> find(String nameOrAlias) {
        String key = nameOrAlias.toLowerCase();

        // Direct lookup
        Command cmd = commands.get(key);
        if (cmd != null) {
            return Optional.of(cmd);
        }

        // Alias lookup
        String resolved = aliasToName.get(key);
        if (resolved != null) {
            return Optional.ofNullable(commands.get(resolved));
        }

        return Optional.empty();
    }

    /**
     * Returns all registered commands.
     */
    public Collection<Command> all() {
        return Collections.unmodifiableCollection(commands.values());
    }

    /**
     * Returns commands grouped by category.
     */
    public Map<String, List<Command>> byCategory() {
        return commands.values().stream()
            .collect(Collectors.groupingBy(
                cmd -> cmd.info().category(),
                LinkedHashMap::new,
                Collectors.toList()
            ));
    }

    /**
     * Returns all command names (excluding aliases).
     */
    public Set<String> names() {
        return Collections.unmodifiableSet(commands.keySet());
    }

    /**
     * Returns all aliases mapped to their command names.
     */
    public Map<String, String> aliases() {
        return Collections.unmodifiableMap(aliasToName);
    }

    /**
     * Checks if a command exists.
     */
    public boolean exists(String nameOrAlias) {
        return find(nameOrAlias).isPresent();
    }

    /**
     * Returns the number of registered commands.
     */
    public int size() {
        return commands.size();
    }
}
