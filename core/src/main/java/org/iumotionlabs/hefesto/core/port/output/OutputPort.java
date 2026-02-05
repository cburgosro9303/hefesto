package org.iumotionlabs.hefesto.core.port.output;

/**
 * Output port for displaying information to the user.
 * Sealed to control implementations.
 */
public sealed interface OutputPort
    permits org.iumotionlabs.hefesto.core.adapter.console.ConsoleOutputAdapter {

    // ANSI color codes
    String RESET = "\u001B[0m";
    String BLACK = "\u001B[30m";
    String RED = "\u001B[31m";
    String GREEN = "\u001B[32m";
    String YELLOW = "\u001B[33m";
    String BLUE = "\u001B[34m";
    String PURPLE = "\u001B[35m";
    String CYAN = "\u001B[36m";
    String WHITE = "\u001B[37m";
    String BOLD = "\u001B[1m";
    String DIM = "\u001B[2m";
    String UNDERLINE = "\u001B[4m";

    /**
     * Prints a message without newline.
     */
    void print(String message);

    /**
     * Prints a message with newline.
     */
    void println(String message);

    /**
     * Prints an empty line.
     */
    void println();

    /**
     * Prints a formatted message.
     */
    void printf(String format, Object... args);

    /**
     * Prints an info message (cyan).
     */
    default void info(String message) {
        println(CYAN + message + RESET);
    }

    /**
     * Prints a success message (green).
     */
    default void success(String message) {
        println(GREEN + message + RESET);
    }

    /**
     * Prints a warning message (yellow).
     */
    default void warning(String message) {
        println(YELLOW + message + RESET);
    }

    /**
     * Prints an error message (red).
     */
    default void error(String message) {
        println(RED + message + RESET);
    }

    /**
     * Prints a header (bold + underline).
     */
    default void header(String message) {
        println(BOLD + UNDERLINE + message + RESET);
    }

    /**
     * Prints dimmed text.
     */
    default void dim(String message) {
        println(DIM + message + RESET);
    }

    /**
     * Checks if colors are supported.
     */
    boolean supportsColors();

    /**
     * Flushes the output stream.
     */
    default void flush() {
        // Default implementation does nothing
    }
}
