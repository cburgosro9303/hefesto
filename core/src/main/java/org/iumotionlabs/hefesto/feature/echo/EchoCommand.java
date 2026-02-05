package org.iumotionlabs.hefesto.feature.echo;

import org.iumotionlabs.hefesto.command.*;
import org.iumotionlabs.hefesto.core.context.ExecutionContext;
import org.iumotionlabs.hefesto.help.Documentation;

import java.util.Optional;

/**
 * Echo command - displays text with optional transformations.
 */
public final class EchoCommand implements Command {

    private static final CommandInfo INFO = new CommandInfo(
        "echo",
        "Muestra texto con transformaciones opcionales",
        "text"
    ).withAliases("e")
     .withDocumentation(Documentation.builder()
        .synopsis("echo [opciones] <texto>")
        .description("Muestra el texto proporcionado en la salida. " +
            "Soporta transformaciones como mayusculas, minusculas y repeticion.")
        .option("uppercase", "u", "Convierte a mayusculas")
        .option("lowercase", "l", "Convierte a minusculas")
        .option("repeat", "r", "Numero de veces a repetir")
        .option("separator", "s", "Separador entre repeticiones")
        .example("echo Hola Mundo", "Muestra 'Hola Mundo'")
        .example("echo -u 'hola mundo'", "Muestra 'HOLA MUNDO'")
        .example("echo -r 3 -s '|' test", "Muestra 'test|test|test'")
        .build());

    @Override
    public CommandInfo info() {
        return INFO;
    }

    @Override
    public Optional<Documentation> documentation() {
        return INFO.documentation();
    }

    @Override
    public CommandResult execute(ExecutionContext ctx, String[] args) {
        if (args.length == 0) {
            ctx.output().println();
            return CommandResult.success();
        }

        var parsed = CommandParser.parse(args);

        // Build the text from positional arguments
        String text = String.join(" ", parsed.positional());

        if (text.isEmpty()) {
            ctx.output().println();
            return CommandResult.success();
        }

        // Apply transformations
        if (parsed.getBoolean("uppercase") || parsed.getBoolean("u")) {
            text = text.toUpperCase();
        } else if (parsed.getBoolean("lowercase") || parsed.getBoolean("l")) {
            text = text.toLowerCase();
        }

        // Handle repetition
        int repeat = parsed.getFlagAsInt("repeat", 1);
        repeat = Math.max(1, parsed.getFlagAsInt("r", repeat));
        repeat = Math.min(repeat, 100); // Safety limit

        String separator = parsed.getFlag("separator", " ");
        separator = parsed.getFlag("s", separator);

        StringBuilder output = new StringBuilder();
        for (int i = 0; i < repeat; i++) {
            if (i > 0) {
                output.append(separator);
            }
            output.append(text);
        }

        ctx.output().println(output.toString());
        return CommandResult.success();
    }
}
