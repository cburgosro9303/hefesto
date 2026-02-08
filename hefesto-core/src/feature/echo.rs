use hefesto_domain::command::{CommandInfo, CommandResult, Documentation, ExampleDoc, OptionDoc};
use hefesto_domain::command_parser;
use hefesto_domain::command_parser::ParsedArgs;

use crate::command::Command;
use crate::context::ExecutionContext;

/// Checks if a flag is used as a boolean (value is "true") or consumed a value.
/// If consumed, recovers the value as text.
fn is_boolean_flag(
    parsed: &ParsedArgs,
    long: &str,
    short: &str,
    text_parts: &mut Vec<String>,
) -> bool {
    for name in [long, short] {
        if let Some(val) = parsed.get_flag(name) {
            if val == "true" {
                return true;
            }
            // Parser consumed next arg as value of this flag -- recover it as text
            text_parts.push(val.to_string());
            return true;
        }
    }
    false
}

/// Echo command - displays text with optional transformations.
pub struct EchoCommand {
    info: CommandInfo,
}

impl EchoCommand {
    pub fn new() -> Self {
        let info = CommandInfo::new("echo", "Muestra texto con transformaciones opcionales")
            .with_category("text")
            .with_aliases(vec!["e".to_string()])
            .with_documentation(
                Documentation::new("echo [opciones] <texto>")
                    .with_long_description(
                        "Muestra el texto proporcionado en la salida. \
                        Soporta transformaciones como mayusculas, minusculas y repeticion.",
                    )
                    .with_option(
                        OptionDoc::flag("uppercase", "Convierte a mayusculas").with_short("u"),
                    )
                    .with_option(
                        OptionDoc::flag("lowercase", "Convierte a minusculas").with_short("l"),
                    )
                    .with_option(
                        OptionDoc::with_value("repeat", "Numero de veces a repetir")
                            .with_short("r"),
                    )
                    .with_option(
                        OptionDoc::with_value("separator", "Separador entre repeticiones")
                            .with_short("s"),
                    )
                    .with_example(ExampleDoc::new("echo Hola Mundo", "Muestra 'Hola Mundo'"))
                    .with_example(ExampleDoc::new(
                        "echo -u 'hola mundo'",
                        "Muestra 'HOLA MUNDO'",
                    ))
                    .with_example(ExampleDoc::new(
                        "echo -r 3 -s '|' test",
                        "Muestra 'test|test|test'",
                    )),
            );

        Self { info }
    }
}

impl Default for EchoCommand {
    fn default() -> Self {
        Self::new()
    }
}

impl Command for EchoCommand {
    fn info(&self) -> &CommandInfo {
        &self.info
    }

    fn execute(&self, ctx: &ExecutionContext, args: &[String]) -> CommandResult {
        if args.is_empty() {
            ctx.output.println("");
            return CommandResult::success();
        }

        let parsed = command_parser::parse(args);

        // Collect text: positional args + values consumed by boolean flags
        let mut text_parts: Vec<String> = parsed.positional.clone();

        // Check boolean flags - if parser consumed the next arg as a value,
        // recover it as text
        let uppercase = is_boolean_flag(&parsed, "uppercase", "u", &mut text_parts);
        let lowercase = is_boolean_flag(&parsed, "lowercase", "l", &mut text_parts);

        let mut text = text_parts.join(" ");

        if text.is_empty() {
            ctx.output.println("");
            return CommandResult::success();
        }

        // Apply transformations
        if uppercase {
            text = text.to_uppercase();
        } else if lowercase {
            text = text.to_lowercase();
        }

        // Handle repetition
        let mut repeat = parsed.get_flag_as_int_or("repeat", 1);
        repeat = parsed.get_flag_as_int_or("r", repeat).max(1).min(100);

        let separator = if parsed.has_flag("s") {
            parsed.get_flag_or("s", " ")
        } else {
            parsed.get_flag_or("separator", " ")
        };

        let mut output = String::new();
        for i in 0..repeat {
            if i > 0 {
                output.push_str(&separator);
            }
            output.push_str(&text);
        }

        ctx.output.println(&output);
        CommandResult::success()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::port::TestInput;
    use crate::port::TestOutput;
    use std::sync::Arc;

    fn make_ctx() -> (ExecutionContext, Arc<TestOutput>) {
        let output = Arc::new(TestOutput::new());
        let input = Arc::new(TestInput::new(vec![]));
        let ctx = ExecutionContext::new(output.clone() as Arc<dyn crate::port::OutputPort>, input);
        (ctx, output)
    }

    #[test]
    fn test_echo_simple() {
        let cmd = EchoCommand::new();
        let (ctx, output) = make_ctx();
        let args: Vec<String> = vec!["Hello".into(), "World".into()];
        cmd.execute(&ctx, &args);
        assert!(output.contains("Hello World"));
    }

    #[test]
    fn test_echo_uppercase() {
        let cmd = EchoCommand::new();
        let (ctx, output) = make_ctx();
        let args: Vec<String> = vec!["-u".into(), "hello".into()];
        cmd.execute(&ctx, &args);
        assert!(output.contains("HELLO"));
    }

    #[test]
    fn test_echo_empty() {
        let cmd = EchoCommand::new();
        let (ctx, output) = make_ctx();
        cmd.execute(&ctx, &[]);
        assert!(output.contains(""));
    }
}
