package org.iumotionlabs.hefesto.command;

/**
 * Sealed interface representing the result of a command execution.
 */
public sealed interface CommandResult {

    /**
     * Successful execution.
     */
    record Success(String message) implements CommandResult {
        public Success() {
            this("");
        }
    }

    /**
     * Failed execution with error.
     */
    record Failure(String error, Throwable cause) implements CommandResult {
        public Failure(String error) {
            this(error, null);
        }
    }

    /**
     * Request to exit the application.
     */
    record Exit(int code) implements CommandResult {
        public static final Exit NORMAL = new Exit(0);

        public Exit() {
            this(0);
        }
    }

    /**
     * Continue execution (for menu navigation).
     */
    record Continue() implements CommandResult {}

    // Factory methods
    static CommandResult success() {
        return new Success();
    }

    static CommandResult success(String message) {
        return new Success(message);
    }

    static CommandResult failure(String error) {
        return new Failure(error);
    }

    static CommandResult failure(String error, Throwable cause) {
        return new Failure(error, cause);
    }

    static CommandResult exit() {
        return Exit.NORMAL;
    }

    static CommandResult exit(int code) {
        return new Exit(code);
    }

    static CommandResult cont() {
        return new Continue();
    }
}
