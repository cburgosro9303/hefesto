package org.iumotionlabs.hefesto.core.adapter.console;

import org.iumotionlabs.hefesto.core.port.input.InputPort;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Optional;

/**
 * Console implementation of InputPort.
 */
public final class ConsoleInputAdapter implements InputPort {

    private final Console console;
    private final BufferedReader reader;

    public ConsoleInputAdapter() {
        this.console = System.console();
        this.reader = new BufferedReader(new InputStreamReader(System.in));
    }

    @Override
    public Optional<String> readLine() {
        return readLine("");
    }

    @Override
    public Optional<String> readLine(String prompt) {
        try {
            if (console != null) {
                String line = console.readLine(prompt);
                return Optional.ofNullable(line);
            } else {
                if (!prompt.isEmpty()) {
                    System.out.print(prompt);
                    System.out.flush();
                }
                String line = reader.readLine();
                return Optional.ofNullable(line);
            }
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<char[]> readPassword(String prompt) {
        if (console != null) {
            char[] password = console.readPassword(prompt);
            return Optional.ofNullable(password);
        }
        // Fallback: read as plain text (not ideal but functional)
        return readLine(prompt).map(String::toCharArray);
    }

    @Override
    public boolean isInteractive() {
        return console != null || System.in != null;
    }
}
