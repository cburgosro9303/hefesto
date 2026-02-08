use std::sync::Arc;
use std::thread;
use std::time::Duration;

use chrono::Local;
use regex::Regex;

use hefesto_domain::command::{CommandInfo, CommandResult, Documentation, ExampleDoc, OptionDoc};
use hefesto_domain::command_parser;
use hefesto_domain::config::HefestoConfig;
use hefesto_domain::procwatch::alert_rule::AlertRule;
use hefesto_domain::procwatch::process_sample::ProcessSample;
use hefesto_platform::process_sampler::ProcessSampler;

use crate::command::Command;
use crate::context::ExecutionContext;
use crate::port::OutputPort;

use super::service::alert_parser::AlertParser;
use super::service::process_monitor_service::{DumpType, ProcessMonitorService, TopMode};

/// Time format for log lines: HH:MM:SS.
const TIME_FORMAT: &str = "%H:%M:%S";

/// Command for monitoring processes -- CPU, RAM, threads, file descriptors, I/O.
/// Supports alerts with a mini DSL and optional diagnostic dumps.
pub struct ProcWatchCommand {
    info: CommandInfo,
    sampler: Arc<dyn ProcessSampler>,
    alert_parser: AlertParser,
}

impl ProcWatchCommand {
    /// Creates a new `ProcWatchCommand` backed by the given process sampler.
    pub fn new(sampler: Arc<dyn ProcessSampler>) -> Self {
        let info = CommandInfo::new(
            "proc-watch",
            "Monitor de procesos (CPU/RAM/threads/FD/IO) con alertas",
        )
        .with_category("system".to_string())
        .with_aliases(vec!["pw".into(), "pwatch".into(), "procmon".into()])
        .with_documentation(build_documentation());

        Self {
            info,
            sampler,
            alert_parser: AlertParser::new(),
        }
    }
}

impl Command for ProcWatchCommand {
    fn info(&self) -> &CommandInfo {
        &self.info
    }

    fn execute(&self, ctx: &ExecutionContext, args: &[String]) -> CommandResult {
        let parsed = command_parser::parse(args);
        let output = &ctx.output;
        let config = ctx.config;

        // ── Help for alert syntax ──────────────────────────────────────
        if parsed.get_boolean("list-alerts") {
            output.println(AlertParser::syntax_help());
            output.flush();
            return CommandResult::success();
        }

        // ── Output format flags ────────────────────────────────────────
        let json = parsed.get_boolean("json") || parsed.get_boolean("j");
        let jsonl = parsed.get_boolean("jsonl");
        let compact = parsed.get_boolean("compact");
        let quiet = parsed.get_boolean("quiet") || parsed.get_boolean("q");
        let once = parsed.get_boolean("once");

        // ── Interval ───────────────────────────────────────────────────
        let interval_str = parsed
            .get_flag("interval")
            .or_else(|| parsed.get_flag("i"))
            .unwrap_or("1s");
        let interval = parse_interval(interval_str);

        // ── Count ──────────────────────────────────────────────────────
        let mut count: i32 = parsed
            .get_flag_as_int("count")
            .or_else(|| parsed.get_flag_as_int("c"))
            .unwrap_or(-1);
        if once {
            count = 1;
        }

        // ── Top-mode limit ─────────────────────────────────────────────
        let limit = parsed
            .get_flag_as_int("limit")
            .or_else(|| parsed.get_flag_as_int("l"))
            .unwrap_or(config.default_top_limit as i32) as usize;

        // ── Alert rules ────────────────────────────────────────────────
        let alerts = parse_alert_flags(&parsed, &self.alert_parser);

        // ── Dump on breach ─────────────────────────────────────────────
        let dump_type = parsed.get_flag("dump-on-breach").and_then(parse_dump_type);

        // ── Build the service ──────────────────────────────────────────
        let mut service = ProcessMonitorService::with_alert_history(
            Arc::clone(&self.sampler),
            config.alert_history_duration(),
        );

        // ── Mode: Top ──────────────────────────────────────────────────
        if parsed.has_flag("top") || parsed.has_flag("t") {
            let mode_str = parsed
                .get_flag("top")
                .or_else(|| parsed.get_flag("t"))
                .unwrap_or("cpu");

            return handle_top_mode(
                output.as_ref(),
                &service,
                config,
                mode_str,
                limit,
                interval,
                count,
                json,
                jsonl,
                compact,
            );
        }

        // ── Mode: By PID ──────────────────────────────────────────────
        if parsed.has_flag("pid") || parsed.has_flag("p") {
            let pid_str = match parsed.get_flag("pid").or_else(|| parsed.get_flag("p")) {
                Some(s) => s,
                None => return CommandResult::failure("Se requiere un PID valido"),
            };

            let pid: u32 = match pid_str.parse() {
                Ok(v) => v,
                Err(_) => return CommandResult::failure(format!("PID invalido: {pid_str}")),
            };

            return handle_pid_mode(
                output.as_ref(),
                &mut service,
                pid,
                interval,
                count,
                &alerts,
                dump_type,
                json,
                jsonl,
                compact,
                quiet,
            );
        }

        // ── Mode: By Name ─────────────────────────────────────────────
        if parsed.has_flag("name") || parsed.has_flag("n") {
            let name = match parsed.get_flag("name").or_else(|| parsed.get_flag("n")) {
                Some(s) => s,
                None => return CommandResult::failure("Se requiere un nombre de proceso"),
            };

            let match_filter = parsed.get_flag("match").or_else(|| parsed.get_flag("m"));

            return handle_name_mode(
                output.as_ref(),
                &mut service,
                name,
                match_filter,
                interval,
                count,
                &alerts,
                dump_type,
                json,
                jsonl,
                compact,
                quiet,
            );
        }

        // ── No target specified ────────────────────────────────────────
        output.print_error("Debe especificar --pid, --name o --top");
        output.println("\nUso: proc-watch --pid <PID>");
        output.println("     proc-watch --name <proceso>");
        output.println("     proc-watch --top cpu|memory");
        output.println("\nEjecute 'proc-watch --help' para mas informacion");
        output.flush();
        CommandResult::failure("Falta especificar objetivo")
    }
}

// ════════════════════════════════════════════════════════════════════════
//  Mode handlers
// ════════════════════════════════════════════════════════════════════════

#[allow(clippy::too_many_arguments)]
fn handle_top_mode(
    output: &dyn OutputPort,
    service: &ProcessMonitorService,
    config: &HefestoConfig,
    mode_str: &str,
    limit: usize,
    interval: Duration,
    count: i32,
    json: bool,
    jsonl: bool,
    compact: bool,
) -> CommandResult {
    let top_mode = match mode_str.to_lowercase().as_str() {
        "cpu" | "c" => TopMode::Cpu,
        "mem" | "memory" | "m" => TopMode::Memory,
        other => return CommandResult::failure(format!("Modo desconocido: {other}")),
    };

    if !json && !jsonl && count != 1 {
        output.println("Presione Ctrl+C para detener");
        output.flush();
    }

    let mut samples_done = 0;
    let interval_ms = interval.as_millis() as u64;

    loop {
        if count >= 0 && samples_done >= count {
            break;
        }

        let samples = match top_mode {
            TopMode::Cpu => service.top_by_cpu(limit),
            TopMode::Memory => service.top_by_memory(limit),
        };
        samples_done += 1;

        if samples.is_empty() {
            output.print_warning("No se encontraron procesos");
            output.flush();
        } else if json {
            output.println(&format_top_json(&samples));
            output.flush();
        } else if jsonl {
            output.println(&format_top_jsonl(&samples));
            output.flush();
        } else if compact {
            clear_screen(output);
            output.println(&format_top_compact(&samples, top_mode));
            output.flush();
        } else {
            clear_screen(output);
            output.println(&format_top_table(&samples, top_mode, config));
            output.flush();
        }

        // Wait for next interval unless this was the last sample.
        if count < 0 || samples_done < count {
            thread::sleep(Duration::from_millis(interval_ms));
        }
    }

    CommandResult::success()
}

#[allow(clippy::too_many_arguments)]
fn handle_pid_mode(
    output: &dyn OutputPort,
    service: &mut ProcessMonitorService,
    pid: u32,
    interval: Duration,
    count: i32,
    alerts: &[AlertRule],
    dump_type: Option<DumpType>,
    json: bool,
    jsonl: bool,
    compact: bool,
    quiet: bool,
) -> CommandResult {
    let interval_ms = interval.as_millis() as u64;

    // Verify process exists.
    if service.sample_by_pid(pid).is_none() {
        return CommandResult::failure(format!("Proceso no encontrado: {pid}"));
    }

    if !quiet && !json && !jsonl && count != 1 {
        output.println(&format!(
            "Monitoreando PID {pid} - Presione Ctrl+C para detener"
        ));
        output.flush();
    }

    let mut samples_done = 0;

    loop {
        if count >= 0 && samples_done >= count {
            break;
        }

        let sample = match service.sample_by_pid(pid) {
            Some(s) => s,
            None => {
                output.print_warning(&format!("Proceso {pid} ya no existe"));
                output.flush();
                break;
            }
        };
        samples_done += 1;

        // ── Output sample ──────────────────────────────────────────
        if !quiet {
            if count == 1 {
                // Detailed output for a single snapshot.
                if json || jsonl {
                    output.println(&format_sample_json(&sample));
                } else {
                    output.println(&format_sample_detailed(&sample));
                }
            } else {
                // Continuous line output.
                if json {
                    output.println(&format_sample_json(&sample));
                } else if jsonl {
                    output.println(&format_sample_jsonl(&sample));
                } else if compact {
                    output.println(&format_sample_compact(&sample));
                } else {
                    output.println(&format_sample_line(&sample));
                }
            }
            output.flush();
        }

        // ── Evaluate alerts ────────────────────────────────────────
        if !alerts.is_empty() {
            let results = service.evaluate_alerts(&sample, alerts);
            for result in &results {
                if result.triggered {
                    output.print_warning(&result.message);
                    output.flush();
                    if let Some(dt) = dump_type {
                        output.println("Ejecutando dump...");
                        let dump = service.execute_dump_command(pid, dt);
                        output.println(&dump);
                        output.flush();
                    }
                }
            }
        }

        // Wait for next interval.
        if count < 0 || samples_done < count {
            thread::sleep(Duration::from_millis(interval_ms));
        }
    }

    CommandResult::success()
}

#[allow(clippy::too_many_arguments)]
fn handle_name_mode(
    output: &dyn OutputPort,
    service: &mut ProcessMonitorService,
    name: &str,
    match_filter: Option<&str>,
    interval: Duration,
    count: i32,
    alerts: &[AlertRule],
    dump_type: Option<DumpType>,
    json: bool,
    jsonl: bool,
    compact: bool,
    quiet: bool,
) -> CommandResult {
    let interval_ms = interval.as_millis() as u64;

    // Verify at least one process matches.
    let initial = filter_by_match(&service.sample_by_name(name), match_filter);
    if initial.is_empty() {
        let msg = match match_filter {
            Some(m) => format!("No se encontraron procesos con nombre: {name} y match: {m}"),
            None => format!("No se encontraron procesos con nombre: {name}"),
        };
        return CommandResult::failure(msg);
    }

    if !quiet && !json && !jsonl && count != 1 {
        output.println(&format!(
            "Monitoreando procesos '{name}' - Presione Ctrl+C para detener"
        ));
        output.flush();
    }

    let mut samples_done = 0;

    loop {
        if count >= 0 && samples_done >= count {
            break;
        }

        let samples = filter_by_match(&service.sample_by_name(name), match_filter);
        samples_done += 1;

        if samples.is_empty() {
            output.print_warning(&format!("No se encontraron procesos con nombre: {name}"));
            output.flush();
            break;
        }

        if count == 1 {
            // Detailed output: enrich with per-process FD/thread info.
            let enriched: Vec<ProcessSample> = samples
                .iter()
                .map(|s| service.sample_by_pid(s.pid).unwrap_or_else(|| s.clone()))
                .collect();

            if json || jsonl {
                output.println(&format_multiple_samples_json(&enriched));
            } else {
                for sample in &enriched {
                    output.println(&format_sample_detailed(sample));
                    output.println("");
                }
            }
        } else {
            // Continuous line output.
            for sample in &samples {
                if !quiet {
                    if json {
                        output.println(&format_sample_json(sample));
                    } else if jsonl {
                        output.println(&format_sample_jsonl(sample));
                    } else if compact {
                        output.println(&format_sample_compact(sample));
                    } else {
                        output.println(&format_sample_line(sample));
                    }
                }

                // Evaluate alerts.
                if !alerts.is_empty() {
                    let results = service.evaluate_alerts(sample, alerts);
                    for result in &results {
                        if result.triggered {
                            output.print_warning(&result.message);
                            if let Some(dt) = dump_type {
                                output.println("Ejecutando dump...");
                                let dump = service.execute_dump_command(sample.pid, dt);
                                output.println(&dump);
                            }
                        }
                    }
                }
            }
        }
        output.flush();

        // Wait for next interval.
        if count < 0 || samples_done < count {
            thread::sleep(Duration::from_millis(interval_ms));
        }
    }

    CommandResult::success()
}

// ════════════════════════════════════════════════════════════════════════
//  Parsing helpers
// ════════════════════════════════════════════════════════════════════════

/// Parses a human-readable interval string such as `"1s"`, `"500ms"`, `"5m"`, `"1h"`.
fn parse_interval(input: &str) -> Duration {
    // Pattern: digits followed by an optional unit suffix.
    let re = Regex::new(r"^(\d+)(ms|s|m|h)?$").expect("Invalid interval regex");
    match re.captures(input) {
        Some(caps) => {
            let value: u64 = caps[1].parse().unwrap_or(1);
            let unit = caps.get(2).map_or("s", |m| m.as_str());

            match unit {
                "ms" => Duration::from_millis(value),
                "s" => Duration::from_secs(value),
                "m" => Duration::from_secs(value * 60),
                "h" => Duration::from_secs(value * 3600),
                _ => Duration::from_secs(value),
            }
        }
        None => Duration::from_secs(1),
    }
}

/// Extracts all `--alert` / `-a` flag values and parses them into `AlertRule`s.
fn parse_alert_flags(
    parsed: &command_parser::ParsedArgs,
    alert_parser: &AlertParser,
) -> Vec<AlertRule> {
    let mut rules = Vec::new();

    for (key, value) in &parsed.flags {
        if key == "alert" || key == "a" {
            if let Ok(rule) = alert_parser.parse(value) {
                rules.push(rule);
            }
        }
    }

    rules
}

/// Parses a dump-type string into a `DumpType` variant.
fn parse_dump_type(s: &str) -> Option<DumpType> {
    match s.to_lowercase().as_str() {
        "lsof" => Some(DumpType::Lsof),
        "pstack" => Some(DumpType::Pstack),
        _ => None,
    }
}

/// Filters samples whose command line contains the `match_str` (case-insensitive).
fn filter_by_match(samples: &[ProcessSample], match_str: Option<&str>) -> Vec<ProcessSample> {
    match match_str {
        None | Some("") => samples.to_vec(),
        Some(m) => {
            let lower = m.to_lowercase();
            samples
                .iter()
                .filter(|s| s.command_line.to_lowercase().contains(&lower))
                .cloned()
                .collect()
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
//  Screen helpers
// ════════════════════════════════════════════════════════════════════════

/// Clears the terminal screen if possible, otherwise prints a separator.
fn clear_screen(output: &dyn OutputPort) {
    // In a CLI piped through Gradle or non-interactive terminals,
    // ANSI escape sequences may not work. Use a separator instead.
    output.println(&format!("\n{}", "=".repeat(90)));
}

// ════════════════════════════════════════════════════════════════════════
//  Formatting: Top mode
// ════════════════════════════════════════════════════════════════════════

fn format_top_table(samples: &[ProcessSample], mode: TopMode, config: &HefestoConfig) -> String {
    let now = Local::now().format(TIME_FORMAT);
    let mut sb = String::with_capacity(2048);

    sb.push_str(&format!(
        "TOP PROCESOS POR {} - {}\n",
        if mode == TopMode::Cpu {
            "CPU"
        } else {
            "MEMORIA"
        },
        now,
    ));
    sb.push_str(&"-".repeat(90));
    sb.push('\n');
    sb.push_str(&format!(
        "{:<8} {:<20} {:<8} {:<10} {:<10} {:<8} {}\n",
        "PID", "NOMBRE", "CPU%", "RSS", "VIRTUAL", "THREADS", "COMANDO"
    ));
    sb.push_str(&"-".repeat(90));
    sb.push('\n');

    for s in samples {
        sb.push_str(&format!(
            "{:<8} {:<20} {:>7.1}% {:<10} {:<10} {:>8} {}\n",
            s.pid,
            config.truncate_process_name(&s.name),
            s.cpu.percent_instant,
            s.memory.rss_formatted(),
            s.memory.virtual_formatted(),
            s.thread_count,
            config.truncate_command(&s.command_line),
        ));
    }

    sb
}

fn format_top_compact(samples: &[ProcessSample], mode: TopMode) -> String {
    let now = Local::now().format(TIME_FORMAT);
    let mut sb = format!("TOP {mode} @ {now}: ");

    let display_count = std::cmp::min(5, samples.len());
    for s in &samples[..display_count] {
        if mode == TopMode::Cpu {
            sb.push_str(&format!("{}({:.1}%) ", s.name, s.cpu.percent_instant));
        } else {
            sb.push_str(&format!("{}({}) ", s.name, s.memory.rss_formatted()));
        }
    }

    sb
}

fn format_top_json(samples: &[ProcessSample]) -> String {
    let now = Local::now().format(TIME_FORMAT);
    let mut sb = String::with_capacity(4096);
    sb.push_str(&format!("{{\"timestamp\":\"{now}\",\"processes\":["));

    for (i, s) in samples.iter().enumerate() {
        if i > 0 {
            sb.push(',');
        }
        sb.push_str(&format_sample_json(s));
    }

    sb.push_str("]}");
    sb
}

fn format_top_jsonl(samples: &[ProcessSample]) -> String {
    samples
        .iter()
        .map(format_sample_jsonl)
        .collect::<Vec<_>>()
        .join("\n")
}

// ════════════════════════════════════════════════════════════════════════
//  Formatting: Single sample
// ════════════════════════════════════════════════════════════════════════

fn format_sample_line(s: &ProcessSample) -> String {
    let now = Local::now().format(TIME_FORMAT);
    format!(
        "[{now}] PID={} {} CPU={:.1}% RSS={} VSZ={} THR={} FD={}",
        s.pid,
        s.name,
        s.cpu.percent_instant,
        s.memory.rss_formatted(),
        s.memory.virtual_formatted(),
        s.thread_count,
        s.open_file_descriptors,
    )
}

fn format_sample_compact(s: &ProcessSample) -> String {
    format!(
        "{}|{}|{:.1}%|{}|{}|{}",
        s.pid,
        s.name,
        s.cpu.percent_instant,
        s.memory.rss_formatted(),
        s.thread_count,
        s.open_file_descriptors,
    )
}

fn format_sample_detailed(s: &ProcessSample) -> String {
    let mut sb = String::with_capacity(1024);

    sb.push_str(&format!("PROCESO: {} (PID: {})\n", s.name, s.pid));
    sb.push_str(&"=".repeat(60));
    sb.push('\n');
    sb.push_str(&format!("  Usuario:    {}\n", s.user));
    sb.push_str(&format!("  Estado:     {}\n", s.state.description()));
    sb.push_str(&format!("  Comando:    {}\n", s.command_line));
    sb.push('\n');
    sb.push_str("  CPU:\n");
    sb.push_str(&format!("    Actual:   {}\n", s.cpu.percent_formatted()));
    sb.push_str(&format!("    User:     {} ms\n", s.cpu.user_time_ms));
    sb.push_str(&format!("    System:   {} ms\n", s.cpu.system_time_ms));
    sb.push('\n');
    sb.push_str("  MEMORIA:\n");
    sb.push_str(&format!("    RSS:      {}\n", s.memory.rss_formatted()));
    sb.push_str(&format!("    Virtual:  {}\n", s.memory.virtual_formatted()));
    sb.push_str(&format!(
        "    % Total:  {:.1}%\n",
        s.memory.percent_of_total
    ));
    sb.push('\n');
    sb.push_str("  I/O:\n");
    sb.push_str(&format!("    Read:     {}\n", s.io.read_formatted()));
    sb.push_str(&format!("    Write:    {}\n", s.io.write_formatted()));
    sb.push('\n');
    sb.push_str("  RECURSOS:\n");
    sb.push_str(&format!("    Threads:  {}\n", s.thread_count));
    sb.push_str(&format!("    FDs:      {}\n", s.open_file_descriptors));

    sb
}

fn format_sample_json(s: &ProcessSample) -> String {
    let mut sb = String::with_capacity(512);
    sb.push('{');
    sb.push_str(&format!("\"pid\":{}", s.pid));
    sb.push_str(&format!(",\"name\":\"{}\"", escape_json(&s.name)));
    sb.push_str(&format!(",\"user\":\"{}\"", escape_json(&s.user)));
    sb.push_str(&format!(",\"state\":\"{}\"", s.state.code()));
    sb.push_str(&format!(
        ",\"commandLine\":\"{}\"",
        escape_json(&s.command_line)
    ));
    sb.push_str(&format!(
        ",\"cpu\":{{\"percent\":{:.2},\"userMs\":{},\"systemMs\":{}}}",
        s.cpu.percent_instant, s.cpu.user_time_ms, s.cpu.system_time_ms,
    ));
    sb.push_str(&format!(
        ",\"memory\":{{\"rssBytes\":{},\"virtualBytes\":{},\"percentOfTotal\":{:.2}}}",
        s.memory.rss_bytes, s.memory.virtual_bytes, s.memory.percent_of_total,
    ));
    sb.push_str(&format!(
        ",\"io\":{{\"readBytes\":{},\"writeBytes\":{}}}",
        s.io.read_bytes, s.io.write_bytes,
    ));
    sb.push_str(&format!(",\"threads\":{}", s.thread_count));
    sb.push_str(&format!(",\"fileDescriptors\":{}", s.open_file_descriptors));
    sb.push('}');
    sb
}

fn format_sample_jsonl(s: &ProcessSample) -> String {
    format_sample_json(s)
}

fn format_multiple_samples_json(samples: &[ProcessSample]) -> String {
    let mut sb = String::with_capacity(samples.len() * 512);
    sb.push('[');
    for (i, s) in samples.iter().enumerate() {
        if i > 0 {
            sb.push(',');
        }
        sb.push_str(&format_sample_json(s));
    }
    sb.push(']');
    sb
}

// ════════════════════════════════════════════════════════════════════════
//  String utilities
// ════════════════════════════════════════════════════════════════════════

/// Escapes special characters for JSON string values.
fn escape_json(s: &str) -> String {
    s.replace('\\', "\\\\")
        .replace('"', "\\\"")
        .replace('\n', "\\n")
        .replace('\r', "\\r")
        .replace('\t', "\\t")
}

// ════════════════════════════════════════════════════════════════════════
//  Documentation builder
// ════════════════════════════════════════════════════════════════════════

fn build_documentation() -> Documentation {
    Documentation::new("proc-watch [opciones] [--pid <PID> | --name <proceso>]")
        .with_long_description(
            "Monitor avanzado de procesos del sistema. Muestra metricas en tiempo real \
             de CPU, memoria, threads, descriptores de archivo e I/O. Soporta alertas \
             configurables con DSL, monitoreo continuo y salida JSON/JSONL.",
        )
        // Target selection
        .with_option(OptionDoc::with_value("pid", "ID del proceso a monitorear").with_short("p"))
        .with_option(
            OptionDoc::with_value("name", "Nombre del proceso (busqueda parcial)").with_short("n"),
        )
        .with_option(
            OptionDoc::with_value(
                "match",
                "Filtro adicional en linea de comandos (con --name)",
            )
            .with_short("m"),
        )
        // Mode options
        .with_option(
            OptionDoc::with_value("top", "Modo top: cpu o memory (ej: --top cpu)").with_short("t"),
        )
        .with_option(
            OptionDoc::with_value("limit", "Limite de procesos en modo top (default: 10)")
                .with_short("l"),
        )
        // Monitoring options
        .with_option(
            OptionDoc::with_value(
                "interval",
                "Intervalo de muestreo (ej: 1s, 500ms, 5m). Default: 1s",
            )
            .with_short("i"),
        )
        .with_option(
            OptionDoc::with_value("count", "Numero de muestras (default: infinito)")
                .with_short("c"),
        )
        .with_option(OptionDoc::flag("once", "Muestra una sola vez y termina"))
        // Alert options
        .with_option(
            OptionDoc::with_value("alert", "Regla de alerta DSL (puede repetirse)").with_short("a"),
        )
        .with_option(OptionDoc::with_value(
            "dump-on-breach",
            "Ejecutar dump al violar alerta: lsof, pstack",
        ))
        // Output options
        .with_option(OptionDoc::flag("json", "Salida en formato JSON").with_short("j"))
        .with_option(OptionDoc::flag(
            "jsonl",
            "Salida en formato JSON Lines (una linea por muestra)",
        ))
        .with_option(OptionDoc::flag("compact", "Formato compacto de una linea"))
        .with_option(OptionDoc::flag("quiet", "Solo mostrar alertas").with_short("q"))
        // Utility options
        .with_option(OptionDoc::flag(
            "list-alerts",
            "Muestra sintaxis de alertas disponibles",
        ))
        // Examples
        .with_example(ExampleDoc::new(
            "proc-watch --pid 4123",
            "Monitorea proceso por PID",
        ))
        .with_example(ExampleDoc::new(
            "proc-watch --name java",
            "Monitorea todos los procesos 'java'",
        ))
        .with_example(ExampleDoc::new(
            "proc-watch --name java --match 'myservice'",
            "Filtra por comando",
        ))
        .with_example(ExampleDoc::new(
            "proc-watch --top cpu --limit 10",
            "Top 10 por CPU en tiempo real",
        ))
        .with_example(ExampleDoc::new(
            "proc-watch --top memory --interval 5s",
            "Top por memoria cada 5s",
        ))
        .with_example(ExampleDoc::new(
            "proc-watch --pid 4123 --once",
            "Muestra metricas una vez",
        ))
        .with_example(ExampleDoc::new(
            "proc-watch --pid 4123 --json",
            "Salida JSON",
        ))
        .with_example(ExampleDoc::new(
            "proc-watch --pid 4123 --jsonl --count 100",
            "100 muestras en JSONL",
        ))
        .with_example(ExampleDoc::new(
            "proc-watch --pid 4123 --alert 'cpu>80%'",
            "Alerta si CPU > 80%",
        ))
        .with_example(ExampleDoc::new(
            "proc-watch --pid 4123 --alert 'rss>1.5GB'",
            "Alerta si RAM > 1.5GB",
        ))
        .with_example(ExampleDoc::new(
            "proc-watch --pid 4123 --alert 'cpu>80% for 30s'",
            "Sostenido 30s",
        ))
        .with_example(ExampleDoc::new(
            "proc-watch --pid 4123 --alert 'threads>100'",
            "Alerta por threads",
        ))
        .with_example(ExampleDoc::new(
            "proc-watch --pid 4123 --dump-on-breach lsof",
            "Dump al violar",
        ))
        .with_example(ExampleDoc::new(
            "proc-watch --list-alerts",
            "Muestra sintaxis de alertas",
        ))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_interval_seconds() {
        assert_eq!(parse_interval("1s"), Duration::from_secs(1));
        assert_eq!(parse_interval("5s"), Duration::from_secs(5));
    }

    #[test]
    fn test_parse_interval_millis() {
        assert_eq!(parse_interval("500ms"), Duration::from_millis(500));
    }

    #[test]
    fn test_parse_interval_minutes() {
        assert_eq!(parse_interval("5m"), Duration::from_secs(300));
    }

    #[test]
    fn test_parse_interval_hours() {
        assert_eq!(parse_interval("1h"), Duration::from_secs(3600));
    }

    #[test]
    fn test_parse_interval_bare_number_defaults_to_seconds() {
        assert_eq!(parse_interval("3"), Duration::from_secs(3));
    }

    #[test]
    fn test_parse_interval_invalid_defaults_to_1s() {
        assert_eq!(parse_interval("abc"), Duration::from_secs(1));
    }

    #[test]
    fn test_parse_dump_type() {
        assert_eq!(parse_dump_type("lsof"), Some(DumpType::Lsof));
        assert_eq!(parse_dump_type("LSOF"), Some(DumpType::Lsof));
        assert_eq!(parse_dump_type("pstack"), Some(DumpType::Pstack));
        assert_eq!(parse_dump_type("unknown"), None);
    }

    #[test]
    fn test_filter_by_match_none() {
        let samples = vec![ProcessSample::minimal(1, "test", "user")];
        let filtered = filter_by_match(&samples, None);
        assert_eq!(filtered.len(), 1);
    }

    #[test]
    fn test_filter_by_match_some() {
        let mut s1 = ProcessSample::minimal(1, "java", "user");
        s1.command_line = "java -jar myservice.jar".to_string();
        let mut s2 = ProcessSample::minimal(2, "java", "user");
        s2.command_line = "java -jar other.jar".to_string();

        let filtered = filter_by_match(&[s1, s2], Some("myservice"));
        assert_eq!(filtered.len(), 1);
        assert_eq!(filtered[0].pid, 1);
    }

    #[test]
    fn test_filter_by_match_case_insensitive() {
        let mut s = ProcessSample::minimal(1, "java", "user");
        s.command_line = "java -jar MyService.jar".to_string();

        let filtered = filter_by_match(&[s], Some("myservice"));
        assert_eq!(filtered.len(), 1);
    }

    #[test]
    fn test_escape_json() {
        assert_eq!(escape_json(r#"hello "world""#), r#"hello \"world\""#);
        assert_eq!(escape_json("line1\nline2"), r"line1\nline2");
        assert_eq!(escape_json("path\\to\\file"), r"path\\to\\file");
        assert_eq!(escape_json("tab\there"), r"tab\there");
    }

    #[test]
    fn test_format_sample_json_structure() {
        let sample = ProcessSample::minimal(42, "my-proc", "root");
        let json = format_sample_json(&sample);
        assert!(json.starts_with('{'));
        assert!(json.ends_with('}'));
        assert!(json.contains("\"pid\":42"));
        assert!(json.contains("\"name\":\"my-proc\""));
        assert!(json.contains("\"cpu\":{"));
        assert!(json.contains("\"memory\":{"));
        assert!(json.contains("\"io\":{"));
    }

    #[test]
    fn test_format_sample_line_contains_pid() {
        let sample = ProcessSample::minimal(123, "nginx", "www");
        let line = format_sample_line(&sample);
        assert!(line.contains("PID=123"));
        assert!(line.contains("nginx"));
    }

    #[test]
    fn test_format_sample_compact_pipe_separated() {
        let sample = ProcessSample::minimal(7, "worker", "app");
        let compact = format_sample_compact(&sample);
        assert!(compact.contains('|'));
        assert!(compact.starts_with("7|worker|"));
    }

    #[test]
    fn test_format_sample_detailed_contains_sections() {
        let sample = ProcessSample::minimal(1, "test-proc", "dev");
        let detailed = format_sample_detailed(&sample);
        assert!(detailed.contains("PROCESO: test-proc (PID: 1)"));
        assert!(detailed.contains("CPU:"));
        assert!(detailed.contains("MEMORIA:"));
        assert!(detailed.contains("I/O:"));
        assert!(detailed.contains("RECURSOS:"));
    }

    #[test]
    fn test_format_multiple_samples_json_array() {
        let s1 = ProcessSample::minimal(1, "a", "u");
        let s2 = ProcessSample::minimal(2, "b", "u");
        let json = format_multiple_samples_json(&[s1, s2]);
        assert!(json.starts_with('['));
        assert!(json.ends_with(']'));
        assert!(json.contains("\"pid\":1"));
        assert!(json.contains("\"pid\":2"));
    }
}
