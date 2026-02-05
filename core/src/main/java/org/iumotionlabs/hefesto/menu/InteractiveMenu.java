package org.iumotionlabs.hefesto.menu;

import org.iumotionlabs.hefesto.command.*;
import org.iumotionlabs.hefesto.core.context.ExecutionContext;
import org.iumotionlabs.hefesto.core.port.output.OutputPort;
import org.iumotionlabs.hefesto.help.HelpRenderer;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Interactive menu for navigating and executing commands.
 */
public final class InteractiveMenu {

    private final CommandRegistry registry;
    private final ExecutionContext ctx;
    private boolean running = true;

    public InteractiveMenu(CommandRegistry registry, ExecutionContext ctx) {
        this.registry = registry;
        this.ctx = ctx;
    }

    /**
     * Starts the interactive menu loop.
     */
    public void run() {
        showWelcome();

        while (running) {
            Optional<String> input = ctx.readLine(OutputPort.CYAN + "hefesto> " + OutputPort.RESET);

            if (input.isEmpty()) {
                // EOF reached
                break;
            }

            String line = input.get().trim();

            if (line.isEmpty()) {
                continue;
            }

            processInput(line);
        }

        ctx.output().println();
        ctx.info("Hasta pronto!");
    }

    private void showWelcome() {
        var out = ctx.output();

        out.println();
        out.header("  HEFESTO - Utilidades de Consola  ");
        out.println();
        out.println("Escribe un comando o 'help' para ver la lista de comandos.");
        out.println("Escribe 'exit' o 'quit' para salir.");
        out.println();
    }

    private void processInput(String line) {
        String[] tokens = CommandParser.tokenize(line);

        if (tokens.length == 0) {
            return;
        }

        String commandName = tokens[0].toLowerCase();
        String[] args = new String[tokens.length - 1];
        System.arraycopy(tokens, 1, args, 0, args.length);

        // Built-in commands
        switch (commandName) {
            case "exit", "quit", "q" -> {
                running = false;
                return;
            }
            case "help", "?" -> {
                handleHelp(args);
                return;
            }
            case "list", "ls" -> {
                showCommandList();
                return;
            }
            case "clear", "cls" -> {
                clearScreen();
                return;
            }
        }

        // Find and execute command
        Optional<Command> command = registry.find(commandName);

        if (command.isEmpty()) {
            ctx.error("Comando desconocido: " + commandName);
            ctx.dim("Escribe 'help' para ver los comandos disponibles.");
            return;
        }

        executeCommand(command.get(), args);
    }

    private void handleHelp(String[] args) {
        var helpRenderer = new HelpRenderer(ctx);

        if (args.length == 0) {
            helpRenderer.renderGeneralHelp(registry);
        } else {
            String cmdName = args[0];
            Optional<Command> cmd = registry.find(cmdName);

            if (cmd.isPresent()) {
                helpRenderer.renderCommandHelp(cmd.get());
            } else {
                ctx.error("Comando desconocido: " + cmdName);
            }
        }
    }

    private void showCommandList() {
        var out = ctx.output();

        out.println();
        out.header("Comandos disponibles:");
        out.println();

        Map<String, List<Command>> byCategory = registry.byCategory();

        for (Map.Entry<String, List<Command>> entry : byCategory.entrySet()) {
            out.println(OutputPort.BOLD + "[" + entry.getKey().toUpperCase() + "]" + OutputPort.RESET);

            for (Command cmd : entry.getValue()) {
                var info = cmd.info();
                out.printf("  %-12s %s%n",
                    OutputPort.CYAN + info.name() + OutputPort.RESET,
                    info.description());
            }
            out.println();
        }
    }

    private void clearScreen() {
        // ANSI clear screen
        ctx.output().print("\033[H\033[2J");
    }

    private void executeCommand(Command command, String[] args) {
        // Check for --help flag
        for (String arg : args) {
            if (arg.equals("--help") || arg.equals("-h")) {
                var helpRenderer = new HelpRenderer(ctx);
                helpRenderer.renderCommandHelp(command);
                return;
            }
        }

        try {
            CommandResult result = command.execute(ctx, args);

            switch (result) {
                case CommandResult.Success success -> {
                    if (!success.message().isEmpty()) {
                        ctx.success(success.message());
                    }
                }
                case CommandResult.Failure failure -> {
                    ctx.error("Error: " + failure.error());
                    if (failure.cause() != null) {
                        ctx.dim(failure.cause().getMessage());
                    }
                }
                case CommandResult.Exit exit -> {
                    running = false;
                }
                case CommandResult.Continue ignored -> {
                    // Do nothing, continue loop
                }
            }
        } catch (Exception e) {
            ctx.error("Error ejecutando comando: " + e.getMessage());
        }
    }
}
