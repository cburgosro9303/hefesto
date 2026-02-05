package org.iumotionlabs.hefesto.core.port.input;

import java.util.Optional;

/**
 * Input port for reading user input.
 * Sealed to control implementations.
 */
public sealed interface InputPort
    permits org.iumotionlabs.hefesto.core.adapter.console.ConsoleInputAdapter {

    /**
     * Reads a line of input from the user.
     *
     * @return the input line, or empty if no input available
     */
    Optional<String> readLine();

    /**
     * Reads a line with a prompt.
     *
     * @param prompt the prompt to display
     * @return the input line, or empty if no input available
     */
    Optional<String> readLine(String prompt);

    /**
     * Reads a password (hidden input).
     *
     * @param prompt the prompt to display
     * @return the password, or empty if not available
     */
    Optional<char[]> readPassword(String prompt);

    /**
     * Checks if input is available (interactive mode).
     *
     * @return true if interactive input is available
     */
    boolean isInteractive();
}
