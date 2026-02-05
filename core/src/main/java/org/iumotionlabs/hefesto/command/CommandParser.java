package org.iumotionlabs.hefesto.command;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for command line arguments.
 */
public final class CommandParser {

    private static final Pattern QUOTED_STRING = Pattern.compile("\"([^\"]*)\"|'([^']*)'|(\\S+)");

    private CommandParser() {
    }

    /**
     * Parses a command line string into tokens.
     */
    public static String[] tokenize(String input) {
        if (input == null || input.isBlank()) {
            return new String[0];
        }

        List<String> tokens = new ArrayList<>();
        Matcher matcher = QUOTED_STRING.matcher(input.trim());

        while (matcher.find()) {
            if (matcher.group(1) != null) {
                // Double quoted
                tokens.add(matcher.group(1));
            } else if (matcher.group(2) != null) {
                // Single quoted
                tokens.add(matcher.group(2));
            } else {
                // Unquoted
                tokens.add(matcher.group(3));
            }
        }

        return tokens.toArray(String[]::new);
    }

    /**
     * Parsed arguments with flags and positional args.
     */
    public record ParsedArgs(
        Map<String, String> flags,
        List<String> positional
    ) {
        public boolean hasFlag(String name) {
            return flags.containsKey(name);
        }

        public Optional<String> getFlag(String name) {
            return Optional.ofNullable(flags.get(name));
        }

        public String getFlag(String name, String defaultValue) {
            return flags.getOrDefault(name, defaultValue);
        }

        public Optional<Integer> getFlagAsInt(String name) {
            return getFlag(name).map(s -> {
                try {
                    return Integer.parseInt(s);
                } catch (NumberFormatException e) {
                    return null;
                }
            });
        }

        public int getFlagAsInt(String name, int defaultValue) {
            return getFlagAsInt(name).orElse(defaultValue);
        }

        public boolean getBoolean(String name) {
            return hasFlag(name);
        }

        public String positional(int index) {
            return index < positional.size() ? positional.get(index) : null;
        }

        public Optional<String> getPositional(int index) {
            return Optional.ofNullable(positional(index));
        }

        public boolean hasPositional() {
            return !positional.isEmpty();
        }

        public int positionalCount() {
            return positional.size();
        }
    }

    /**
     * Parses arguments into flags and positional arguments.
     * Flags can be:
     * - --flag value
     * - --flag=value
     * - -f value
     * - -f (boolean flag)
     */
    public static ParsedArgs parse(String[] args) {
        Map<String, String> flags = new LinkedHashMap<>();
        List<String> positional = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            if (arg.startsWith("--")) {
                // Long flag
                String flagPart = arg.substring(2);
                int eqIndex = flagPart.indexOf('=');

                if (eqIndex > 0) {
                    // --flag=value
                    flags.put(flagPart.substring(0, eqIndex), flagPart.substring(eqIndex + 1));
                } else if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                    // --flag value
                    flags.put(flagPart, args[++i]);
                } else {
                    // --flag (boolean)
                    flags.put(flagPart, "true");
                }
            } else if (arg.startsWith("-") && arg.length() > 1) {
                // Short flag(s)
                String flagChars = arg.substring(1);

                if (flagChars.length() == 1) {
                    // Single short flag
                    if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                        flags.put(flagChars, args[++i]);
                    } else {
                        flags.put(flagChars, "true");
                    }
                } else {
                    // Multiple short flags (-abc = -a -b -c)
                    for (char c : flagChars.toCharArray()) {
                        flags.put(String.valueOf(c), "true");
                    }
                }
            } else {
                positional.add(arg);
            }
        }

        return new ParsedArgs(flags, positional);
    }

    /**
     * Parses a raw command line string.
     */
    public static ParsedArgs parse(String commandLine) {
        return parse(tokenize(commandLine));
    }
}
