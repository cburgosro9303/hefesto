package org.iumotionlabs.hefesto.feature.json;

import lombok.extern.log4j.Log4j2;
import org.iumotionlabs.hefesto.command.*;
import org.iumotionlabs.hefesto.core.context.ExecutionContext;
import org.iumotionlabs.hefesto.help.Documentation;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * JSON command - formats and validates JSON.
 */
@Log4j2
public final class JsonCommand implements Command {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ObjectMapper PRETTY_MAPPER = JsonMapper.builder()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .build();

    private static final CommandInfo INFO = new CommandInfo(
        "json",
        "Formatea y valida JSON",
        "encoding"
    ).withAliases("jq")
     .withDocumentation(Documentation.builder()
        .synopsis("json [opciones] <json|archivo>")
        .description("Formatea JSON para mejor legibilidad o valida su estructura. " +
            "Puede recibir JSON directamente o un archivo.")
        .option("validate", "v", "Solo valida sin formatear")
        .option("compact", "c", "Salida compacta (sin espacios)")
        .option("file", "f", "Lee desde un archivo")
        .example("json '{\"name\":\"test\"}'", "Formatea el JSON")
        .example("json -v '{\"invalid'", "Valida el JSON (reporta error)")
        .example("json -f data.json", "Formatea el contenido del archivo")
        .example("json -c '{\"a\": 1, \"b\": 2}'", "Compacta el JSON")
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
        log.debug("Executing json command with {} arguments", args.length);

        if (args.length == 0) {
            log.debug("No arguments provided");
            return CommandResult.failure("Se requiere JSON o un archivo");
        }

        var parsed = CommandParser.parse(args);
        log.debug("Parsed flags: {}, positional args: {}", parsed.flags(), parsed.positional());

        if (!parsed.hasPositional()) {
            return CommandResult.failure("Se requiere JSON o un archivo");
        }

        boolean validate = parsed.getBoolean("validate") || parsed.getBoolean("v");
        boolean compact = parsed.getBoolean("compact") || parsed.getBoolean("c");
        boolean fromFile = parsed.getBoolean("file") || parsed.getBoolean("f");

        log.debug("Options - validate: {}, compact: {}, fromFile: {}", validate, compact, fromFile);

        String input = String.join(" ", parsed.positional());
        log.debug("Input length: {} characters", input.length());

        try {
            String jsonContent;

            if (fromFile) {
                Path path = Path.of(input);
                log.debug("Reading from file: {}", path.toAbsolutePath());
                if (!Files.exists(path)) {
                    log.debug("File not found: {}", path);
                    return CommandResult.failure("Archivo no encontrado: " + input);
                }
                jsonContent = Files.readString(path);
                log.debug("Read {} characters from file", jsonContent.length());
            } else {
                jsonContent = input;
            }

            // Parse JSON
            log.debug("Parsing JSON...");
            JsonNode node = MAPPER.readTree(jsonContent);
            log.debug("JSON parsed successfully. Node type: {}", node.getNodeType());

            if (validate) {
                log.debug("Validation mode - JSON is valid");
                ctx.success("JSON valido");
                return CommandResult.success();
            }

            // Format output
            String output;
            if (compact) {
                log.debug("Generating compact output");
                output = MAPPER.writeValueAsString(node);
            } else {
                log.debug("Generating pretty output");
                output = PRETTY_MAPPER.writeValueAsString(node);
            }

            log.debug("Output generated: {} characters", output.length());
            ctx.output().println(output);
            return CommandResult.success();

        } catch (tools.jackson.core.JacksonException e) {
            log.debug("JSON parsing error: {}", e.getOriginalMessage());
            String message = "JSON invalido: " + e.getOriginalMessage();
            if (validate) {
                ctx.error(message);
                return CommandResult.failure("Validacion fallida");
            }
            return CommandResult.failure(message);
        } catch (IOException e) {
            log.debug("IO error: {}", e.getMessage());
            return CommandResult.failure("Error leyendo archivo: " + e.getMessage());
        }
    }
}
