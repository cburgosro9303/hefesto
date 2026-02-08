use hefesto_domain::command::CommandResult;
use hefesto_domain::command_parser;

use crate::command::CommandRegistry;
use crate::context::ExecutionContext;
use crate::help::HelpRenderer;

/// Interactive menu for the CLI.
pub struct InteractiveMenu;

impl InteractiveMenu {
    /// Runs the interactive menu loop.
    pub fn run(ctx: &ExecutionContext, registry: &CommandRegistry) {
        ctx.output.print_header("Hefesto - System Diagnostic Tool");
        ctx.output
            .println("Type 'help' for available commands, 'exit' to quit.");
        ctx.output.print_separator();

        while let Some(line) = ctx.input.read_line("hefesto> ") {
            let input = line.trim();
            if input.is_empty() {
                continue;
            }

            let tokens = command_parser::tokenize(input);
            if tokens.is_empty() {
                continue;
            }

            let command_name = &tokens[0];
            let args: Vec<String> = tokens[1..].to_vec();

            match command_name.to_lowercase().as_str() {
                "exit" | "quit" | "q" => {
                    ctx.output.println("Goodbye!");
                    break;
                }
                "help" | "?" => {
                    if let Some(cmd_name) = args.first() {
                        if let Some(cmd) = registry.find(cmd_name) {
                            HelpRenderer::render_command(ctx.output.as_ref(), cmd);
                        } else {
                            ctx.output
                                .print_error(&format!("Unknown command: {}", cmd_name));
                        }
                    } else {
                        HelpRenderer::render_all(ctx.output.as_ref(), registry.all());
                    }
                }
                "clear" | "cls" => {
                    // Clear screen using ANSI escape
                    ctx.output.print("\x1B[2J\x1B[H");
                }
                _ => {
                    if let Some(cmd) = registry.find(command_name) {
                        match cmd.execute(ctx, &args) {
                            CommandResult::Success(msg) => {
                                if !msg.is_empty() {
                                    ctx.output.print_success(&msg);
                                }
                            }
                            CommandResult::Failure { error, cause } => {
                                ctx.output.print_error(&format!("Error: {}", error));
                                if let Some(cause) = cause {
                                    ctx.output.print_error(&format!("Caused by: {}", cause));
                                }
                            }
                            CommandResult::Exit(code) => {
                                std::process::exit(code);
                            }
                            CommandResult::Continue => {}
                        }
                    } else {
                        ctx.output.print_error(&format!(
                            "Unknown command: '{}'. Type 'help' for available commands.",
                            command_name
                        ));
                    }
                }
            }
        }
    }
}
