use crate::command::Command;
use crate::port::OutputPort;

/// Renders help information for commands.
pub struct HelpRenderer;

impl HelpRenderer {
    /// Renders help for all commands.
    pub fn render_all(output: &dyn OutputPort, commands: &[Box<dyn Command>]) {
        output.print_header("Hefesto - System Diagnostic Tool");
        output.println_empty();
        output.println("Available commands:");
        output.print_separator();

        // Group by category
        let mut categories: std::collections::HashMap<&str, Vec<&dyn Command>> =
            std::collections::HashMap::new();

        for cmd in commands {
            categories
                .entry(cmd.info().category.as_str())
                .or_default()
                .push(cmd.as_ref());
        }

        let mut sorted_cats: Vec<(&str, Vec<&dyn Command>)> = categories.into_iter().collect();
        sorted_cats.sort_by_key(|(k, _)| *k);

        for (category, cmds) in &sorted_cats {
            output.println_empty();
            output.print_header(&format!("  {}", category.to_uppercase()));

            for cmd in cmds {
                let info = cmd.info();
                let aliases = if info.aliases.is_empty() {
                    String::new()
                } else {
                    format!(" ({})", info.aliases.join(", "))
                };
                output.println(&format!(
                    "    {:<20} {}{}",
                    info.name, info.description, aliases
                ));
            }
        }

        output.println_empty();
        output.println("Use '<command> --help' for detailed information about a command.");
        output.println("Use 'exit' or 'quit' to exit the application.");
    }

    /// Renders detailed help for a specific command.
    pub fn render_command(output: &dyn OutputPort, command: &dyn Command) {
        let info = command.info();

        output.print_header(&format!("{} - {}", info.name, info.description));
        output.println_empty();

        if let Some(ref docs) = info.documentation {
            if !docs.usage.is_empty() {
                output.println(&format!("Usage: {}", docs.usage));
                output.println_empty();
            }

            if !docs.long_description.is_empty() {
                output.println(&docs.long_description);
                output.println_empty();
            }

            if !docs.options.is_empty() {
                output.println("Options:");
                for opt in &docs.options {
                    let short = opt
                        .short
                        .as_ref()
                        .map(|s| format!("-{}, ", s))
                        .unwrap_or_default();
                    let value_hint = if opt.has_value { " <value>" } else { "" };
                    output.println(&format!(
                        "  {}--{}{:<20} {}",
                        short, opt.name, value_hint, opt.description
                    ));
                }
                output.println_empty();
            }

            if !docs.examples.is_empty() {
                output.println("Examples:");
                for example in &docs.examples {
                    output.println(&format!("  {}  # {}", example.command, example.description));
                }
            }
        }

        if !info.aliases.is_empty() {
            output.println_empty();
            output.println(&format!("Aliases: {}", info.aliases.join(", ")));
        }
    }
}
