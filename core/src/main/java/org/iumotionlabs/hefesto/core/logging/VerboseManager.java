package org.iumotionlabs.hefesto.core.logging;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;

/**
 * Manages verbose mode for the application.
 * Allows dynamic log level changes at runtime.
 */
public final class VerboseManager {

    private static boolean verboseEnabled = false;

    private VerboseManager() {
    }

    /**
     * Enables verbose mode, setting log level to DEBUG.
     */
    public static void enableVerbose() {
        verboseEnabled = true;
        Configurator.setLevel("org.iumotionlabs.hefesto", Level.DEBUG);
    }

    /**
     * Disables verbose mode, setting log level to INFO.
     */
    public static void disableVerbose() {
        verboseEnabled = false;
        Configurator.setLevel("org.iumotionlabs.hefesto", Level.INFO);
    }

    /**
     * Sets verbose mode.
     */
    public static void setVerbose(boolean enabled) {
        if (enabled) {
            enableVerbose();
        } else {
            disableVerbose();
        }
    }

    /**
     * Checks if verbose mode is enabled.
     */
    public static boolean isVerbose() {
        return verboseEnabled;
    }
}
