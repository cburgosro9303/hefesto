package org.iumotionlabs.hefesto.help;

import org.iumotionlabs.hefesto.command.Command;
import org.iumotionlabs.hefesto.command.CommandRegistry;
import org.iumotionlabs.hefesto.core.context.ExecutionContext;
import org.iumotionlabs.hefesto.core.port.output.OutputPort;

import java.util.List;
import java.util.Map;

/**
 * Renders help documentation to the output.
 */
public final class HelpRenderer {

    private final ExecutionContext ctx;

    public HelpRenderer(ExecutionContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Renders general help showing all commands.
     */
    public void renderGeneralHelp(CommandRegistry registry) {
        var out = ctx.output();

        out.header("Hefesto - Utilidades de Consola");
        out.println();
        out.println("Uso: hefesto <comando> [opciones] [argumentos]");
        out.println("     hefesto --help | -h");
        out.println("     hefesto (modo interactivo)");
        out.println();

        Map<String, List<Command>> byCategory = registry.byCategory();

        for (Map.Entry<String, List<Command>> entry : byCategory.entrySet()) {
            out.println(OutputPort.BOLD + capitalize(entry.getKey()) + ":" + OutputPort.RESET);

            for (Command cmd : entry.getValue()) {
                var info = cmd.info();
                String aliases = info.aliases().isEmpty() ? "" :
                    " (" + String.join(", ", info.aliases()) + ")";

                out.printf("  %-15s %s%s%n",
                    OutputPort.CYAN + info.name() + OutputPort.RESET + aliases,
                    info.description(),
                    "");
            }
            out.println();
        }

        out.dim("Use 'hefesto <comando> --help' para ayuda detallada de un comando.");
    }

    /**
     * Renders detailed help for a specific command.
     */
    public void renderCommandHelp(Command command) {
        var out = ctx.output();
        var info = command.info();
        var doc = command.documentation();

        out.header(info.name().toUpperCase());
        out.println();

        // Name and aliases
        out.println(OutputPort.BOLD + "NOMBRE" + OutputPort.RESET);
        String aliases = info.aliases().isEmpty() ? "" :
            " (alias: " + String.join(", ", info.aliases()) + ")";
        out.println("    " + info.name() + aliases + " - " + info.description());
        out.println();

        if (doc.isPresent()) {
            var d = doc.get();

            // Synopsis
            if (d.synopsis() != null && !d.synopsis().isEmpty()) {
                out.println(OutputPort.BOLD + "SINOPSIS" + OutputPort.RESET);
                out.println("    " + d.synopsis());
                out.println();
            }

            // Description
            if (d.description() != null && !d.description().isEmpty()) {
                out.println(OutputPort.BOLD + "DESCRIPCION" + OutputPort.RESET);
                for (String line : wrapText(d.description(), 70)) {
                    out.println("    " + line);
                }
                out.println();
            }

            // Options
            if (!d.options().isEmpty()) {
                out.println(OutputPort.BOLD + "OPCIONES" + OutputPort.RESET);
                for (var opt : d.options()) {
                    String shortOpt = opt.shortName() != null ? "-" + opt.shortName() + ", " : "    ";
                    String longOpt = "--" + opt.name();
                    String defaultVal = opt.defaultValue()
                        .map(v -> " (default: " + v + ")")
                        .orElse("");
                    String required = opt.isRequired() ? " [requerido]" : "";

                    out.printf("    %s%-20s %s%s%s%n",
                        shortOpt, longOpt, opt.description(), defaultVal, required);
                }
                out.println();
            }

            // Examples
            if (!d.examples().isEmpty()) {
                out.println(OutputPort.BOLD + "EJEMPLOS" + OutputPort.RESET);
                for (var ex : d.examples()) {
                    out.println("    " + OutputPort.CYAN + ex.command() + OutputPort.RESET);
                    out.println("        " + ex.description());
                    out.println();
                }
            }

            // See also
            d.seeAlso().ifPresent(see -> {
                out.println(OutputPort.BOLD + "VER TAMBIEN" + OutputPort.RESET);
                out.println("    " + see);
                out.println();
            });
        } else {
            out.dim("    No hay documentacion detallada disponible.");
            out.println();
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private List<String> wrapText(String text, int width) {
        List<String> lines = new java.util.ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();

        for (String word : words) {
            if (line.length() + word.length() + 1 > width) {
                lines.add(line.toString());
                line = new StringBuilder();
            }
            if (!line.isEmpty()) {
                line.append(" ");
            }
            line.append(word);
        }

        if (!line.isEmpty()) {
            lines.add(line.toString());
        }

        return lines;
    }
}
