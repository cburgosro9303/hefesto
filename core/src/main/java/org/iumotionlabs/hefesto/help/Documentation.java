package org.iumotionlabs.hefesto.help;

import java.util.List;
import java.util.Optional;

/**
 * Record containing structured documentation for a command.
 */
public record Documentation(
    String synopsis,
    String description,
    List<OptionDoc> options,
    List<ExampleDoc> examples,
    Optional<String> seeAlso
) {
    public Documentation(String synopsis, String description, List<OptionDoc> options, List<ExampleDoc> examples) {
        this(synopsis, description, options, examples, Optional.empty());
    }

    /**
     * Documentation for a command option.
     */
    public record OptionDoc(
        String name,
        String shortName,
        String description,
        boolean required,
        Optional<String> defaultValue
    ) {
        public OptionDoc(String name, String description) {
            this(name, null, description, false, Optional.empty());
        }

        public OptionDoc(String name, String shortName, String description) {
            this(name, shortName, description, false, Optional.empty());
        }

        public OptionDoc withDefault(String defaultValue) {
            return new OptionDoc(name, shortName, description, required, Optional.of(defaultValue));
        }

        public OptionDoc asRequired() {
            return new OptionDoc(name, shortName, description, true, defaultValue);
        }

        public boolean isRequired() {
            return required;
        }
    }

    /**
     * Documentation for an example usage.
     */
    public record ExampleDoc(
        String command,
        String description
    ) {}

    /**
     * Builder for creating Documentation instances.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        public Builder() {}

        private String synopsis;
        private String description;
        private final List<OptionDoc> options = new java.util.ArrayList<>();
        private final List<ExampleDoc> examples = new java.util.ArrayList<>();
        private String seeAlso;

        public Builder synopsis(String synopsis) {
            this.synopsis = synopsis;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder option(String name, String desc) {
            options.add(new OptionDoc(name, desc));
            return this;
        }

        public Builder option(String name, String shortName, String desc) {
            options.add(new OptionDoc(name, shortName, desc));
            return this;
        }

        public Builder option(OptionDoc option) {
            options.add(option);
            return this;
        }

        public Builder example(String command, String desc) {
            examples.add(new ExampleDoc(command, desc));
            return this;
        }

        public Builder seeAlso(String seeAlso) {
            this.seeAlso = seeAlso;
            return this;
        }

        public Documentation build() {
            return new Documentation(
                synopsis,
                description,
                List.copyOf(options),
                List.copyOf(examples),
                Optional.ofNullable(seeAlso)
            );
        }
    }
}
