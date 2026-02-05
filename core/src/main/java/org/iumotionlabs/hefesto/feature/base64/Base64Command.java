package org.iumotionlabs.hefesto.feature.base64;

import org.iumotionlabs.hefesto.command.*;
import org.iumotionlabs.hefesto.core.context.ExecutionContext;
import org.iumotionlabs.hefesto.help.Documentation;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * Base64 command - encodes and decodes Base64 strings.
 */
public final class Base64Command implements Command {

    private static final CommandInfo INFO = new CommandInfo(
        "base64",
        "Codifica/decodifica texto en Base64",
        "encoding"
    ).withAliases("b64")
     .withDocumentation(Documentation.builder()
        .synopsis("base64 [opciones] <texto>")
        .description("Codifica texto a Base64 o decodifica texto desde Base64. " +
            "Por defecto codifica. Use --decode para decodificar.")
        .option("decode", "d", "Decodifica en lugar de codificar")
        .option("url", "Usa codificacion URL-safe")
        .option("mime", "Usa codificacion MIME (con saltos de linea)")
        .example("base64 'Hola Mundo'", "Codifica 'Hola Mundo' a Base64")
        .example("base64 -d 'SG9sYSBNdW5kbw=='", "Decodifica el texto Base64")
        .example("base64 --url 'test+data'", "Codifica con caracteres URL-safe")
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
            return CommandResult.failure("Se requiere texto para codificar/decodificar");
        }

        var parsed = CommandParser.parse(args);

        if (!parsed.hasPositional()) {
            return CommandResult.failure("Se requiere texto para codificar/decodificar");
        }

        String text = String.join(" ", parsed.positional());
        boolean decode = parsed.getBoolean("decode") || parsed.getBoolean("d");
        boolean urlSafe = parsed.getBoolean("url");
        boolean mime = parsed.getBoolean("mime");

        try {
            String result;

            if (decode) {
                // Decode
                Base64.Decoder decoder = urlSafe ? Base64.getUrlDecoder() :
                    mime ? Base64.getMimeDecoder() : Base64.getDecoder();

                byte[] decoded = decoder.decode(text.trim());
                result = new String(decoded, StandardCharsets.UTF_8);
            } else {
                // Encode
                Base64.Encoder encoder = urlSafe ? Base64.getUrlEncoder() :
                    mime ? Base64.getMimeEncoder() : Base64.getEncoder();

                result = encoder.encodeToString(text.getBytes(StandardCharsets.UTF_8));
            }

            ctx.output().println(result);
            return CommandResult.success();

        } catch (IllegalArgumentException e) {
            return CommandResult.failure("Texto Base64 invalido: " + e.getMessage());
        }
    }
}
