package org.iumotionlabs.hefesto;

import lombok.extern.log4j.Log4j2;
import org.iumotionlabs.hefesto.command.*;
import org.iumotionlabs.hefesto.core.adapter.console.ConsoleInputAdapter;
import org.iumotionlabs.hefesto.core.adapter.console.ConsoleOutputAdapter;
import org.iumotionlabs.hefesto.core.context.ExecutionContext;
import org.iumotionlabs.hefesto.core.logging.VerboseManager;
import org.iumotionlabs.hefesto.feature.base64.Base64Command;
import org.iumotionlabs.hefesto.feature.echo.EchoCommand;
import org.iumotionlabs.hefesto.feature.json.JsonCommand;
import org.iumotionlabs.hefesto.feature.portinfo.PortInfoCommand;
import org.iumotionlabs.hefesto.feature.procwatch.ProcWatchCommand;
import org.iumotionlabs.hefesto.help.HelpRenderer;
import org.iumotionlabs.hefesto.menu.InteractiveMenu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Main entry point for Hefesto console utilities application.
 */
@Log4j2
public final class HefestoApplication {

    private final CommandRegistry registry;
    private final ExecutionContext ctx;

    public HefestoApplication() {
        this.registry = new CommandRegistry();
        this.ctx = new ExecutionContext(
            new ConsoleInputAdapter(),
            new ConsoleOutputAdapter()
        );

        registerCommands();
    }

    private void registerCommands() {
        log.debug("Registering commands...");

        // Text utilities
        registry.register(new EchoCommand());
        log.debug("Registered: echo");

        // Encoding utilities
        registry.register(new Base64Command());
        log.debug("Registered: base64");

        registry.register(new JsonCommand());
        log.debug("Registered: json");

        // Network utilities
        registry.register(new PortInfoCommand());
        log.debug("Registered: portinfo");

        // System monitoring utilities
        registry.register(new ProcWatchCommand());
        log.debug("Registered: proc-watch");

        log.debug("Total commands registered: {}", registry.size());
    }

    /**
     * Runs the application with the given arguments.
     */
    public int run(String[] args) {
        // Process global flags first
        List<String> remainingArgs = processGlobalFlags(args);

        if (remainingArgs.isEmpty()) {
            // Interactive mode
            return runInteractive();
        }

        String firstArg = remainingArgs.getFirst();

        // Check for help flag
        if (firstArg.equals("--help") || firstArg.equals("-h")) {
            showHelp();
            return 0;
        }

        if (firstArg.equals("--version")) {
            showVersion();
            return 0;
        }

        // CLI mode
        return runCommand(remainingArgs);
    }

    /**
     * Processes global flags like --verbose and returns remaining arguments.
     */
    private List<String> processGlobalFlags(String[] args) {
        List<String> remaining = new ArrayList<>();

        for (String arg : args) {
            switch (arg) {
                case "--verbose", "-V" -> {
                    VerboseManager.enableVerbose();
                    log.debug("Verbose mode enabled");
                }
                default -> remaining.add(arg);
            }
        }

        return remaining;
    }

    private int runInteractive() {
        log.debug("Starting interactive mode");
        var menu = new InteractiveMenu(registry, ctx);
        menu.run();
        return 0;
    }

    private int runCommand(List<String> args) {
        String commandName = args.getFirst();
        String[] commandArgs = args.subList(1, args.size()).toArray(String[]::new);

        log.debug("Executing command: {} with args: {}", commandName, Arrays.toString(commandArgs));

        // Check for help on specific command
        if (commandArgs.length > 0 && (commandArgs[0].equals("--help") || commandArgs[0].equals("-h"))) {
            return showCommandHelp(commandName);
        }

        var command = registry.find(commandName);

        if (command.isEmpty()) {
            ctx.error("Comando desconocido: " + commandName);
            ctx.output().println("Usa 'hefesto --help' para ver los comandos disponibles.");
            return 1;
        }

        try {
            log.debug("Found command: {}", command.get().info().name());
            CommandResult result = command.get().execute(ctx, commandArgs);
            log.debug("Command result: {}", result.getClass().getSimpleName());

            return switch (result) {
                case CommandResult.Success success -> {
                    log.debug("Command succeeded: {}", success.message());
                    yield 0;
                }
                case CommandResult.Failure failure -> {
                    log.debug("Command failed: {}", failure.error());
                    ctx.error("Error: " + failure.error());
                    yield 1;
                }
                case CommandResult.Exit exit -> {
                    log.debug("Exit requested with code: {}", exit.code());
                    yield exit.code();
                }
                case CommandResult.Continue _ -> 0;
            };
        } catch (Exception e) {
            log.error("Error executing command: {}", commandName, e);
            ctx.error("Error: " + e.getMessage());
            return 1;
        }
    }

    private void showHelp() {
        var helpRenderer = new HelpRenderer(ctx);
        helpRenderer.renderGeneralHelp(registry);
    }

    private int showCommandHelp(String commandName) {
        var command = registry.find(commandName);

        if (command.isEmpty()) {
            ctx.error("Comando desconocido: " + commandName);
            return 1;
        }

        var helpRenderer = new HelpRenderer(ctx);
        helpRenderer.renderCommandHelp(command.get());
        return 0;
    }

    private void showVersion() {
        ctx.output().println("Hefesto v1.0.0-SNAPSHOT");
        ctx.output().println("Java " + System.getProperty("java.version"));
    }

    /**
     * Main entry point.
     */
    public static void main(String[] args) {
        var app = new HefestoApplication();
        int exitCode = app.run(args);
        System.exit(exitCode);
    }
}
