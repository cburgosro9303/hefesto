use hefesto_domain::command::{CommandInfo, CommandResult, Documentation, ExampleDoc, OptionDoc};
use hefesto_domain::command_parser;
use hefesto_domain::command_parser::ParsedArgs;
use std::path::Path;

use crate::command::Command;
use crate::context::ExecutionContext;

/// Checks if a flag is used as a boolean. If the parser consumed the next arg
/// as the flag's value, recovers it into text_parts.
fn recover_boolean_flag(
    parsed: &ParsedArgs,
    long: &str,
    short: &str,
    text_parts: &mut Vec<String>,
) -> bool {
    for name in [long, short] {
        if name.is_empty() {
            continue;
        }
        if let Some(val) = parsed.get_flag(name) {
            if val == "true" {
                return true;
            }
            text_parts.push(val.to_string());
            return true;
        }
    }
    false
}

/// JSON command - formats and validates JSON.
pub struct JsonCommand {
    info: CommandInfo,
}

impl JsonCommand {
    pub fn new() -> Self {
        let info = CommandInfo::new("json", "Formatea y valida JSON")
            .with_category("encoding")
            .with_aliases(vec!["jq".to_string()])
            .with_documentation(
                Documentation::new("json [opciones] <json|archivo>")
                    .with_long_description(
                        "Formatea JSON para mejor legibilidad o valida su estructura. \
                        Puede recibir JSON directamente o un archivo.",
                    )
                    .with_option(
                        OptionDoc::flag("validate", "Solo valida sin formatear").with_short("v"),
                    )
                    .with_option(
                        OptionDoc::flag("compact", "Salida compacta (sin espacios)")
                            .with_short("c"),
                    )
                    .with_option(OptionDoc::flag("file", "Lee desde un archivo").with_short("f"))
                    .with_example(ExampleDoc::new(
                        "json '{\"name\":\"test\"}'",
                        "Formatea el JSON",
                    ))
                    .with_example(ExampleDoc::new(
                        "json -v '{\"invalid'",
                        "Valida el JSON (reporta error)",
                    ))
                    .with_example(ExampleDoc::new(
                        "json -f data.json",
                        "Formatea el contenido del archivo",
                    ))
                    .with_example(ExampleDoc::new(
                        "json -c '{\"a\": 1, \"b\": 2}'",
                        "Compacta el JSON",
                    )),
            );

        Self { info }
    }
}

impl Default for JsonCommand {
    fn default() -> Self {
        Self::new()
    }
}

impl Command for JsonCommand {
    fn info(&self) -> &CommandInfo {
        &self.info
    }

    fn execute(&self, ctx: &ExecutionContext, args: &[String]) -> CommandResult {
        if args.is_empty() {
            return CommandResult::failure("Se requiere JSON o un archivo");
        }

        let parsed = command_parser::parse(args);

        // Recover text consumed by boolean flags
        let mut text_parts: Vec<String> = parsed.positional.clone();
        let validate = recover_boolean_flag(&parsed, "validate", "v", &mut text_parts);
        let compact = recover_boolean_flag(&parsed, "compact", "c", &mut text_parts);
        let from_file = recover_boolean_flag(&parsed, "file", "f", &mut text_parts);

        if text_parts.is_empty() {
            return CommandResult::failure("Se requiere JSON o un archivo");
        }

        let input = text_parts.join(" ");

        let json_content = if from_file {
            let path = Path::new(&input);
            if !path.exists() {
                return CommandResult::failure(format!("Archivo no encontrado: {}", input));
            }
            match std::fs::read_to_string(path) {
                Ok(content) => content,
                Err(e) => return CommandResult::failure(format!("Error leyendo archivo: {}", e)),
            }
        } else {
            input
        };

        // Parse JSON
        let value: serde_json::Value = match serde_json::from_str(&json_content) {
            Ok(v) => v,
            Err(e) => {
                let message = format!("JSON invalido: {}", e);
                if validate {
                    ctx.output.print_error(&message);
                    return CommandResult::failure("Validacion fallida");
                }
                return CommandResult::failure(message);
            }
        };

        if validate {
            ctx.output.print_success("JSON valido");
            return CommandResult::success();
        }

        // Format output
        let output = if compact {
            serde_json::to_string(&value).unwrap_or_default()
        } else {
            serde_json::to_string_pretty(&value).unwrap_or_default()
        };

        ctx.output.println(&output);
        CommandResult::success()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::port::{TestInput, TestOutput};
    use std::sync::Arc;

    fn make_ctx() -> (ExecutionContext, Arc<TestOutput>) {
        let output = Arc::new(TestOutput::new());
        let input = Arc::new(TestInput::new(vec![]));
        let ctx = ExecutionContext::new(output.clone() as Arc<dyn crate::port::OutputPort>, input);
        (ctx, output)
    }

    #[test]
    fn test_format_json() {
        let cmd = JsonCommand::new();
        let (ctx, output) = make_ctx();
        let args: Vec<String> = vec![r#"{"name":"test","value":42}"#.into()];
        cmd.execute(&ctx, &args);
        let lines = output.lines();
        assert!(lines.iter().any(|l| l.contains("name")));
    }

    #[test]
    fn test_validate_valid() {
        let cmd = JsonCommand::new();
        let (ctx, output) = make_ctx();
        let args: Vec<String> = vec!["--validate".into(), r#"{"key":"value"}"#.into()];
        cmd.execute(&ctx, &args);
        assert!(output.contains("JSON valido"));
    }

    #[test]
    fn test_validate_invalid() {
        let cmd = JsonCommand::new();
        let (ctx, _) = make_ctx();
        let args: Vec<String> = vec!["--validate".into(), r#"{"invalid"#.into()];
        let result = cmd.execute(&ctx, &args);
        assert!(matches!(result, CommandResult::Failure { .. }));
    }

    #[test]
    fn test_compact() {
        let cmd = JsonCommand::new();
        let (ctx, output) = make_ctx();
        let args: Vec<String> = vec!["-c".into(), r#"{ "a": 1, "b": 2 }"#.into()];
        cmd.execute(&ctx, &args);
        let lines = output.lines();
        assert!(lines.iter().any(|l| l.contains(r#"{"a":1,"b":2}"#)));
    }
}
