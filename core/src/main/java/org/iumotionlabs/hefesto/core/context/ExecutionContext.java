package org.iumotionlabs.hefesto.core.context;

import org.iumotionlabs.hefesto.core.port.input.InputPort;
import org.iumotionlabs.hefesto.core.port.output.OutputPort;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Execution context that encapsulates I/O ports and execution state.
 */
public record ExecutionContext(
    InputPort input,
    OutputPort output,
    Map<String, Object> properties
) {
    public ExecutionContext(InputPort input, OutputPort output) {
        this(input, output, new HashMap<>());
    }

    /**
     * Gets a property from the context.
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getProperty(String key, Class<T> type) {
        Object value = properties.get(key);
        if (value != null && type.isInstance(value)) {
            return Optional.of((T) value);
        }
        return Optional.empty();
    }

    /**
     * Sets a property in the context.
     */
    public ExecutionContext withProperty(String key, Object value) {
        Map<String, Object> newProps = new HashMap<>(properties);
        newProps.put(key, value);
        return new ExecutionContext(input, output, newProps);
    }

    /**
     * Checks if running in interactive mode.
     */
    public boolean isInteractive() {
        return input.isInteractive();
    }

    /**
     * Convenience method to print a line.
     */
    public void println(String message) {
        output.println(message);
    }

    /**
     * Convenience method to print an error.
     */
    public void error(String message) {
        output.error(message);
    }

    /**
     * Convenience method to print a success message.
     */
    public void success(String message) {
        output.success(message);
    }

    /**
     * Convenience method to print an info message.
     */
    public void info(String message) {
        output.info(message);
    }

    /**
     * Convenience method to read a line with prompt.
     */
    public Optional<String> readLine(String prompt) {
        return input.readLine(prompt);
    }

    /**
     * Convenience method to print a dimmed message.
     */
    public void dim(String message) {
        output.dim(message);
    }
}
