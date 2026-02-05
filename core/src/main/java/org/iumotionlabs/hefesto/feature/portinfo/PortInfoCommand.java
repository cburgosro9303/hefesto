package org.iumotionlabs.hefesto.feature.portinfo;

import org.iumotionlabs.hefesto.command.*;
import org.iumotionlabs.hefesto.core.context.ExecutionContext;
import org.iumotionlabs.hefesto.core.port.output.OutputPort;
import org.iumotionlabs.hefesto.feature.portinfo.model.*;
import org.iumotionlabs.hefesto.feature.portinfo.output.*;
import org.iumotionlabs.hefesto.feature.portinfo.service.*;
import org.iumotionlabs.hefesto.help.Documentation;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Command for querying port information and managing processes.
 * Enhanced with service identification, health checks, security analysis,
 * Docker integration, and multiple output formats.
 */
public final class PortInfoCommand implements Command {

    private static final Pattern RANGE_PATTERN = Pattern.compile("(\\d+)-(\\d+)");
    private static final Pattern WATCH_PATTERN = Pattern.compile("(\\d+)([smh]?)");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final CommandInfo INFO = new CommandInfo(
        "portinfo",
        "Muestra que proceso esta usando un puerto",
        "network"
    ).withAliases("port-who", "pw")
     .withDocumentation(Documentation.builder()
        .synopsis("portinfo [opciones] <puerto>")
        .description("Herramienta completa de diagnostico de red para DevOps y desarrolladores. " +
            "Identifica procesos en puertos, realiza health checks, analiza seguridad, " +
            "detecta contenedores Docker, y mas. Soporta Linux, macOS y Windows.")
        // Basic options
        .option("kill", "k", "Termina el proceso que usa el puerto (pide confirmacion)")
        .option("force", "f", "Termina sin confirmacion (requiere --kill)")
        .option("udp", "Buscar en UDP ademas de TCP")
        // Output format options
        .option("json", "j", "Salida en formato JSON enriquecido")
        .option("table", "Salida en formato tabla ASCII")
        .option("csv", "Salida en formato CSV (para exportar)")
        // Discovery options
        .option("all", "a", "Lista todos los puertos en estado LISTEN")
        .option("overview", "Vista completa de red con estadisticas")
        .option("range", "Buscar en un rango de puertos (ej: 8000-8100)")
        .option("listen", "Solo mostrar puertos en estado LISTEN")
        .option("pid", "p", "Buscar puertos por PID de proceso")
        .option("name", "n", "Filtrar por nombre de proceso")
        // Health check options
        .option("check", "c", "Realizar health check TCP")
        .option("http", "Health check HTTP (requiere --check)")
        .option("ssl", "Obtener info de certificado SSL")
        // Developer options
        .option("dev", "Mostrar puertos comunes de desarrollo")
        .option("free", "Verificar si puerto esta libre y sugerir alternativas")
        // Analysis options
        .option("process", "P", "Mostrar info extendida del proceso")
        .option("docker", "d", "Detectar y mostrar info de contenedores Docker")
        .option("security", "s", "Analisis de seguridad de puertos")
        // Monitoring
        .option("watch", "w", "Monitorear el puerto (ej: 2s, 5m)")
        // Examples
        .example("portinfo 8080", "Muestra quien usa el puerto 8080")
        .example("portinfo --all", "Lista todos los puertos LISTEN")
        .example("portinfo --overview", "Vista completa con estadisticas")
        .example("portinfo 8080 --check", "Health check TCP del puerto")
        .example("portinfo 8080 --check --http", "Health check HTTP")
        .example("portinfo 443 --ssl", "Info del certificado SSL")
        .example("portinfo --security", "Analisis de seguridad completo")
        .example("portinfo --docker", "Solo puertos de Docker")
        .example("portinfo --dev", "Puertos de desarrollo en uso")
        .example("portinfo --free 8080", "Verificar disponibilidad")
        .example("portinfo --name java", "Puertos del proceso java")
        .example("portinfo --all --table", "Tabla ASCII formateada")
        .example("portinfo --all --csv", "Export a CSV")
        .example("portinfo 8080 --kill", "Termina el proceso en el puerto")
        .example("portinfo --range 8000-8100", "Rango de puertos")
        .example("portinfo --pid 1234", "Puertos del proceso 1234")
        .example("portinfo 8080 --watch 2s", "Monitorea cada 2 segundos")
        .seeAlso("lsof, ss, netstat, docker port")
        .build());

    private final PortInfoService service;
    private final ServiceRegistry serviceRegistry;
    private final HealthCheckService healthCheckService;
    private final SecurityAnalysisService securityService;
    private final DockerService dockerService;
    private final ProcessEnrichmentService processService;

    public PortInfoCommand() {
        this.service = new PortInfoService();
        this.serviceRegistry = new ServiceRegistry();
        this.healthCheckService = new HealthCheckService();
        this.securityService = new SecurityAnalysisService(serviceRegistry);
        this.dockerService = new DockerService();
        this.processService = new ProcessEnrichmentService();
    }

    // For testing
    PortInfoCommand(PortInfoService service) {
        this.service = service;
        this.serviceRegistry = new ServiceRegistry();
        this.healthCheckService = new HealthCheckService();
        this.securityService = new SecurityAnalysisService(serviceRegistry);
        this.dockerService = new DockerService();
        this.processService = new ProcessEnrichmentService();
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

        // Determine output format
        OutputFormatter formatter = determineFormatter(parsed);
        boolean jsonOutput = formatter instanceof JsonFormatter;

        // Basic flags
        boolean kill = parsed.getBoolean("kill") || parsed.getBoolean("k");
        boolean force = parsed.getBoolean("force") || parsed.getBoolean("f");
        boolean udp = parsed.getBoolean("udp");
        boolean listenOnly = parsed.getBoolean("listen");

        // Mode: All listening ports
        if (parsed.getBoolean("all") || parsed.getBoolean("a")) {
            return handleAll(ctx, formatter, listenOnly);
        }

        // Mode: Network overview
        if (parsed.getBoolean("overview")) {
            return handleOverview(ctx, formatter);
        }

        // Mode: Security analysis
        if (parsed.getBoolean("security") || parsed.getBoolean("s")) {
            return handleSecurity(ctx, parsed, formatter);
        }

        // Mode: Docker ports
        if (parsed.getBoolean("docker") || parsed.getBoolean("d")) {
            return handleDocker(ctx, parsed, formatter);
        }

        // Mode: Development ports
        if (parsed.getBoolean("dev")) {
            return handleDevPorts(ctx, formatter);
        }

        // Mode: Free port check
        if (parsed.hasFlag("free")) {
            return handleFreeCheck(ctx, parsed, formatter);
        }

        // Mode: Filter by process name
        if (parsed.hasFlag("name") || parsed.hasFlag("n")) {
            return handleByName(ctx, parsed, formatter);
        }

        // Mode: Range
        if (parsed.hasFlag("range")) {
            return handleRange(ctx, parsed, formatter, listenOnly);
        }

        // Mode: PID
        if (parsed.hasFlag("pid") || parsed.hasFlag("p")) {
            return handlePid(ctx, parsed, formatter);
        }

        // Mode: Watch
        if (parsed.hasFlag("watch") || parsed.hasFlag("w")) {
            return handleWatch(ctx, parsed, udp);
        }

        // Mode: Single port
        if (!parsed.hasPositional()) {
            return CommandResult.failure("Se requiere un numero de puerto. Usa --help para ver opciones.");
        }

        String portStr = parsed.positional(0);
        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) {
                return CommandResult.failure("Puerto fuera de rango (1-65535): " + port);
            }
        } catch (NumberFormatException e) {
            return CommandResult.failure("Puerto invalido: " + portStr);
        }

        // Mode: Health check
        if (parsed.getBoolean("check") || parsed.getBoolean("c")) {
            return handleHealthCheck(ctx, port, parsed, formatter);
        }

        // Mode: SSL check
        if (parsed.getBoolean("ssl")) {
            return handleSslCheck(ctx, port, formatter);
        }

        // Mode: Single port with optional process info
        boolean showProcess = parsed.getBoolean("process") || parsed.getBoolean("P");
        boolean showDocker = parsed.getBoolean("docker") || parsed.getBoolean("d");

        return handleSinglePort(ctx, port, udp, formatter, kill, force, showProcess, showDocker);
    }

    private OutputFormatter determineFormatter(CommandParser.ParsedArgs parsed) {
        if (parsed.getBoolean("json") || parsed.getBoolean("j")) {
            return new JsonFormatter();
        }
        if (parsed.getBoolean("csv")) {
            return new CsvFormatter();
        }
        if (parsed.getBoolean("table")) {
            return new TableFormatter(true);
        }
        // Default: use TableFormatter for list views, simple output for single port
        return new TableFormatter(true);
    }

    private CommandResult handleAll(ExecutionContext ctx, OutputFormatter formatter, boolean listenOnly) {
        List<PortBinding> bindings = service.findAllListening();

        if (bindings.isEmpty()) {
            ctx.info("No hay puertos en estado LISTEN");
            return CommandResult.success();
        }

        List<EnrichedPortBinding> enriched = service.enrichBindings(bindings);
        String output = formatter.format(enriched);
        ctx.output().println(output);

        return CommandResult.success();
    }

    private CommandResult handleOverview(ExecutionContext ctx, OutputFormatter formatter) {
        NetworkOverview overview = service.getNetworkOverview();
        String output = formatter.formatOverview(overview);
        ctx.output().println(output);
        return CommandResult.success();
    }

    private CommandResult handleSecurity(ExecutionContext ctx, CommandParser.ParsedArgs parsed, OutputFormatter formatter) {
        List<PortBinding> bindings;

        if (parsed.hasPositional()) {
            // Analyze specific port
            try {
                int port = Integer.parseInt(parsed.positional(0));
                bindings = service.findByPort(port, true, true);
            } catch (NumberFormatException e) {
                return CommandResult.failure("Puerto invalido: " + parsed.positional(0));
            }
        } else {
            // Analyze all listening ports
            bindings = service.findAllListening();
        }

        if (bindings.isEmpty()) {
            ctx.info("No hay puertos para analizar");
            return CommandResult.success();
        }

        SecurityReport report = securityService.analyze(bindings);
        String output = formatter.formatSecurityReport(report);
        ctx.output().println(output);

        return CommandResult.success();
    }

    private CommandResult handleDocker(ExecutionContext ctx, CommandParser.ParsedArgs parsed, OutputFormatter formatter) {
        if (!dockerService.isDockerAvailable()) {
            return CommandResult.failure("Docker no esta disponible o no se puede conectar");
        }

        List<DockerInfo> containers = dockerService.listRunningContainers();

        if (containers.isEmpty()) {
            ctx.info("No hay contenedores Docker en ejecucion");
            return CommandResult.success();
        }

        ctx.output().header("DOCKER CONTAINERS");
        ctx.output().println();

        for (DockerInfo container : containers) {
            ctx.output().println(OutputPort.GREEN + container.containerName() + OutputPort.RESET +
                " (" + container.shortId() + ") - " + container.image());
            ctx.output().println("  Status: " + container.status());

            if (!container.portMappings().isEmpty()) {
                ctx.output().println("  Ports: " + container.portMappingsFormatted());
            }
            ctx.output().println();
        }

        return CommandResult.success();
    }

    private CommandResult handleDevPorts(ExecutionContext ctx, OutputFormatter formatter) {
        List<PortBinding> devBindings = service.findDevPorts();

        if (devBindings.isEmpty()) {
            ctx.info("No hay puertos de desarrollo en uso");
            return CommandResult.success();
        }

        ctx.output().header("PUERTOS DE DESARROLLO EN USO");
        ctx.output().println();

        List<EnrichedPortBinding> enriched = service.enrichBindings(devBindings);
        String output = formatter.format(enriched);
        ctx.output().println(output);

        return CommandResult.success();
    }

    private CommandResult handleFreeCheck(ExecutionContext ctx, CommandParser.ParsedArgs parsed, OutputFormatter formatter) {
        String portStr = parsed.getFlag("free", "");
        if (portStr.isEmpty() && parsed.hasPositional()) {
            portStr = parsed.positional(0);
        }

        if (portStr.isEmpty()) {
            return CommandResult.failure("Se requiere un puerto para verificar");
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            return CommandResult.failure("Puerto invalido: " + portStr);
        }

        boolean isFree = service.isPortFree(port);

        if (isFree) {
            ctx.success("Puerto " + port + " esta LIBRE y disponible");
        } else {
            ctx.output().warning("Puerto " + port + " esta EN USO");

            // Show what's using it
            List<PortBinding> bindings = service.findByPort(port);
            for (PortBinding b : bindings) {
                ctx.output().println("  -> " + b.processName() + " (pid " + b.pid() + ")");
            }

            // Suggest alternatives
            List<Integer> alternatives = service.findAlternatives(port, 5);
            if (!alternatives.isEmpty()) {
                ctx.output().println();
                ctx.info("Alternativas disponibles:");
                for (Integer alt : alternatives) {
                    ctx.output().println("  " + alt);
                }
            }
        }

        return CommandResult.success();
    }

    private CommandResult handleByName(ExecutionContext ctx, CommandParser.ParsedArgs parsed, OutputFormatter formatter) {
        String name = parsed.getFlag("name", parsed.getFlag("n", ""));
        if (name.isEmpty() && parsed.hasPositional()) {
            name = parsed.positional(0);
        }

        if (name.isEmpty()) {
            return CommandResult.failure("Se requiere un nombre de proceso");
        }

        List<PortBinding> bindings = service.findByProcessName(name);

        if (bindings.isEmpty()) {
            ctx.info("No se encontraron puertos para el proceso: " + name);
            return CommandResult.success();
        }

        ctx.output().header("Puertos del proceso '" + name + "':");
        ctx.output().println();

        List<EnrichedPortBinding> enriched = service.enrichBindings(bindings);
        String output = formatter.format(enriched);
        ctx.output().println(output);

        return CommandResult.success();
    }

    private CommandResult handleHealthCheck(ExecutionContext ctx, int port, CommandParser.ParsedArgs parsed,
                                            OutputFormatter formatter) {
        boolean httpCheck = parsed.getBoolean("http");
        String host = parsed.getFlag("host", "127.0.0.1");

        HealthCheckResult result;

        if (httpCheck) {
            String path = parsed.getFlag("path", "/");
            result = healthCheckService.checkHttp(host, port, path, false);
        } else {
            result = healthCheckService.checkTcp(host, port);
        }

        String output = formatter.formatHealthCheck(result);
        ctx.output().println(output);

        return result.isHealthy() ? CommandResult.success() : CommandResult.failure("Health check failed");
    }

    private CommandResult handleSslCheck(ExecutionContext ctx, int port, OutputFormatter formatter) {
        String host = "127.0.0.1";

        HealthCheckResult result = healthCheckService.checkSsl(host, port);
        String output = formatter.formatHealthCheck(result);
        ctx.output().println(output);

        return result.isHealthy() ? CommandResult.success() : CommandResult.failure("SSL check failed");
    }

    private CommandResult handleSinglePort(ExecutionContext ctx, int port, boolean udp,
                                           OutputFormatter formatter, boolean kill, boolean force,
                                           boolean showProcess, boolean showDocker) {
        List<PortBinding> bindings = service.findByPort(port, true, udp);

        if (bindings.isEmpty()) {
            if (formatter instanceof JsonFormatter) {
                ctx.output().println("[]");
            } else {
                ctx.info("Puerto " + port + " esta libre");
            }
            return CommandResult.success();
        }

        // Enrich bindings
        List<EnrichedPortBinding> enriched = service.enrichBindings(bindings);

        // Add process info if requested
        if (showProcess) {
            enriched = enriched.stream().map(eb -> {
                Optional<ProcessInfo> procInfo = processService.getProcessInfo(eb.pid());
                return procInfo.map(eb::withProcessInfo).orElse(eb);
            }).toList();
        }

        // Add Docker info if requested
        if (showDocker && dockerService.isDockerAvailable()) {
            enriched = enriched.stream().map(eb -> {
                Optional<DockerInfo> dockInfo = dockerService.getContainerByPid(eb.pid());
                return dockInfo.map(eb::withDockerInfo).orElse(eb);
            }).toList();
        }

        // Output
        if (formatter instanceof JsonFormatter) {
            ctx.output().println(formatter.format(enriched));
        } else {
            for (EnrichedPortBinding eb : enriched) {
                printEnrichedBinding(ctx, eb, showProcess, showDocker);
            }
        }

        // Handle kill request
        if (kill && !bindings.isEmpty()) {
            return handleKill(ctx, bindings, force);
        }

        return CommandResult.success();
    }

    private void printEnrichedBinding(ExecutionContext ctx, EnrichedPortBinding eb,
                                     boolean showProcess, boolean showDocker) {
        // Basic info
        ctx.output().print(OutputPort.CYAN + eb.protocol() + OutputPort.RESET + " ");
        ctx.output().print(eb.localAddress() + ":" + OutputPort.BOLD + eb.port() + OutputPort.RESET + " ");
        ctx.output().print(formatState(eb.state()) + " ");
        ctx.output().print("pid=" + eb.pid() + " ");
        ctx.output().print(OutputPort.GREEN + eb.processName() + OutputPort.RESET);

        // Service tag
        eb.serviceInfoOpt().ifPresent(service -> {
            ctx.output().print(" " + OutputPort.PURPLE + service.toTag() + OutputPort.RESET);
        });

        ctx.output().println();

        // Extended process info
        if (showProcess) {
            eb.processInfoOpt().ifPresent(proc -> {
                ctx.output().println("  Memory: " + proc.memoryRssFormatted() + " RSS, " + proc.memoryVirtualFormatted() + " Virtual");
                ctx.output().println("  CPU Time: " + proc.cpuTimeFormatted() + ", Threads: " + proc.threadCount());
                if (!proc.workingDirectory().isEmpty()) {
                    ctx.output().println("  CWD: " + proc.workingDirectory());
                }
            });
        }

        // Docker info
        if (showDocker) {
            eb.dockerInfoOpt().ifPresent(docker -> {
                ctx.output().println("  Docker: " + docker.containerName() + " (" + docker.shortId() + ")");
                ctx.output().println("  Image: " + docker.image());
            });
        }
    }

    private CommandResult handleRange(ExecutionContext ctx, CommandParser.ParsedArgs parsed,
                                      OutputFormatter formatter, boolean listenOnly) {
        String rangeStr = parsed.getFlag("range", "");
        Matcher m = RANGE_PATTERN.matcher(rangeStr);

        if (!m.matches()) {
            return CommandResult.failure("Rango invalido. Usa el formato: 8000-8100");
        }

        int from = Integer.parseInt(m.group(1));
        int to = Integer.parseInt(m.group(2));

        if (from > to || from < 1 || to > 65535) {
            return CommandResult.failure("Rango invalido: " + from + "-" + to);
        }

        List<PortBinding> bindings = service.findInRange(from, to, listenOnly);

        if (bindings.isEmpty()) {
            ctx.info("No hay puertos ocupados en el rango " + from + "-" + to);
            return CommandResult.success();
        }

        ctx.output().header("Puertos en rango " + from + "-" + to + ":");
        ctx.output().println();

        List<EnrichedPortBinding> enriched = service.enrichBindings(bindings);
        String output = formatter.format(enriched);
        ctx.output().println(output);

        return CommandResult.success();
    }

    private CommandResult handlePid(ExecutionContext ctx, CommandParser.ParsedArgs parsed, OutputFormatter formatter) {
        String pidStr = parsed.getFlag("pid", parsed.getFlag("p", ""));

        if (pidStr.isEmpty() && parsed.hasPositional()) {
            pidStr = parsed.positional(0);
        }

        if (pidStr.isEmpty()) {
            return CommandResult.failure("Se requiere un PID");
        }

        long pid;
        try {
            pid = Long.parseLong(pidStr);
        } catch (NumberFormatException e) {
            return CommandResult.failure("PID invalido: " + pidStr);
        }

        List<PortBinding> bindings = service.findByPid(pid);

        if (bindings.isEmpty()) {
            ctx.info("No se encontraron puertos para el PID " + pid);
            return CommandResult.success();
        }

        ctx.output().header("Puertos del proceso " + pid + ":");
        ctx.output().println();

        List<EnrichedPortBinding> enriched = service.enrichBindings(bindings);
        String output = formatter.format(enriched);
        ctx.output().println(output);

        return CommandResult.success();
    }

    private CommandResult handleWatch(ExecutionContext ctx, CommandParser.ParsedArgs parsed, boolean udp) {
        String watchStr = parsed.getFlag("watch", parsed.getFlag("w", "2s"));
        long intervalMs = parseWatchInterval(watchStr);

        if (!parsed.hasPositional()) {
            return CommandResult.failure("Se requiere un puerto para monitorear");
        }

        int port;
        try {
            port = Integer.parseInt(parsed.positional(0));
        } catch (NumberFormatException e) {
            return CommandResult.failure("Puerto invalido");
        }

        ctx.info("Monitoreando puerto " + port + " cada " + watchStr + " (Ctrl+C para detener)");
        ctx.output().println();

        try {
            while (true) {
                List<PortBinding> bindings = service.findByPort(port, true, udp);
                String timestamp = "[" + LocalDateTime.now().format(TIME_FORMAT) + "] ";

                if (bindings.isEmpty()) {
                    ctx.output().println(OutputPort.DIM + timestamp + "(puerto libre)" + OutputPort.RESET);
                } else {
                    for (PortBinding b : bindings) {
                        EnrichedPortBinding eb = service.enrichBinding(b);
                        ctx.output().println(timestamp + eb.toCompactWithService());
                    }
                }

                Thread.sleep(intervalMs);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        ctx.output().println();
        return CommandResult.success();
    }

    private CommandResult handleKill(ExecutionContext ctx, List<PortBinding> bindings, boolean force) {
        // Get unique PIDs
        var pids = bindings.stream()
            .map(PortBinding::pid)
            .filter(pid -> pid > 0)
            .distinct()
            .toList();

        if (pids.isEmpty()) {
            return CommandResult.failure("No se pudo determinar el PID del proceso");
        }

        for (long pid : pids) {
            String processName = bindings.stream()
                .filter(b -> b.pid() == pid)
                .findFirst()
                .map(PortBinding::processName)
                .orElse("desconocido");

            if (!force) {
                ctx.output().println();
                Optional<String> confirm = ctx.readLine(
                    OutputPort.YELLOW + "Terminar proceso " + processName + " (PID " + pid + ")? [s/N]: " + OutputPort.RESET
                );

                if (confirm.isEmpty() || !confirm.get().toLowerCase().startsWith("s")) {
                    ctx.info("Operacion cancelada");
                    continue;
                }
            }

            if (service.killProcess(pid, force)) {
                ctx.success("Proceso " + pid + " terminado");
            } else {
                ctx.error("No se pudo terminar el proceso " + pid + ". Puede requerir permisos elevados.");
            }
        }

        return CommandResult.success();
    }

    private String formatState(String state) {
        return switch (state) {
            case "LISTEN" -> OutputPort.GREEN + state + OutputPort.RESET;
            case "ESTABLISHED", "ESTAB" -> OutputPort.BLUE + state + OutputPort.RESET;
            case "TIME_WAIT", "CLOSE_WAIT" -> OutputPort.YELLOW + state + OutputPort.RESET;
            default -> state;
        };
    }

    private long parseWatchInterval(String s) {
        Matcher m = WATCH_PATTERN.matcher(s);
        if (!m.matches()) {
            return 2000; // Default 2 seconds
        }

        int value = Integer.parseInt(m.group(1));
        String unit = m.group(2);

        return switch (unit) {
            case "m" -> TimeUnit.MINUTES.toMillis(value);
            case "h" -> TimeUnit.HOURS.toMillis(value);
            default -> TimeUnit.SECONDS.toMillis(value);
        };
    }
}
