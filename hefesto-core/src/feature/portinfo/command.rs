use std::sync::Arc;
use std::thread;
use std::time::Duration;

use chrono::Local;
use crossterm::style::{Color, Stylize};
use regex::Regex;

use hefesto_domain::command::{
    CommandInfo, CommandResult, Documentation, ExampleDoc, OptionDoc,
};
use hefesto_domain::command_parser;
use hefesto_domain::portinfo::enriched_port_binding::EnrichedPortBinding;
use hefesto_domain::portinfo::port_binding::PortBinding;

use crate::command::Command;
use crate::context::ExecutionContext;

use super::output::csv_formatter::CsvFormatter;
use super::output::json_formatter::JsonFormatter;
use super::output::table_formatter::TableFormatter;
use super::output::OutputFormatter;
use super::service::docker_service::DockerService;
use super::service::health_check_service::HealthCheckService;
use super::service::port_info_service::PortInfoService;
use super::service::process_enrichment_service::ProcessEnrichmentService;
use super::service::security_analysis_service::SecurityAnalysisService;
use super::service::service_registry::ServiceRegistry;

use hefesto_platform::port_parser;

/// Command for querying port information and managing processes.
///
/// Enhanced with service identification, health checks, security analysis,
/// Docker integration, and multiple output formats. Supports 30+ flags
/// covering discovery, analysis, monitoring, and output control.
pub struct PortInfoCommand {
    info: CommandInfo,
    service: PortInfoService,
    #[allow(dead_code)]
    service_registry: ServiceRegistry,
    health_check_service: HealthCheckService,
    security_service: SecurityAnalysisService,
    docker_service: DockerService,
    process_service: ProcessEnrichmentService,
}

impl PortInfoCommand {
    /// Creates a new `PortInfoCommand` with platform-specific parser.
    pub fn new() -> Self {
        let parser: Arc<dyn hefesto_platform::port_parser::PortParser> =
            Arc::from(port_parser::create_parser());
        Self::with_parser(parser)
    }

    /// Creates a new `PortInfoCommand` with a custom parser (for testing).
    pub fn with_parser(parser: Arc<dyn hefesto_platform::port_parser::PortParser>) -> Self {
        let service_registry = ServiceRegistry::new();
        let security_service = SecurityAnalysisService::with_registry(ServiceRegistry::new());

        let info = CommandInfo::new("portinfo", "Muestra que proceso esta usando un puerto")
            .with_category("network".to_string())
            .with_aliases(vec!["port-who".to_string(), "pw".to_string()])
            .with_documentation(Self::build_documentation());

        Self {
            info,
            service: PortInfoService::new(parser),
            service_registry,
            health_check_service: HealthCheckService::new(),
            security_service,
            docker_service: DockerService::new(),
            process_service: ProcessEnrichmentService::new(),
        }
    }

    fn build_documentation() -> Documentation {
        Documentation::new("portinfo [opciones] <puerto>")
            .with_long_description(
                "Herramienta completa de diagnostico de red para DevOps y desarrolladores. \
                Identifica procesos en puertos, realiza health checks, analiza seguridad, \
                detecta contenedores Docker, y mas. Soporta Linux, macOS y Windows.",
            )
            // Basic options
            .with_option(
                OptionDoc::flag("kill", "Termina el proceso que usa el puerto (pide confirmacion)")
                    .with_short("k"),
            )
            .with_option(
                OptionDoc::flag("force", "Termina sin confirmacion (requiere --kill)")
                    .with_short("f"),
            )
            .with_option(OptionDoc::flag("udp", "Buscar en UDP ademas de TCP"))
            // Output format options
            .with_option(
                OptionDoc::flag("json", "Salida en formato JSON enriquecido").with_short("j"),
            )
            .with_option(OptionDoc::flag("table", "Salida en formato tabla ASCII"))
            .with_option(OptionDoc::flag(
                "csv",
                "Salida en formato CSV (para exportar)",
            ))
            // Discovery options
            .with_option(
                OptionDoc::flag("all", "Lista todos los puertos en estado LISTEN").with_short("a"),
            )
            .with_option(OptionDoc::flag(
                "overview",
                "Vista completa de red con estadisticas",
            ))
            .with_option(OptionDoc::with_value(
                "range",
                "Buscar en un rango de puertos (ej: 8000-8100)",
            ))
            .with_option(OptionDoc::flag(
                "listen",
                "Solo mostrar puertos en estado LISTEN",
            ))
            .with_option(
                OptionDoc::with_value("pid", "Buscar puertos por PID de proceso").with_short("p"),
            )
            .with_option(
                OptionDoc::with_value("name", "Filtrar por nombre de proceso").with_short("n"),
            )
            // Health check options
            .with_option(
                OptionDoc::flag("check", "Realizar health check TCP").with_short("c"),
            )
            .with_option(OptionDoc::flag(
                "http",
                "Health check HTTP (requiere --check)",
            ))
            .with_option(OptionDoc::flag("ssl", "Obtener info de certificado SSL"))
            // Developer options
            .with_option(OptionDoc::flag(
                "dev",
                "Mostrar puertos comunes de desarrollo",
            ))
            .with_option(OptionDoc::with_value(
                "free",
                "Verificar si puerto esta libre y sugerir alternativas",
            ))
            // Analysis options
            .with_option(
                OptionDoc::flag("process", "Mostrar info extendida del proceso").with_short("P"),
            )
            .with_option(
                OptionDoc::flag("docker", "Detectar y mostrar info de contenedores Docker")
                    .with_short("d"),
            )
            .with_option(
                OptionDoc::flag("security", "Analisis de seguridad de puertos").with_short("s"),
            )
            // Monitoring
            .with_option(
                OptionDoc::with_value("watch", "Monitorear el puerto (ej: 2s, 5m)").with_short("w"),
            )
            // Health check host/path
            .with_option(OptionDoc::with_value(
                "host",
                "Host para health check (default: 127.0.0.1)",
            ))
            .with_option(OptionDoc::with_value(
                "path",
                "Path para health check HTTP (default: /)",
            ))
            // Examples
            .with_example(ExampleDoc::new(
                "portinfo 8080",
                "Muestra quien usa el puerto 8080",
            ))
            .with_example(ExampleDoc::new(
                "portinfo --all",
                "Lista todos los puertos LISTEN",
            ))
            .with_example(ExampleDoc::new(
                "portinfo --overview",
                "Vista completa con estadisticas",
            ))
            .with_example(ExampleDoc::new(
                "portinfo 8080 --check",
                "Health check TCP del puerto",
            ))
            .with_example(ExampleDoc::new(
                "portinfo 8080 --check --http",
                "Health check HTTP",
            ))
            .with_example(ExampleDoc::new(
                "portinfo 443 --ssl",
                "Info del certificado SSL",
            ))
            .with_example(ExampleDoc::new(
                "portinfo --security",
                "Analisis de seguridad completo",
            ))
            .with_example(ExampleDoc::new(
                "portinfo --docker",
                "Solo puertos de Docker",
            ))
            .with_example(ExampleDoc::new(
                "portinfo --dev",
                "Puertos de desarrollo en uso",
            ))
            .with_example(ExampleDoc::new(
                "portinfo --free 8080",
                "Verificar disponibilidad",
            ))
            .with_example(ExampleDoc::new(
                "portinfo --name java",
                "Puertos del proceso java",
            ))
            .with_example(ExampleDoc::new(
                "portinfo --all --table",
                "Tabla ASCII formateada",
            ))
            .with_example(ExampleDoc::new(
                "portinfo --all --csv",
                "Export a CSV",
            ))
            .with_example(ExampleDoc::new(
                "portinfo 8080 --kill",
                "Termina el proceso en el puerto",
            ))
            .with_example(ExampleDoc::new(
                "portinfo --range 8000-8100",
                "Rango de puertos",
            ))
            .with_example(ExampleDoc::new(
                "portinfo --pid 1234",
                "Puertos del proceso 1234",
            ))
            .with_example(ExampleDoc::new(
                "portinfo 8080 --watch 2s",
                "Monitorea cada 2 segundos",
            ))
    }

    fn determine_formatter(
        &self,
        parsed: &command_parser::ParsedArgs,
    ) -> Box<dyn OutputFormatter> {
        if parsed.get_boolean("json") || parsed.get_boolean("j") {
            Box::new(JsonFormatter::new())
        } else if parsed.get_boolean("csv") {
            Box::new(CsvFormatter::new())
        } else if parsed.get_boolean("table") {
            Box::new(TableFormatter::new(true))
        } else {
            Box::new(TableFormatter::new(true))
        }
    }

    fn is_json_formatter(parsed: &command_parser::ParsedArgs) -> bool {
        parsed.get_boolean("json") || parsed.get_boolean("j")
    }

    // ── Mode handlers ──────────────────────────────────────────────────────

    fn handle_all(
        &self,
        ctx: &ExecutionContext,
        formatter: &dyn OutputFormatter,
        _listen_only: bool,
    ) -> CommandResult {
        let bindings = self.service.find_all_listening();

        if bindings.is_empty() {
            ctx.output.print_warning("No hay puertos en estado LISTEN");
            return CommandResult::success();
        }

        let enriched = self.service.enrich_bindings(&bindings);
        let output = formatter.format(&enriched);
        ctx.output.println(&output);

        CommandResult::success()
    }

    fn handle_overview(
        &self,
        ctx: &ExecutionContext,
        formatter: &dyn OutputFormatter,
    ) -> CommandResult {
        let overview = self.service.get_network_overview();
        let output = formatter.format_overview(&overview);
        ctx.output.println(&output);
        CommandResult::success()
    }

    fn handle_security(
        &self,
        ctx: &ExecutionContext,
        parsed: &command_parser::ParsedArgs,
        formatter: &dyn OutputFormatter,
    ) -> CommandResult {
        let bindings = if parsed.has_positional() {
            match parsed.positional(0).and_then(|s| s.parse::<u16>().ok()) {
                Some(port) => self.service.find_by_port_with_protocol(port, true, true),
                None => {
                    return CommandResult::failure(format!(
                        "Puerto invalido: {}",
                        parsed.positional(0).unwrap_or("")
                    ));
                }
            }
        } else {
            self.service.find_all_listening()
        };

        if bindings.is_empty() {
            ctx.output.print_warning("No hay puertos para analizar");
            return CommandResult::success();
        }

        let report = self.security_service.analyze(&bindings);
        let output = formatter.format_security_report(&report);
        ctx.output.println(&output);

        CommandResult::success()
    }

    fn handle_docker(&self, ctx: &ExecutionContext) -> CommandResult {
        if !self.docker_service.is_docker_available() {
            return CommandResult::failure(
                "Docker no esta disponible o no se puede conectar",
            );
        }

        let containers = self.docker_service.list_running_containers();

        if containers.is_empty() {
            ctx.output
                .print_warning("No hay contenedores Docker en ejecucion");
            return CommandResult::success();
        }

        ctx.output.print_header("DOCKER CONTAINERS");
        ctx.output.println_empty();

        for container in &containers {
            let name_line = format!(
                "{} ({}) - {}",
                container.container_name,
                container.short_id(),
                container.image
            );
            ctx.output.print_success(&name_line);
            ctx.output
                .println(&format!("  Status: {}", container.status));

            if !container.port_mappings.is_empty() {
                ctx.output.println(&format!(
                    "  Ports: {}",
                    container.port_mappings_formatted()
                ));
            }
            ctx.output.println_empty();
        }

        CommandResult::success()
    }

    fn handle_dev_ports(
        &self,
        ctx: &ExecutionContext,
        formatter: &dyn OutputFormatter,
    ) -> CommandResult {
        let dev_bindings = self.service.find_dev_ports();

        if dev_bindings.is_empty() {
            ctx.output
                .print_warning("No hay puertos de desarrollo en uso");
            return CommandResult::success();
        }

        ctx.output
            .print_header("PUERTOS DE DESARROLLO EN USO");
        ctx.output.println_empty();

        let enriched = self.service.enrich_bindings(&dev_bindings);
        let output = formatter.format(&enriched);
        ctx.output.println(&output);

        CommandResult::success()
    }

    fn handle_free_check(
        &self,
        ctx: &ExecutionContext,
        parsed: &command_parser::ParsedArgs,
    ) -> CommandResult {
        let port_str = parsed
            .get_flag("free")
            .filter(|s| *s != "true")
            .or_else(|| parsed.positional(0))
            .unwrap_or("");

        if port_str.is_empty() {
            return CommandResult::failure("Se requiere un puerto para verificar");
        }

        let port: u16 = match port_str.parse() {
            Ok(p) => p,
            Err(_) => return CommandResult::failure(format!("Puerto invalido: {port_str}")),
        };

        let is_free = self.service.is_port_free(port);

        if is_free {
            ctx.output
                .print_success(&format!("Puerto {port} esta LIBRE y disponible"));
        } else {
            ctx.output
                .print_warning(&format!("Puerto {port} esta EN USO"));

            // Show what's using it
            let bindings = self.service.find_by_port(port);
            for b in &bindings {
                ctx.output.println(&format!(
                    "  -> {} (pid {})",
                    b.process_name, b.pid
                ));
            }

            // Suggest alternatives
            let alternatives = self.service.find_alternatives(port, 5);
            if !alternatives.is_empty() {
                ctx.output.println_empty();
                ctx.output.print_warning("Alternativas disponibles:");
                for alt in &alternatives {
                    ctx.output.println(&format!("  {alt}"));
                }
            }
        }

        CommandResult::success()
    }

    fn handle_by_name(
        &self,
        ctx: &ExecutionContext,
        parsed: &command_parser::ParsedArgs,
        formatter: &dyn OutputFormatter,
    ) -> CommandResult {
        let name = parsed
            .get_flag("name")
            .filter(|s| *s != "true")
            .or_else(|| parsed.get_flag("n").filter(|s| *s != "true"))
            .or_else(|| parsed.positional(0))
            .unwrap_or("");

        if name.is_empty() {
            return CommandResult::failure("Se requiere un nombre de proceso");
        }

        let bindings = self.service.find_by_process_name(name);

        if bindings.is_empty() {
            ctx.output.print_warning(&format!(
                "No se encontraron puertos para el proceso: {name}"
            ));
            return CommandResult::success();
        }

        ctx.output
            .print_header(&format!("Puertos del proceso '{name}':"));
        ctx.output.println_empty();

        let enriched = self.service.enrich_bindings(&bindings);
        let output = formatter.format(&enriched);
        ctx.output.println(&output);

        CommandResult::success()
    }

    fn handle_health_check(
        &self,
        ctx: &ExecutionContext,
        port: u16,
        parsed: &command_parser::ParsedArgs,
        formatter: &dyn OutputFormatter,
    ) -> CommandResult {
        let http_check = parsed.get_boolean("http");
        let host = parsed.get_flag_or("host", "127.0.0.1");

        let result = if http_check {
            let path = parsed.get_flag_or("path", "/");
            self.health_check_service.check_http(&host, port, &path, false)
        } else {
            self.health_check_service.check_tcp(&host, port)
        };

        let output = formatter.format_health_check(&result);
        ctx.output.println(&output);

        if result.is_healthy() {
            CommandResult::success()
        } else {
            CommandResult::failure("Health check failed")
        }
    }

    fn handle_ssl_check(
        &self,
        ctx: &ExecutionContext,
        port: u16,
        formatter: &dyn OutputFormatter,
    ) -> CommandResult {
        let result = self.health_check_service.check_ssl("127.0.0.1", port);
        let output = formatter.format_health_check(&result);
        ctx.output.println(&output);

        if result.is_healthy() {
            CommandResult::success()
        } else {
            CommandResult::failure("SSL check failed")
        }
    }

    fn handle_single_port(
        &self,
        ctx: &ExecutionContext,
        port: u16,
        udp: bool,
        formatter: &dyn OutputFormatter,
        kill: bool,
        force: bool,
        show_process: bool,
        show_docker: bool,
        is_json: bool,
    ) -> CommandResult {
        let bindings = self.service.find_by_port_with_protocol(port, true, udp);

        if bindings.is_empty() {
            if is_json {
                ctx.output.println("[]");
            } else {
                ctx.output
                    .print_warning(&format!("Puerto {port} esta libre"));
            }
            return CommandResult::success();
        }

        // Enrich bindings
        let mut enriched = self.service.enrich_bindings(&bindings);

        // Add process info if requested
        if show_process {
            enriched = enriched
                .into_iter()
                .map(|eb| {
                    if let Some(proc_info) = self.process_service.get_process_info(eb.pid()) {
                        eb.set_process_info(proc_info)
                    } else {
                        eb
                    }
                })
                .collect();
        }

        // Add Docker info if requested
        if show_docker && self.docker_service.is_docker_available() {
            enriched = enriched
                .into_iter()
                .map(|eb| {
                    if let Some(docker_info) = self.docker_service.get_container_by_pid(eb.pid()) {
                        eb.set_docker_info(docker_info)
                    } else {
                        eb
                    }
                })
                .collect();
        }

        // Output
        if is_json {
            ctx.output.println(&formatter.format(&enriched));
        } else {
            for eb in &enriched {
                self.print_enriched_binding(ctx, eb, show_process, show_docker);
            }
        }

        // Handle kill request
        if kill && !bindings.is_empty() {
            return self.handle_kill(ctx, &bindings, force);
        }

        CommandResult::success()
    }

    fn print_enriched_binding(
        &self,
        ctx: &ExecutionContext,
        eb: &EnrichedPortBinding,
        show_process: bool,
        show_docker: bool,
    ) {
        // Basic info line
        let proto = eb.protocol().as_str().with(Color::Cyan).to_string();
        let port_str = eb.port().to_string().bold().to_string();
        let state = format_state_colored(eb.state().as_str());
        let process = eb.process_name().with(Color::Green).to_string();

        let mut line = format!(
            "{} {}:{} {} pid={} {}",
            proto,
            eb.local_address(),
            port_str,
            state,
            eb.pid(),
            process
        );

        // Service tag
        if let Some(ref si) = eb.service_info {
            let tag = si.to_tag().with(Color::Magenta).to_string();
            line.push(' ');
            line.push_str(&tag);
        }

        ctx.output.println(&line);

        // Extended process info
        if show_process {
            if let Some(ref proc_info) = eb.process_info {
                ctx.output.println(&format!(
                    "  Memory: {} RSS, {} Virtual",
                    proc_info.memory_rss_formatted(),
                    proc_info.memory_virtual_formatted()
                ));
                ctx.output.println(&format!(
                    "  CPU Time: {}, Threads: {}",
                    proc_info.cpu_time_formatted(),
                    proc_info.thread_count
                ));
                if !proc_info.working_directory.is_empty() {
                    ctx.output
                        .println(&format!("  CWD: {}", proc_info.working_directory));
                }
            }
        }

        // Docker info
        if show_docker {
            if let Some(ref docker) = eb.docker_info {
                ctx.output.println(&format!(
                    "  Docker: {} ({})",
                    docker.container_name,
                    docker.short_id()
                ));
                ctx.output
                    .println(&format!("  Image: {}", docker.image));
            }
        }
    }

    fn handle_range(
        &self,
        ctx: &ExecutionContext,
        parsed: &command_parser::ParsedArgs,
        formatter: &dyn OutputFormatter,
        listen_only: bool,
    ) -> CommandResult {
        let range_str = parsed.get_flag_or("range", "");
        let re = Regex::new(r"^(\d+)-(\d+)$").unwrap();

        let captures = match re.captures(&range_str) {
            Some(c) => c,
            None => {
                return CommandResult::failure(
                    "Rango invalido. Usa el formato: 8000-8100",
                );
            }
        };

        let from: u16 = match captures[1].parse() {
            Ok(v) => v,
            Err(_) => return CommandResult::failure("Rango invalido"),
        };
        let to: u16 = match captures[2].parse() {
            Ok(v) => v,
            Err(_) => return CommandResult::failure("Rango invalido"),
        };

        if from > to || from < 1 {
            return CommandResult::failure(format!("Rango invalido: {from}-{to}"));
        }

        let bindings = self.service.find_in_range(from, to, listen_only);

        if bindings.is_empty() {
            ctx.output.print_warning(&format!(
                "No hay puertos ocupados en el rango {from}-{to}"
            ));
            return CommandResult::success();
        }

        ctx.output
            .print_header(&format!("Puertos en rango {from}-{to}:"));
        ctx.output.println_empty();

        let enriched = self.service.enrich_bindings(&bindings);
        let output = formatter.format(&enriched);
        ctx.output.println(&output);

        CommandResult::success()
    }

    fn handle_pid(
        &self,
        ctx: &ExecutionContext,
        parsed: &command_parser::ParsedArgs,
        formatter: &dyn OutputFormatter,
    ) -> CommandResult {
        let pid_str = parsed
            .get_flag("pid")
            .filter(|s| *s != "true")
            .or_else(|| parsed.get_flag("p").filter(|s| *s != "true"))
            .or_else(|| parsed.positional(0))
            .unwrap_or("");

        if pid_str.is_empty() {
            return CommandResult::failure("Se requiere un PID");
        }

        let pid: u32 = match pid_str.parse() {
            Ok(p) => p,
            Err(_) => {
                return CommandResult::failure(format!("PID invalido: {pid_str}"));
            }
        };

        let bindings = self.service.find_by_pid(pid);

        if bindings.is_empty() {
            ctx.output
                .print_warning(&format!("No se encontraron puertos para el PID {pid}"));
            return CommandResult::success();
        }

        ctx.output
            .print_header(&format!("Puertos del proceso {pid}:"));
        ctx.output.println_empty();

        let enriched = self.service.enrich_bindings(&bindings);
        let output = formatter.format(&enriched);
        ctx.output.println(&output);

        CommandResult::success()
    }

    fn handle_watch(
        &self,
        ctx: &ExecutionContext,
        parsed: &command_parser::ParsedArgs,
        udp: bool,
    ) -> CommandResult {
        let watch_str = parsed
            .get_flag("watch")
            .or_else(|| parsed.get_flag("w"))
            .unwrap_or("2s");
        let interval_ms = parse_watch_interval(watch_str);

        if !parsed.has_positional() {
            return CommandResult::failure("Se requiere un puerto para monitorear");
        }

        let port: u16 = match parsed.positional(0).and_then(|s| s.parse().ok()) {
            Some(p) => p,
            None => return CommandResult::failure("Puerto invalido"),
        };

        ctx.output.print_warning(&format!(
            "Monitoreando puerto {port} cada {watch_str} (Ctrl+C para detener)"
        ));
        ctx.output.println_empty();

        loop {
            let bindings = self.service.find_by_port_with_protocol(port, true, udp);
            let timestamp = Local::now().format("%Y-%m-%d %H:%M:%S").to_string();
            let prefix = format!("[{timestamp}] ");

            if bindings.is_empty() {
                let dim_text = format!("{prefix}(puerto libre)");
                ctx.output.println(&dim_text);
            } else {
                for b in &bindings {
                    let eb = self.service.enrich_binding(b);
                    ctx.output
                        .println(&format!("{prefix}{}", eb.to_compact_with_service()));
                }
            }
            ctx.output.flush();

            thread::sleep(Duration::from_millis(interval_ms));
        }
    }

    fn handle_kill(
        &self,
        ctx: &ExecutionContext,
        bindings: &[PortBinding],
        force: bool,
    ) -> CommandResult {
        // Get unique PIDs
        let mut pids: Vec<u32> = bindings
            .iter()
            .map(|b| b.pid)
            .filter(|&pid| pid > 0)
            .collect();
        pids.sort();
        pids.dedup();

        if pids.is_empty() {
            return CommandResult::failure("No se pudo determinar el PID del proceso");
        }

        for pid in &pids {
            let process_name = bindings
                .iter()
                .find(|b| b.pid == *pid)
                .map(|b| b.process_name.as_str())
                .unwrap_or("desconocido");

            if !force {
                ctx.output.println_empty();
                let prompt = format!(
                    "Terminar proceso {process_name} (PID {pid})? [s/N]: "
                );

                match ctx.input.read_line(&prompt) {
                    Some(answer) if answer.to_lowercase().starts_with('s') => {}
                    _ => {
                        ctx.output.print_warning("Operacion cancelada");
                        continue;
                    }
                }
            }

            if self.service.kill_process(*pid, force) {
                ctx.output
                    .print_success(&format!("Proceso {pid} terminado"));
            } else {
                ctx.output.print_error(&format!(
                    "No se pudo terminar el proceso {pid}. Puede requerir permisos elevados."
                ));
            }
        }

        CommandResult::success()
    }
}

impl Default for PortInfoCommand {
    fn default() -> Self {
        Self::new()
    }
}

impl Command for PortInfoCommand {
    fn info(&self) -> &CommandInfo {
        &self.info
    }

    fn execute(&self, ctx: &ExecutionContext, args: &[String]) -> CommandResult {
        let parsed = command_parser::parse(args);

        // Determine output format
        let formatter = self.determine_formatter(&parsed);
        let is_json = Self::is_json_formatter(&parsed);

        // Basic flags
        let kill = parsed.get_boolean("kill") || parsed.get_boolean("k");
        let force = parsed.get_boolean("force") || parsed.get_boolean("f");
        let udp = parsed.get_boolean("udp");
        let listen_only = parsed.get_boolean("listen");

        // Mode: All listening ports
        if parsed.get_boolean("all") || parsed.get_boolean("a") {
            return self.handle_all(ctx, formatter.as_ref(), listen_only);
        }

        // Mode: Network overview
        if parsed.get_boolean("overview") {
            return self.handle_overview(ctx, formatter.as_ref());
        }

        // Mode: Security analysis
        if parsed.get_boolean("security") || parsed.get_boolean("s") {
            return self.handle_security(ctx, &parsed, formatter.as_ref());
        }

        // Mode: Docker ports
        if parsed.get_boolean("docker") || parsed.get_boolean("d") {
            return self.handle_docker(ctx);
        }

        // Mode: Development ports
        if parsed.get_boolean("dev") {
            return self.handle_dev_ports(ctx, formatter.as_ref());
        }

        // Mode: Free port check
        if parsed.has_flag("free") {
            return self.handle_free_check(ctx, &parsed);
        }

        // Mode: Filter by process name
        if parsed.has_flag("name") || parsed.has_flag("n") {
            return self.handle_by_name(ctx, &parsed, formatter.as_ref());
        }

        // Mode: Range
        if parsed.has_flag("range") {
            return self.handle_range(ctx, &parsed, formatter.as_ref(), listen_only);
        }

        // Mode: PID
        if parsed.has_flag("pid") || parsed.has_flag("p") {
            return self.handle_pid(ctx, &parsed, formatter.as_ref());
        }

        // Mode: Watch
        if parsed.has_flag("watch") || parsed.has_flag("w") {
            return self.handle_watch(ctx, &parsed, udp);
        }

        // Mode: Single port
        if !parsed.has_positional() {
            return CommandResult::failure(
                "Se requiere un numero de puerto. Usa --help para ver opciones.",
            );
        }

        let port_str = parsed.positional(0).unwrap_or("");
        let port: u16 = match port_str.parse() {
            Ok(p) if p >= 1 => p,
            Ok(p) => {
                return CommandResult::failure(format!(
                    "Puerto fuera de rango (1-65535): {p}"
                ));
            }
            Err(_) => {
                return CommandResult::failure(format!("Puerto invalido: {port_str}"));
            }
        };

        // Mode: Health check
        if parsed.get_boolean("check") || parsed.get_boolean("c") {
            return self.handle_health_check(ctx, port, &parsed, formatter.as_ref());
        }

        // Mode: SSL check
        if parsed.get_boolean("ssl") {
            return self.handle_ssl_check(ctx, port, formatter.as_ref());
        }

        // Mode: Single port with optional process info
        let show_process = parsed.get_boolean("process") || parsed.get_boolean("P");
        let show_docker = parsed.get_boolean("docker") || parsed.get_boolean("d");

        self.handle_single_port(
            ctx,
            port,
            udp,
            formatter.as_ref(),
            kill,
            force,
            show_process,
            show_docker,
            is_json,
        )
    }
}

/// Formats a connection state string with color.
fn format_state_colored(state: &str) -> String {
    match state {
        "LISTEN" => state.with(Color::Green).to_string(),
        "ESTABLISHED" | "ESTAB" => state.with(Color::Blue).to_string(),
        "TIME_WAIT" | "CLOSE_WAIT" => state.with(Color::Yellow).to_string(),
        _ => state.to_string(),
    }
}

/// Parses a watch interval string (e.g., "2s", "5m", "1h") into milliseconds.
fn parse_watch_interval(s: &str) -> u64 {
    let re = Regex::new(r"^(\d+)([smh]?)$").unwrap();
    match re.captures(s) {
        Some(caps) => {
            let value: u64 = caps[1].parse().unwrap_or(2);
            let unit = caps.get(2).map(|m| m.as_str()).unwrap_or("s");
            match unit {
                "m" => value * 60 * 1000,
                "h" => value * 60 * 60 * 1000,
                _ => value * 1000, // default: seconds
            }
        }
        None => 2000, // Default 2 seconds
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_watch_interval_seconds() {
        assert_eq!(parse_watch_interval("2s"), 2000);
        assert_eq!(parse_watch_interval("10s"), 10000);
    }

    #[test]
    fn test_parse_watch_interval_minutes() {
        assert_eq!(parse_watch_interval("5m"), 300_000);
    }

    #[test]
    fn test_parse_watch_interval_hours() {
        assert_eq!(parse_watch_interval("1h"), 3_600_000);
    }

    #[test]
    fn test_parse_watch_interval_no_unit() {
        assert_eq!(parse_watch_interval("3"), 3000);
    }

    #[test]
    fn test_parse_watch_interval_invalid() {
        assert_eq!(parse_watch_interval("abc"), 2000);
    }
}
