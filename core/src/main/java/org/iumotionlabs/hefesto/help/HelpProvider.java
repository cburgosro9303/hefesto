package org.iumotionlabs.hefesto.help;

import java.util.Optional;

/**
 * Interface for objects that can provide help documentation.
 */
public interface HelpProvider {

    /**
     * Returns the documentation for this provider.
     */
    default Optional<Documentation> documentation() {
        return Optional.empty();
    }

    /**
     * Returns a short one-line help text.
     */
    default String shortHelp() {
        return documentation()
            .map(Documentation::synopsis)
            .orElse("");
    }
}
