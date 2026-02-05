package org.iumotionlabs.hefesto.core.adapter.console;

import org.iumotionlabs.hefesto.core.port.output.OutputPort;

import java.io.PrintStream;

/**
 * Console implementation of OutputPort.
 */
public final class ConsoleOutputAdapter implements OutputPort {

    private final PrintStream out;
    private final boolean colorsEnabled;

    public ConsoleOutputAdapter() {
        this(System.out, detectColorSupport());
    }

    public ConsoleOutputAdapter(PrintStream out, boolean colorsEnabled) {
        this.out = out;
        this.colorsEnabled = colorsEnabled;
    }

    private static boolean detectColorSupport() {
        // Check if running in a terminal that supports colors
        String term = System.getenv("TERM");
        String colorTerm = System.getenv("COLORTERM");
        boolean isTty = System.console() != null;

        return isTty && (term != null && !term.equals("dumb")) ||
               (colorTerm != null && !colorTerm.isEmpty());
    }

    @Override
    public void print(String message) {
        out.print(stripColorsIfNeeded(message));
    }

    @Override
    public void println(String message) {
        out.println(stripColorsIfNeeded(message));
    }

    @Override
    public void println() {
        out.println();
    }

    @Override
    public void printf(String format, Object... args) {
        out.printf(stripColorsIfNeeded(format), args);
    }

    @Override
    public boolean supportsColors() {
        return colorsEnabled;
    }

    @Override
    public void flush() {
        out.flush();
    }

    private String stripColorsIfNeeded(String message) {
        if (colorsEnabled) {
            return message;
        }
        // Remove ANSI codes if colors are not supported
        return message.replaceAll("\u001B\\[[;\\d]*m", "");
    }
}
