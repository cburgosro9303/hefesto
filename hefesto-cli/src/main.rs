use std::sync::Arc;

use hefesto_domain::command::CommandResult;
use hefesto_domain::config::HefestoConfig;
use hefesto_platform::process_sampler::SysInfoSampler;

use hefesto_core::command::CommandRegistry;
use hefesto_core::context::ExecutionContext;
use hefesto_core::feature::base64_cmd::Base64Command;
use hefesto_core::feature::echo::EchoCommand;
use hefesto_core::feature::json_cmd::JsonCommand;
use hefesto_core::feature::portinfo::command::PortInfoCommand;
use hefesto_core::feature::procwatch::command::ProcWatchCommand;
use hefesto_core::help::HelpRenderer;
use hefesto_core::menu::InteractiveMenu;
use hefesto_core::port::console::{ConsoleInputAdapter, ConsoleOutputAdapter};
use hefesto_core::port::OutputPort;

const VERSION: &str = env!("CARGO_PKG_VERSION");

fn main() {
    // Initialize config from embedded YAML
    let yaml = include_str!("../../hefesto-core/resources/hefesto.yml");
    HefestoConfig::init_from_yaml(yaml);

    // Initialize tracing
    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::from_default_env()
                .add_directive(tracing::Level::WARN.into()),
        )
        .init();

    let args: Vec<String> = std::env::args().skip(1).collect();
    let exit_code = run(args);
    std::process::exit(exit_code);
}

fn run(args: Vec<String>) -> i32 {
    // Create I/O adapters
    let output: Arc<dyn OutputPort> = Arc::new(ConsoleOutputAdapter::new());
    let input = Arc::new(ConsoleInputAdapter::new());
    let ctx = ExecutionContext::new(output.clone(), input);

    // Create platform services
    let process_sampler: Arc<dyn hefesto_platform::process_sampler::ProcessSampler> =
        Arc::new(SysInfoSampler::new());

    // Register commands
    let mut registry = CommandRegistry::new();
    registry.register(Box::new(EchoCommand::new()));
    registry.register(Box::new(Base64Command::new()));
    registry.register(Box::new(JsonCommand::new()));
    registry.register(Box::new(PortInfoCommand::new()));
    registry.register(Box::new(ProcWatchCommand::new(process_sampler)));

    // Process global flags
    let mut remaining_args = Vec::new();
    let mut verbose = false;

    for arg in &args {
        match arg.as_str() {
            "--verbose" | "-V" => {
                verbose = true;
            }
            _ => remaining_args.push(arg.clone()),
        }
    }

    if verbose {
        tracing::info!("Verbose mode enabled");
    }

    if remaining_args.is_empty() {
        // Interactive mode
        InteractiveMenu::run(&ctx, &registry);
        return 0;
    }

    let first_arg = &remaining_args[0];

    // Check for help flag
    if first_arg == "--help" || first_arg == "-h" {
        HelpRenderer::render_all(ctx.output.as_ref(), registry.all());
        return 0;
    }

    if first_arg == "--version" {
        ctx.output.println(&format!("Hefesto v{}", VERSION));
        let info = hefesto_platform::system_info::get_system_info();
        ctx.output
            .println(&format!("{} {}", info.os, info.os_version));
        return 0;
    }

    // CLI mode
    let command_name = &remaining_args[0];
    let command_args: Vec<String> = remaining_args[1..].to_vec();

    // Check for help on specific command
    if !command_args.is_empty() && (command_args[0] == "--help" || command_args[0] == "-h") {
        if let Some(cmd) = registry.find(command_name) {
            HelpRenderer::render_command(ctx.output.as_ref(), cmd);
            return 0;
        } else {
            ctx.output
                .print_error(&format!("Comando desconocido: {}", command_name));
            return 1;
        }
    }

    match registry.find(command_name) {
        Some(cmd) => {
            let result = cmd.execute(&ctx, &command_args);
            match result {
                CommandResult::Success(msg) => {
                    if !msg.is_empty() {
                        tracing::debug!("Command succeeded: {}", msg);
                    }
                    0
                }
                CommandResult::Failure { error, cause } => {
                    ctx.output.print_error(&format!("Error: {}", error));
                    if let Some(cause) = cause {
                        ctx.output.print_error(&format!("Caused by: {}", cause));
                    }
                    1
                }
                CommandResult::Exit(code) => code,
                CommandResult::Continue => 0,
            }
        }
        None => {
            ctx.output
                .print_error(&format!("Comando desconocido: {}", command_name));
            ctx.output
                .println("Usa 'hefesto --help' para ver los comandos disponibles.");
            1
        }
    }
}
