package org.iumotionlabs.hefesto.command;

import org.iumotionlabs.hefesto.core.context.ExecutionContext;
import org.iumotionlabs.hefesto.help.HelpProvider;

/**
 * Base interface for all commands in Hefesto.
 */
public interface Command extends HelpProvider {

    /**
     * Returns metadata about this command.
     */
    CommandInfo info();

    /**
     * Executes the command with the given context and arguments.
     *
     * @param ctx  the execution context
     * @param args command line arguments (excluding the command name)
     * @return the result of the execution
     */
    CommandResult execute(ExecutionContext ctx, String[] args);

    /**
     * Returns the command name (convenience).
     */
    default String name() {
        return info().name();
    }

    /**
     * Returns the command description (convenience).
     */
    default String description() {
        return info().description();
    }
}
