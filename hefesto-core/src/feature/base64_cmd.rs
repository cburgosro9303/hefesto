use base64::engine::general_purpose::{STANDARD, URL_SAFE};
use base64::Engine;
use hefesto_domain::command::{CommandInfo, CommandResult, Documentation, ExampleDoc, OptionDoc};
use hefesto_domain::command_parser;
use hefesto_domain::command_parser::ParsedArgs;

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
            // Parser consumed next arg as value -- recover it as text
            text_parts.push(val.to_string());
            return true;
        }
    }
    false
}

/// Base64 command - encodes and decodes Base64 strings.
pub struct Base64Command {
    info: CommandInfo,
}

impl Base64Command {
    pub fn new() -> Self {
        let info = CommandInfo::new("base64", "Codifica/decodifica texto en Base64")
            .with_category("encoding")
            .with_aliases(vec!["b64".to_string()])
            .with_documentation(
                Documentation::new("base64 [opciones] <texto>")
                    .with_long_description(
                        "Codifica texto a Base64 o decodifica texto desde Base64. \
                        Por defecto codifica. Use --decode para decodificar.",
                    )
                    .with_option(
                        OptionDoc::flag("decode", "Decodifica en lugar de codificar")
                            .with_short("d"),
                    )
                    .with_option(OptionDoc::flag("url", "Usa codificacion URL-safe"))
                    .with_example(ExampleDoc::new(
                        "base64 'Hola Mundo'",
                        "Codifica 'Hola Mundo' a Base64",
                    ))
                    .with_example(ExampleDoc::new(
                        "base64 -d 'SG9sYSBNdW5kbw=='",
                        "Decodifica el texto Base64",
                    ))
                    .with_example(ExampleDoc::new(
                        "base64 --url 'test+data'",
                        "Codifica con caracteres URL-safe",
                    )),
            );

        Self { info }
    }
}

impl Default for Base64Command {
    fn default() -> Self {
        Self::new()
    }
}

impl Command for Base64Command {
    fn info(&self) -> &CommandInfo {
        &self.info
    }

    fn execute(&self, ctx: &ExecutionContext, args: &[String]) -> CommandResult {
        if args.is_empty() {
            return CommandResult::failure("Se requiere texto para codificar/decodificar");
        }

        let parsed = command_parser::parse(args);

        // Determine mode and recover text consumed by boolean flags
        let mut text_parts: Vec<String> = parsed.positional.clone();
        let decode = recover_boolean_flag(&parsed, "decode", "d", &mut text_parts);
        let url_safe = recover_boolean_flag(&parsed, "url", "", &mut text_parts);

        if text_parts.is_empty() {
            return CommandResult::failure("Se requiere texto para codificar/decodificar");
        }

        let text = text_parts.join(" ");

        if decode {
            let engine = if url_safe { &URL_SAFE } else { &STANDARD };
            match engine.decode(text.trim()) {
                Ok(decoded) => match String::from_utf8(decoded) {
                    Ok(result) => {
                        ctx.output.println(&result);
                        CommandResult::success()
                    }
                    Err(e) => CommandResult::failure(format!(
                        "Texto decodificado no es UTF-8 valido: {}",
                        e
                    )),
                },
                Err(e) => CommandResult::failure(format!("Texto Base64 invalido: {}", e)),
            }
        } else {
            let engine = if url_safe { &URL_SAFE } else { &STANDARD };
            let encoded = engine.encode(text.as_bytes());
            ctx.output.println(&encoded);
            CommandResult::success()
        }
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
    fn test_encode() {
        let cmd = Base64Command::new();
        let (ctx, output) = make_ctx();
        let args: Vec<String> = vec!["Hello World".into()];
        cmd.execute(&ctx, &args);
        assert!(output.contains("SGVsbG8gV29ybGQ="));
    }

    #[test]
    fn test_decode() {
        let cmd = Base64Command::new();
        let (ctx, output) = make_ctx();
        let args: Vec<String> = vec!["-d".into(), "SGVsbG8gV29ybGQ=".into()];
        cmd.execute(&ctx, &args);
        assert!(output.contains("Hello World"));
    }

    #[test]
    fn test_no_args() {
        let cmd = Base64Command::new();
        let (ctx, _) = make_ctx();
        let result = cmd.execute(&ctx, &[]);
        assert!(matches!(result, CommandResult::Failure { .. }));
    }
}
