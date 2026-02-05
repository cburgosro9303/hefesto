package org.iumotionlabs.hefesto.feature.procwatch;

import org.iumotionlabs.hefesto.command.*;
import org.iumotionlabs.hefesto.core.config.HefestoConfig;
import org.iumotionlabs.hefesto.core.context.ExecutionContext;
import org.iumotionlabs.hefesto.feature.procwatch.model.*;
import org.iumotionlabs.hefesto.feature.procwatch.service.*;
import org.iumotionlabs.hefesto.help.Documentation;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Locale;

/**
 * Command for monitoring processes - CPU, RAM, threads, file descriptors, I/O.
 * Supports alerts with a mini DSL and optional JVM/JMX integration.
 */
public final class ProcWatchCommand implements Command {

    private static final Pattern INTERVAL_PATTERN = Pattern.compile("(\\d+)([smh]?)");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final CommandInfo INFO = new CommandInfo(
        "proc-watch",
        "Monitor de procesos (CPU/RAM/threads/FD/IO) con alertas",
        "system"
    ).withAliases("pw", "pwatch", "procmon")
     .withDocumentation(Documentation.builder()
        .synopsis("proc-watch [opciones] [--pid <PID> | --name <proceso>]")
        .description("Monitor avanzado de procesos del sistema. Muestra metricas en tiempo real " +
            "de CPU, memoria, threads, descriptores de archivo e I/O. Soporta alertas " +
            "configurables con DSL, monitoreo continuo, salida JSON/JSONL, y metricas JVM via JMX.")
        // Target selection
        .option("pid", "p", "ID del proceso a monitorear")
        .option("name", "n", "Nombre del proceso (busqueda parcial)")
        .option("match", "m", "Filtro adicional en linea de comandos (con --name)")
        // Mode options
        .option("top", "t", "Modo top: cpu o memory (ej: --top cpu)")
        .option("limit", "l", "Limite de procesos en modo top (default: 10)")
        // Monitoring options
        .option("interval", "i", "Intervalo de muestreo (ej: 1s, 500ms, 5m). Default: 1s")
        .option("count", "c", "Numero de muestras (default: infinito)")
        .option("once", "Muestra una sola vez y termina")
        // Alert options
        .option("alert", "a", "Regla de alerta DSL (puede repetirse)")
        .option("dump-on-breach", "Ejecutar dump al violar alerta: jstack, jmap, lsof, pstack")
        // Output options
        .option("json", "j", "Salida en formato JSON")
        .option("jsonl", "Salida en formato JSON Lines (una linea por muestra)")
        .option("compact", "Formato compacto de una linea")
        .option("quiet", "q", "Solo mostrar alertas")
        // JVM options
        .option("jvm", "Incluir metricas JVM via JMX (heap, GC, threads)")
        .option("jmx-port", "Puerto JMX remoto (default: auto-detect)")
        // Utility options
        .option("list-alerts", "Muestra sintaxis de alertas disponibles")
        // Examples
        .example("proc-watch --pid 4123", "Monitorea proceso por PID")
        .example("proc-watch --name java", "Monitorea todos los procesos 'java'")
        .example("proc-watch --name java --match 'myservice'", "Filtra por comando")
        .example("proc-watch --top cpu --limit 10", "Top 10 por CPU en tiempo real")
        .example("proc-watch --top memory --interval 5s", "Top por memoria cada 5s")
        .example("proc-watch --pid 4123 --once", "Muestra metricas una vez")
        .example("proc-watch --pid 4123 --json", "Salida JSON")
        .example("proc-watch --pid 4123 --jsonl --count 100", "100 muestras en JSONL")
        .example("proc-watch --pid 4123 --alert 'cpu>80%'", "Alerta si CPU > 80%")
        .example("proc-watch --pid 4123 --alert 'rss>1.5GB'", "Alerta si RAM > 1.5GB")
        .example("proc-watch --pid 4123 --alert 'cpu>80% for 30s'", "Sostenido 30s")
        .example("proc-watch --pid 4123 --alert 'threads>100'", "Alerta por threads")
        .example("proc-watch --pid 4123 --jvm", "Incluye metricas JVM")
        .example("proc-watch --pid 4123 --dump-on-breach jstack", "Dump al violar")
        .example("proc-watch --list-alerts", "Muestra sintaxis de alertas")
        .seeAlso("top, htop, ps, jcmd, jstat")
        .build());

    private final ProcessMonitorService service;
    private final AlertParser alertParser;

    private final HefestoConfig config = HefestoConfig.get();

    public ProcWatchCommand() {
        this.service = new ProcessMonitorService();
        this.alertParser = new AlertParser();
    }

    @Override
    public CommandInfo info() {
        return INFO;
    }

    @Override
    public Optional<Documentation> documentation() {
        return INFO.documentation();
    }

    @Override
    public CommandResult execute(ExecutionContext ctx, String[] args) {
        var parsed = CommandParser.parse(args);
        var output = ctx.output();

        // Help for alert syntax
        if (parsed.getBoolean("list-alerts")) {
            output.info(AlertParser.getSyntaxHelp());
            return CommandResult.success();
        }

        // Output format
        boolean json = parsed.getBoolean("json") || parsed.getBoolean("j");
        boolean jsonl = parsed.getBoolean("jsonl");
        boolean compact = parsed.getBoolean("compact");
        boolean quiet = parsed.getBoolean("quiet") || parsed.getBoolean("q");
        boolean once = parsed.getBoolean("once");
        boolean includeJvm = parsed.getBoolean("jvm");

        // Interval
        Duration interval = parseInterval(parsed.getFlag("interval", parsed.getFlag("i", "1s")));

        // Count
        int count = parsed.getFlagAsInt("count", parsed.getFlagAsInt("c", -1));
        if (once) count = 1;

        // Limit for top mode
        int limit = parsed.getFlagAsInt("limit", parsed.getFlagAsInt("l", 10));

        // Parse alerts
        List<AlertRule> alerts = parseAlerts(parsed);

        // Dump on breach
        String dumpOnBreach = parsed.getFlag("dump-on-breach").orElse(null);
        ProcessMonitorService.DumpType dumpType = parseDumpType(dumpOnBreach);

        // Mode: Top
        if (parsed.hasFlag("top") || parsed.hasFlag("t")) {
            String mode = parsed.getFlag("top", parsed.getFlag("t", "cpu"));
            return handleTopMode(ctx, mode, limit, interval, count, json, jsonl, compact);
        }

        // Mode: By PID
        if (parsed.hasFlag("pid") || parsed.hasFlag("p")) {
            String pidStr = parsed.getFlag("pid", parsed.getFlag("p", null));
            if (pidStr == null) {
                return CommandResult.failure("Se requiere un PID valido");
            }
            long pid;
            try {
                pid = Long.parseLong(pidStr);
            } catch (NumberFormatException e) {
                return CommandResult.failure("PID invalido: " + pidStr);
            }
            return handlePidMode(ctx, pid, interval, count, alerts, dumpType, json, jsonl, compact, quiet, includeJvm);
        }

        // Mode: By Name
        if (parsed.hasFlag("name") || parsed.hasFlag("n")) {
            String name = parsed.getFlag("name", parsed.getFlag("n", null));
            String match = parsed.getFlag("match", parsed.getFlag("m", null));
            if (name == null) {
                return CommandResult.failure("Se requiere un nombre de proceso");
            }
            return handleNameMode(ctx, name, match, interval, count, alerts, dumpType, json, jsonl, compact, quiet);
        }

        // No target specified - show help
        output.error("Debe especificar --pid, --name o --top");
        output.info("\nUso: proc-watch --pid <PID>");
        output.info("     proc-watch --name <proceso>");
        output.info("     proc-watch --top cpu|memory");
        output.info("\nEjecute 'proc-watch --help' para mas informacion");
        return CommandResult.failure("Falta especificar objetivo");
    }

    private CommandResult handleTopMode(ExecutionContext ctx, String mode, int limit,
                                        Duration interval, int count,
                                        boolean json, boolean jsonl, boolean compact) {
        var output = ctx.output();
        ProcessMonitorService.TopMode topMode;

        try {
            topMode = switch (mode.toLowerCase()) {
                case "cpu", "c" -> ProcessMonitorService.TopMode.CPU;
                case "mem", "memory", "m" -> ProcessMonitorService.TopMode.MEMORY;
                default -> throw new IllegalArgumentException("Modo desconocido: " + mode);
            };
        } catch (IllegalArgumentException e) {
            return CommandResult.failure(e.getMessage());
        }

        if (!json && !jsonl && count != 1) {
            output.println("Presione Ctrl+C para detener");
            output.flush();
        }

        int samplesDone = 0;
        long intervalMs = interval.toMillis();

        try {
            while (count < 0 || samplesDone < count) {
                List<ProcessSample> samples = switch (topMode) {
                    case CPU -> service.topByCpu(limit);
                    case MEMORY -> service.topByMemory(limit);
                };

                samplesDone++;

                if (samples.isEmpty()) {
                    output.warning("No se encontraron procesos");
                    output.flush();
                } else if (json) {
                    output.println(formatTopJson(samples));
                    output.flush();
                } else if (jsonl) {
                    output.println(formatTopJsonl(samples));
                    output.flush();
                } else if (compact) {
                    clearScreen(output);
                    output.println(formatTopCompact(samples, topMode));
                    output.flush();
                } else {
                    clearScreen(output);
                    output.println(formatTopTable(samples, topMode));
                    output.flush();
                }

                // Wait for next interval (unless this was the last sample)
                if (count < 0 || samplesDone < count) {
                    Thread.sleep(intervalMs);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            output.error("Error al muestrear: " + e.getMessage());
        }

        return CommandResult.success();
    }

    private CommandResult handlePidMode(ExecutionContext ctx, long pid, Duration interval,
                                        int count, List<AlertRule> alerts,
                                        ProcessMonitorService.DumpType dumpType,
                                        boolean json, boolean jsonl, boolean compact,
                                        boolean quiet, boolean includeJvm) {
        var output = ctx.output();
        long intervalMs = interval.toMillis();

        // Verify process exists
        Optional<ProcessSample> initial = service.sampleByPid(pid);
        if (initial.isEmpty()) {
            return CommandResult.failure("Proceso no encontrado: " + pid);
        }

        if (!quiet && !json && !jsonl && count != 1) {
            output.println("Monitoreando PID " + pid + " - Presione Ctrl+C para detener");
            output.flush();
        }

        int samplesDone = 0;

        try {
            while (count < 0 || samplesDone < count) {
                Optional<ProcessSample> opt = service.sampleByPid(pid);
                if (opt.isEmpty()) {
                    output.warning("Proceso " + pid + " ya no existe");
                    break;
                }

                ProcessSample sample = opt.get();
                samplesDone++;

                // Output sample
                if (!quiet) {
                    JvmMetrics jvm = includeJvm ? service.getJvmMetrics(pid).orElse(null) : null;

                    if (count == 1) {
                        // Detailed output for single sample
                        if (json || jsonl) {
                            output.println(formatSampleJson(sample, jvm));
                        } else {
                            output.println(formatSampleDetailed(sample, jvm));
                        }
                    } else {
                        // Line output for continuous monitoring
                        if (json) {
                            output.println(formatSampleJson(sample, jvm));
                        } else if (jsonl) {
                            output.println(formatSampleJsonl(sample));
                        } else if (compact) {
                            output.println(formatSampleCompact(sample));
                        } else {
                            output.println(formatSampleLine(sample));
                        }
                    }
                    output.flush();
                }

                // Evaluate alerts
                if (!alerts.isEmpty()) {
                    List<AlertResult> results = service.evaluateAlerts(sample, alerts);
                    for (AlertResult result : results) {
                        if (result.triggered()) {
                            output.warning(result.message());
                            output.flush();
                            if (dumpType != null) {
                                output.println("Ejecutando dump...");
                                String dump = service.executeDumpCommand(pid, dumpType);
                                output.println(dump);
                                output.flush();
                            }
                        }
                    }
                }

                // Wait for next interval
                if (count < 0 || samplesDone < count) {
                    Thread.sleep(intervalMs);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            output.error("Error al muestrear: " + e.getMessage());
        }

        return CommandResult.success();
    }

    private CommandResult handleNameMode(ExecutionContext ctx, String name, String match,
                                         Duration interval, int count, List<AlertRule> alerts,
                                         ProcessMonitorService.DumpType dumpType,
                                         boolean json, boolean jsonl, boolean compact,
                                         boolean quiet) {
        var output = ctx.output();
        long intervalMs = interval.toMillis();

        // Verify at least one process matches
        List<ProcessSample> initial = filterByMatch(service.sampleByName(name), match);

        if (initial.isEmpty()) {
            return CommandResult.failure("No se encontraron procesos con nombre: " + name +
                (match != null ? " y match: " + match : ""));
        }

        if (!quiet && !json && !jsonl && count != 1) {
            output.println("Monitoreando procesos '" + name + "' - Presione Ctrl+C para detener");
            output.flush();
        }

        int samplesDone = 0;

        try {
            while (count < 0 || samplesDone < count) {
                List<ProcessSample> samples = filterByMatch(service.sampleByName(name), match);
                samplesDone++;

                if (samples.isEmpty()) {
                    output.warning("No se encontraron procesos con nombre: " + name);
                    output.flush();
                    break;
                }

                if (count == 1) {
                    // Detailed output: enrich with per-process FD/thread info
                    List<ProcessSample> enriched = new ArrayList<>();
                    for (ProcessSample s : samples) {
                        enriched.add(service.sampleByPid(s.pid()).orElse(s));
                    }

                    if (json || jsonl) {
                        output.println(formatMultipleSamplesJson(enriched));
                    } else {
                        for (ProcessSample sample : enriched) {
                            output.println(formatSampleDetailed(sample, null));
                            output.println("");
                        }
                    }
                } else {
                    // Line output for continuous
                    for (ProcessSample sample : samples) {
                        if (!quiet) {
                            if (json) {
                                output.println(formatSampleJson(sample, null));
                            } else if (jsonl) {
                                output.println(formatSampleJsonl(sample));
                            } else if (compact) {
                                output.println(formatSampleCompact(sample));
                            } else {
                                output.println(formatSampleLine(sample));
                            }
                        }

                        // Evaluate alerts
                        if (!alerts.isEmpty()) {
                            List<AlertResult> results = service.evaluateAlerts(sample, alerts);
                            for (AlertResult result : results) {
                                if (result.triggered()) {
                                    output.warning(result.message());
                                    if (dumpType != null) {
                                        output.println("Ejecutando dump...");
                                        String dump = service.executeDumpCommand(sample.pid(), dumpType);
                                        output.println(dump);
                                    }
                                }
                            }
                        }
                    }
                }
                output.flush();

                // Wait for next interval
                if (count < 0 || samplesDone < count) {
                    Thread.sleep(intervalMs);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            output.error("Error al muestrear: " + e.getMessage());
        }

        return CommandResult.success();
    }

    private List<ProcessSample> filterByMatch(List<ProcessSample> samples, String match) {
        if (match == null || match.isEmpty()) return samples;
        return samples.stream()
            .filter(s -> s.commandLine().toLowerCase().contains(match.toLowerCase()))
            .toList();
    }

    private Duration parseInterval(String interval) {
        Matcher m = INTERVAL_PATTERN.matcher(interval);
        if (!m.matches()) {
            return Duration.ofSeconds(1);
        }

        long value = Long.parseLong(m.group(1));
        String unit = m.group(2);

        return switch (unit.toLowerCase()) {
            case "ms" -> Duration.ofMillis(value);
            case "s", "" -> Duration.ofSeconds(value);
            case "m" -> Duration.ofMinutes(value);
            case "h" -> Duration.ofHours(value);
            default -> Duration.ofSeconds(value);
        };
    }

    private List<AlertRule> parseAlerts(CommandParser.ParsedArgs parsed) {
        List<AlertRule> rules = new ArrayList<>();

        // Check for multiple --alert flags
        for (Map.Entry<String, String> entry : parsed.flags().entrySet()) {
            if (entry.getKey().equals("alert") || entry.getKey().equals("a")) {
                try {
                    rules.add(alertParser.parse(entry.getValue()));
                } catch (Exception e) {
                    // Will be reported during execution
                }
            }
        }

        return rules;
    }

    private ProcessMonitorService.DumpType parseDumpType(String dump) {
        if (dump == null) return null;
        return switch (dump.toLowerCase()) {
            case "jstack" -> ProcessMonitorService.DumpType.JSTACK;
            case "jmap" -> ProcessMonitorService.DumpType.JMAP;
            case "lsof" -> ProcessMonitorService.DumpType.LSOF;
            case "pstack" -> ProcessMonitorService.DumpType.PSTACK;
            default -> null;
        };
    }

    // Formatting methods

    private void clearScreen(org.iumotionlabs.hefesto.core.port.output.OutputPort output) {
        // Only use ANSI clear if running in an interactive terminal
        if (System.console() != null && output.supportsColors()) {
            output.print("\033[H\033[2J");
            output.flush();
        } else {
            // Fallback: print separator for non-interactive environments
            output.println("\n" + "═".repeat(90));
        }
    }

    private String formatTopTable(List<ProcessSample> samples, ProcessMonitorService.TopMode mode) {
        StringBuilder sb = new StringBuilder();
        sb.append("TOP PROCESOS POR ").append(mode == ProcessMonitorService.TopMode.CPU ? "CPU" : "MEMORIA");
        sb.append(" - ").append(TIME_FORMAT.format(LocalDateTime.now())).append("\n");
        sb.append("─".repeat(90)).append("\n");
        sb.append(String.format("%-8s %-20s %-8s %-10s %-10s %-8s %s%n",
            "PID", "NOMBRE", "CPU%", "RSS", "VIRTUAL", "THREADS", "COMANDO"));
        sb.append("─".repeat(90)).append("\n");

        for (ProcessSample s : samples) {
            sb.append(String.format("%-8d %-20s %7.1f%% %-10s %-10s %8d %s%n",
                s.pid(),
                truncate(s.name(), 20),
                s.cpu().percentInstant(),
                s.memory().rssFormatted(),
                s.memory().virtualFormatted(),
                s.threadCount(),
                truncate(s.commandLine(), 30)
            ));
        }

        return sb.toString();
    }

    private String formatTopCompact(List<ProcessSample> samples, ProcessMonitorService.TopMode mode) {
        StringBuilder sb = new StringBuilder();
        sb.append("TOP ").append(mode).append(" @ ").append(TIME_FORMAT.format(LocalDateTime.now())).append(": ");

        for (int i = 0; i < Math.min(5, samples.size()); i++) {
            ProcessSample s = samples.get(i);
            if (mode == ProcessMonitorService.TopMode.CPU) {
                sb.append(String.format("%s(%.1f%%) ", s.name(), s.cpu().percentInstant()));
            } else {
                sb.append(String.format("%s(%s) ", s.name(), s.memory().rssFormatted()));
            }
        }

        return sb.toString();
    }

    private String formatTopJson(List<ProcessSample> samples) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"timestamp\":\"").append(TIME_FORMAT.format(LocalDateTime.now())).append("\",");
        sb.append("\"processes\":[");

        for (int i = 0; i < samples.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(formatSampleJson(samples.get(i), null));
        }

        sb.append("]}");
        return sb.toString();
    }

    private String formatTopJsonl(List<ProcessSample> samples) {
        StringBuilder sb = new StringBuilder();
        for (ProcessSample s : samples) {
            sb.append(formatSampleJsonl(s)).append("\n");
        }
        return sb.toString().trim();
    }

    private String formatSampleLine(ProcessSample s) {
        return String.format("[%s] PID=%d %s CPU=%.1f%% RSS=%s VSZ=%s THR=%d FD=%d",
            TIME_FORMAT.format(LocalDateTime.now()),
            s.pid(),
            s.name(),
            s.cpu().percentInstant(),
            s.memory().rssFormatted(),
            s.memory().virtualFormatted(),
            s.threadCount(),
            s.openFileDescriptors()
        );
    }

    private String formatSampleCompact(ProcessSample s) {
        return String.format("%d|%s|%.1f%%|%s|%d|%d",
            s.pid(), s.name(), s.cpu().percentInstant(),
            s.memory().rssFormatted(), s.threadCount(), s.openFileDescriptors());
    }

    private String formatSampleDetailed(ProcessSample s, JvmMetrics jvm) {
        StringBuilder sb = new StringBuilder();
        sb.append("PROCESO: ").append(s.name()).append(" (PID: ").append(s.pid()).append(")\n");
        sb.append("═".repeat(60)).append("\n");
        sb.append("  Usuario:    ").append(s.user()).append("\n");
        sb.append("  Estado:     ").append(s.state().description()).append("\n");
        sb.append("  Comando:    ").append(s.commandLine()).append("\n");
        sb.append("\n");
        sb.append("  CPU:\n");
        sb.append("    Actual:   ").append(s.cpu().percentFormatted()).append("\n");
        sb.append("    User:     ").append(s.cpu().userTimeMs()).append(" ms\n");
        sb.append("    System:   ").append(s.cpu().systemTimeMs()).append(" ms\n");
        sb.append("\n");
        sb.append("  MEMORIA:\n");
        sb.append("    RSS:      ").append(s.memory().rssFormatted()).append("\n");
        sb.append("    Virtual:  ").append(s.memory().virtualFormatted()).append("\n");
        sb.append("    % Total:  ").append(String.format("%.1f%%", s.memory().percentOfTotal())).append("\n");
        sb.append("\n");
        sb.append("  I/O:\n");
        sb.append("    Read:     ").append(s.io().readFormatted()).append("\n");
        sb.append("    Write:    ").append(s.io().writeFormatted()).append("\n");
        sb.append("\n");
        sb.append("  RECURSOS:\n");
        sb.append("    Threads:  ").append(s.threadCount()).append("\n");
        sb.append("    FDs:      ").append(s.openFileDescriptors()).append("\n");

        if (jvm != null && jvm.heap().usedBytes() > 0) {
            sb.append("\n");
            sb.append("  JVM (via JMX):\n");
            sb.append("    Heap:     ").append(jvm.heap().usedFormatted())
              .append(" / ").append(jvm.heap().maxFormatted())
              .append(" (").append(jvm.heap().percentFormatted()).append(")\n");
            sb.append("    Non-Heap: ").append(jvm.nonHeap().usedFormatted()).append("\n");
            sb.append("    Threads:  ").append(jvm.threads().liveThreads())
              .append(" (").append(jvm.threads().daemonThreads()).append(" daemon)\n");
            sb.append("    GC:       ").append(jvm.gc().totalCollections())
              .append(" collections, avg ").append(jvm.gc().avgPauseFormatted()).append("\n");
            sb.append("    Uptime:   ").append(jvm.runtime().uptimeFormatted()).append("\n");

            if (jvm.threads().hasDeadlock()) {
                sb.append("    ⚠ DEADLOCK DETECTADO: ").append(jvm.threads().deadlockedCount()).append(" threads\n");
            }
        }

        return sb.toString();
    }

    private String formatSampleJson(ProcessSample s, JvmMetrics jvm) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"pid\":").append(s.pid()).append(",");
        sb.append("\"name\":\"").append(escape(s.name())).append("\",");
        sb.append("\"user\":\"").append(escape(s.user())).append("\",");
        sb.append("\"state\":\"").append(s.state().code()).append("\",");
        sb.append("\"commandLine\":\"").append(escape(s.commandLine())).append("\",");
        sb.append("\"cpu\":{");
        sb.append("\"percent\":").append(String.format(Locale.US, "%.2f", s.cpu().percentInstant())).append(",");
        sb.append("\"userMs\":").append(s.cpu().userTimeMs()).append(",");
        sb.append("\"systemMs\":").append(s.cpu().systemTimeMs());
        sb.append("},");
        sb.append("\"memory\":{");
        sb.append("\"rssBytes\":").append(s.memory().rssBytes()).append(",");
        sb.append("\"virtualBytes\":").append(s.memory().virtualBytes()).append(",");
        sb.append("\"percentOfTotal\":").append(String.format(Locale.US, "%.2f", s.memory().percentOfTotal()));
        sb.append("},");
        sb.append("\"io\":{");
        sb.append("\"readBytes\":").append(s.io().readBytes()).append(",");
        sb.append("\"writeBytes\":").append(s.io().writeBytes());
        sb.append("},");
        sb.append("\"threads\":").append(s.threadCount()).append(",");
        sb.append("\"fileDescriptors\":").append(s.openFileDescriptors());

        if (jvm != null && jvm.heap().usedBytes() > 0) {
            sb.append(",\"jvm\":{");
            sb.append("\"heap\":{");
            sb.append("\"usedBytes\":").append(jvm.heap().usedBytes()).append(",");
            sb.append("\"maxBytes\":").append(jvm.heap().maxBytes()).append(",");
            sb.append("\"percentUsed\":").append(String.format(Locale.US, "%.2f", jvm.heap().usedPercent()));
            sb.append("},");
            sb.append("\"threads\":{");
            sb.append("\"live\":").append(jvm.threads().liveThreads()).append(",");
            sb.append("\"daemon\":").append(jvm.threads().daemonThreads()).append(",");
            sb.append("\"deadlocked\":").append(jvm.threads().deadlockedCount());
            sb.append("},");
            sb.append("\"gc\":{");
            sb.append("\"collections\":").append(jvm.gc().totalCollections()).append(",");
            sb.append("\"totalTimeMs\":").append(jvm.gc().totalTimeMs());
            sb.append("}");
            sb.append("}");
        }

        sb.append("}");
        return sb.toString();
    }

    private String formatSampleJsonl(ProcessSample s) {
        return formatSampleJson(s, null);
    }

    private String formatMultipleSamplesJson(List<ProcessSample> samples) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < samples.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(formatSampleJson(samples.get(i), null));
        }
        sb.append("]");
        return sb.toString();
    }

    private String truncate(String s, int maxLen) {
        return config.truncate(s, maxLen);
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
