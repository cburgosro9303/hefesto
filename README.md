# Hefesto

**Hefesto** es una suite de utilidades para desarrolladores y DevOps, escrita en Java 25 con arquitectura modular. Incluye una **CLI de consola** y una **aplicación de escritorio JavaFX**, proporcionando herramientas de diagnóstico de red, monitoreo de procesos, codificación y más.

## Características Principales

- **Arquitectura Hexagonal** - Diseño limpio con puertos y adaptadores
- **Java 25** - Aprovecha las últimas características del lenguaje (records, sealed classes, pattern matching)
- **Sistema de Módulos JPMS** - Encapsulación robusta con `module-info.java`
- **Multi-módulo Gradle** - Tres módulos: `core/`, `desktop-api/`, `desktop/`
- **CLI + Desktop** - Ejecución por línea de comandos o interfaz gráfica JavaFX
- **Multi-plataforma** - Soporte para Linux, macOS y Windows
- **Extensible** - Sistema de plugins con ServiceLoader SPI

## Tabla de Contenidos

- [Instalación](#instalación)
- [Aplicación Desktop](#aplicación-desktop)
  - [Process Explorer](#process-explorer)
  - [Process Monitor](#process-monitor)
  - [Network Explorer](#network-explorer)
  - [Port Detail](#port-detail)
  - [Utility Tools](#utility-tools)
- [CLI - Comandos Disponibles](#cli---comandos-disponibles)
  - [PortInfo](#portinfo---diagnóstico-de-red)
  - [ProcWatch](#procwatch---monitor-de-procesos)
  - [Base64](#base64---codificación)
  - [JSON](#json---formateo-y-validación)
  - [Echo](#echo---manipulación-de-texto)
- [Arquitectura](#arquitectura)
- [Desarrollo](#desarrollo)
- [Licencia](#licencia)

## Instalación

### Requisitos

- **Java 25** o superior
- **Gradle 9.x** (incluido wrapper)

### Compilar desde Fuente

```bash
# Clonar el repositorio
git clone https://github.com/iumotionlabs/hefesto.git
cd hefesto

# Compilar todo
./gradlew build

# Compilar solo CLI
./gradlew :core:build

# Compilar solo Desktop
./gradlew :desktop:build
```

### Ejecutar CLI

```bash
# Modo CLI directo
./gradlew :core:run --args="<comando> [opciones]"

# Modo interactivo
./gradlew :core:run
```

### Ejecutar Desktop

```bash
./gradlew :desktop:run
```

## Aplicación Desktop

La aplicación de escritorio JavaFX proporciona una interfaz gráfica moderna para todas las capacidades del core. Usa el patrón **MVVM**, temas claro/oscuro, internacionalización (EN/ES) y concurrencia con virtual threads.

### Process Explorer

Vista principal del módulo System. Muestra todos los procesos del sistema en tiempo real.

**Funcionalidades:**
- **Panel de KPIs** - Total de procesos, CPU del sistema (%), Memoria del sistema (%)
- **Gráficas de tendencia** - CPU y Memoria del sistema con historial de 60 puntos
- **Tabla de procesos** - PID, Nombre, Estado (badge), CPU%, Mem%, RSS, Threads, Usuario, badge Java
- **Búsqueda** - Filtrado en tiempo real por nombre, PID o comando
- **Ordenamiento** - Por CPU, Memoria, PID o Nombre
- **Auto-refresh** - Actualización automática cada 3 segundos
- **Menú contextual** - "Monitor Process" (drill-down), "Kill Process", "Copy PID"
- **Doble clic** - Abre Process Monitor con el PID seleccionado
- **Exportar CSV** - Exporta la tabla actual a archivo CSV

### Process Monitor

Vista detallada para monitoreo continuo de un proceso individual.

**Funcionalidades:**
- **Búsqueda por PID** - Ingreso directo del PID
- **Búsqueda por nombre** - Busca procesos por nombre y monitorea el primero encontrado
- **Gauges en tiempo real** - CPU y Memoria como indicadores circulares
- **KPI Cards** - PID, Proceso, Threads, Uptime, Lectura I/O, Escritura I/O
- **Badge de estado** - Running (verde), Zombie (rojo), Sleeping (azul), etc.
- **Gráficas temporales** - CPU% y Memory% con historial de 60 muestras
- **Kill Process** - Botón para terminar el proceso con confirmación
- **Drill-down** - Recibe PID desde Process Explorer y auto-inicia monitoreo
- **Panel de alertas** - Colapsable, muestra alertas configuradas

### Network Explorer

Vista principal del módulo Network. Muestra todos los puertos en escucha con panel de detalle.

**Funcionalidades:**
- **Panel de KPIs** - Total Listening, TCP Ports, UDP Ports
- **Tabla de puertos** - Puerto, Protocolo, Dirección, Estado, Proceso, PID, Seguridad (badge)
- **Búsqueda con debounce** - Filtro por puerto, proceso o dirección
- **Filtro de protocolo** - ALL, TCP, UDP
- **Menú contextual** - "Kill Process" con confirmación, "Copy PID"
- **Exportar CSV** - Exporta la tabla filtrada a archivo CSV
- **Panel de detalle** - Seleccionar un puerto muestra PortDetail a la derecha

### Port Detail

Panel de detalle que se muestra al seleccionar un puerto en Network Explorer.

**Pestaña Info:**
- Detalles del binding: puerto, protocolo, dirección, estado, proceso, PID, usuario
- Información de servicio (si reconocido): nombre, descripción
- Información de proceso: comando, usuario
- **Botón Kill Process** - Termina el proceso con confirmación

**Pestaña Health:**
- Botón "Run Health Check" con indicador de progreso
- Estado general: Healthy/Unhealthy, tiempo de respuesta, detalles
- **HTTP Info** (cuando disponible): Status code badge, Content-Type, Content-Length, Response Time
- **SSL Info** (cuando disponible): Issuer, Subject, Valid From/To, Days Until Expiry (badge con warning si < 30 días), Protocol, Cipher Suite, badge Valid/Invalid

**Pestaña Security:**
- Security flags con badges de severidad (CRITICAL/HIGH/WARNING/INFO)
- Título y categoría por cada flag
- Descripción detallada
- **Recomendación** - Texto en itálica con la recomendación de seguridad

**Pestaña Docker** (condicional):
- Solo aparece si Docker está disponible y el proceso está en un contenedor
- Container Name, Image, Status (badge Running/Stopped), Container ID (corto)
- Port Mappings - Lista de mapeos host:container/protocol

### Utility Tools

Herramientas de utilidad accesibles desde la sidebar bajo "Utilities".

**Base64:**
- TextArea de entrada + TextArea de salida (read-only)
- Botones: Encode, Decode
- Opciones: URL-safe, MIME
- Botones: Clear, Copy
- Manejo de errores para entrada inválida

**JSON:**
- TextArea de entrada + TextArea de salida (monoespaciada, read-only)
- Botones: Format (pretty-print), Compact (minify), Validate
- Badge de validación: "Valid JSON" (verde) / "Invalid JSON" (rojo)
- Botones: Clear, Copy
- Usa Jackson 3.x para procesamiento

## CLI - Comandos Disponibles

### PortInfo - Diagnóstico de Red

Herramienta completa de diagnóstico de red para DevOps y desarrolladores. Identifica procesos en puertos, realiza health checks, analiza seguridad, detecta contenedores Docker, y más.

#### Opciones Básicas

| Opción | Alias | Descripción |
|--------|-------|-------------|
| `--kill` | `-k` | Termina el proceso que usa el puerto |
| `--force` | `-f` | Termina sin confirmación |
| `--udp` | | Buscar en UDP además de TCP |

#### Descubrimiento de Puertos

| Opción | Alias | Descripción |
|--------|-------|-------------|
| `--all` | `-a` | Lista todos los puertos en estado LISTEN |
| `--overview` | | Vista completa de red con estadísticas |
| `--range` | | Buscar en rango de puertos (ej: 8000-8100) |
| `--listen` | | Solo mostrar puertos en estado LISTEN |
| `--pid` | `-p` | Buscar puertos por PID de proceso |
| `--name` | `-n` | Filtrar por nombre de proceso |

#### Health Checks

| Opción | Alias | Descripción |
|--------|-------|-------------|
| `--check` | `-c` | Realizar health check TCP |
| `--http` | | Health check HTTP |
| `--ssl` | | Obtener información de certificado SSL |

#### Análisis y Seguridad

| Opción | Alias | Descripción |
|--------|-------|-------------|
| `--security` | `-s` | Análisis de seguridad de puertos |
| `--process` | `-P` | Información extendida del proceso |
| `--docker` | `-d` | Detectar contenedores Docker |
| `--dev` | | Mostrar puertos comunes de desarrollo |
| `--free` | | Verificar si puerto está libre |

#### Formatos de Salida

| Opción | Alias | Descripción |
|--------|-------|-------------|
| `--json` | `-j` | Salida en formato JSON enriquecido |
| `--table` | | Salida en formato tabla ASCII |
| `--csv` | | Salida en formato CSV |

#### Ejemplos de Uso

```bash
# Ver qué proceso usa el puerto 8080
portinfo 8080

# Listar todos los puertos en escucha
portinfo --all

# Vista completa de red con estadísticas
portinfo --overview

# Health check TCP
portinfo 8080 --check

# Health check HTTP
portinfo 8080 --check --http

# Información de certificado SSL
portinfo 443 --ssl

# Análisis de seguridad
portinfo --security

# Puertos de desarrollo en uso
portinfo --dev

# Verificar disponibilidad y alternativas
portinfo --free 8080

# Filtrar por proceso
portinfo --name java

# Exportar a CSV
portinfo --all --csv > ports.csv

# Salida JSON
portinfo --all --json

# Buscar en rango
portinfo --range 8000-8100

# Puertos de un PID específico
portinfo --pid 1234

# Monitorear puerto cada 2 segundos
portinfo 8080 --watch 2s

# Terminar proceso en puerto
portinfo 8080 --kill
```

#### Salida de Ejemplo: Overview

```
NETWORK OVERVIEW
================

STATISTICS
  Listening:     23 ports
  Established:   47 connections
  TCP: 68 | UDP: 2
  Exposed (0.0.0.0): 5 | Local (127.0.0.1): 18

EXPOSED PORTS (Network Accessible)
  TCP :8080   java         [HTTP-Alt]     pid=1234
  TCP :3306   mysqld       [MySQL]        pid=5678

PROCESSES
  java (pid 1234) - developer
    :8080  LISTEN  0.0.0.0    [HTTP-Alt]
    :8443  LISTEN  127.0.0.1  [HTTPS-Alt]
```

#### Salida de Ejemplo: Security Analysis

```
SECURITY ANALYSIS
=================

CRITICAL (2)
  :3306 mysqld [MySQL] - Database exposed + running as root
  :5005 java [Debug]   - Debug port exposed to network

WARNING (3)
  :8080 java [HTTP-Alt] - Exposed to network (0.0.0.0)

SUMMARY: 2 critical, 3 warnings, 5 info
```

#### Servicios Reconocidos

PortInfo reconoce automáticamente más de 40 servicios comunes:

| Categoría | Servicios |
|-----------|-----------|
| **DATABASE** | MySQL, PostgreSQL, MongoDB, Redis, Oracle, MSSQL, Cassandra, CouchDB |
| **WEB** | HTTP (80), HTTPS (443), HTTP-Alt (8080, 8443) |
| **MESSAGING** | Kafka, RabbitMQ, ActiveMQ, NATS, MQTT, Zookeeper |
| **CACHE** | Redis, Memcached |
| **SEARCH** | Elasticsearch, Solr, Milvus |
| **MONITORING** | Prometheus, Grafana, Jaeger, Zipkin, InfluxDB |
| **DEV** | Java Debug (5005), Node Debug (9229), Vite, Angular |
| **INFRA** | SSH, Docker, Kubernetes, etcd, Consul, Vault |

---

### ProcWatch - Monitor de Procesos

Monitor avanzado de procesos del sistema. Muestra métricas en tiempo real de CPU, memoria, threads, descriptores de archivo e I/O. Soporta alertas configurables con DSL, monitoreo continuo, salida JSON/JSONL, y métricas JVM vía JMX.

#### Selección de Objetivo

| Opción | Alias | Descripción |
|--------|-------|-------------|
| `--pid` | `-p` | ID del proceso a monitorear |
| `--name` | `-n` | Nombre del proceso (búsqueda parcial) |
| `--match` | `-m` | Filtro adicional en línea de comandos (con --name) |
| `--top` | `-t` | Modo top: `cpu` o `memory` |

#### Opciones de Monitoreo

| Opción | Alias | Descripción |
|--------|-------|-------------|
| `--interval` | `-i` | Intervalo de muestreo (ej: 1s, 500ms, 5m). Default: 1s |
| `--count` | `-c` | Número de muestras (default: infinito) |
| `--limit` | `-l` | Límite de procesos en modo top (default: 10) |
| `--once` | | Muestra una sola vez y termina |

#### Alertas con DSL

| Opción | Alias | Descripción |
|--------|-------|-------------|
| `--alert` | `-a` | Regla de alerta DSL (puede repetirse) |
| `--dump-on-breach` | | Ejecutar dump al violar alerta: jstack, jmap, lsof, pstack |
| `--list-alerts` | | Muestra sintaxis de alertas disponibles |

#### Métricas JVM

| Opción | Alias | Descripción |
|--------|-------|-------------|
| `--jvm` | | Incluir métricas JVM vía JMX (heap, GC, threads) |
| `--jmx-port` | | Puerto JMX remoto (default: auto-detect) |

#### Formatos de Salida

| Opción | Alias | Descripción |
|--------|-------|-------------|
| `--json` | `-j` | Salida en formato JSON |
| `--jsonl` | | Salida en formato JSON Lines (una línea por muestra) |
| `--compact` | | Formato compacto de una línea |
| `--quiet` | `-q` | Solo mostrar alertas |

#### Ejemplos de Uso

```bash
# Monitorear proceso por PID
proc-watch --pid 4123

# Muestra métricas una sola vez
proc-watch --pid 4123 --once

# Monitorear procesos por nombre
proc-watch --name java

# Filtrar por contenido en línea de comandos
proc-watch --name java --match 'myservice'

# Top 10 procesos por CPU en tiempo real
proc-watch --top cpu --limit 10

# Top por memoria cada 5 segundos
proc-watch --top memory --interval 5s

# Salida JSON
proc-watch --pid 4123 --json

# 100 muestras en formato JSON Lines
proc-watch --pid 4123 --jsonl --count 100

# Alerta si CPU > 80%
proc-watch --pid 4123 --alert 'cpu>80%'

# Alerta si RAM > 1.5GB
proc-watch --pid 4123 --alert 'rss>1.5GB'

# Alerta sostenida por 30 segundos
proc-watch --pid 4123 --alert 'cpu>80% for 30s'

# Múltiples alertas
proc-watch --pid 4123 --alert 'cpu>80%' --alert 'rss>2GB' --alert 'threads>100'

# Ejecutar jstack al violar alerta
proc-watch --pid 4123 --alert 'cpu>90%' --dump-on-breach jstack

# Incluir métricas JVM
proc-watch --pid 4123 --jvm

# Ver sintaxis de alertas
proc-watch --list-alerts
```

#### Salida de Ejemplo: Detalle de Proceso

```
PROCESO: java (PID: 4123)
════════════════════════════════════════════════════════════
  Usuario:    developer
  Estado:     Running
  Comando:    /usr/bin/java -jar myapp.jar

  CPU:
    Actual:   45.2%
    User:     12340 ms
    System:   3210 ms

  MEMORIA:
    RSS:      1.2 GB
    Virtual:  4.5 GB
    % Total:  15.3%

  I/O:
    Read:     234.5 MB
    Write:    56.7 MB

  RECURSOS:
    Threads:  87
    FDs:      234

  JVM (via JMX):
    Heap:     512 MB / 2 GB (25.6%)
    Non-Heap: 128 MB
    Threads:  87 (23 daemon)
    GC:       1234 collections, avg 2.3ms
    Uptime:   2d 5h 32m
```

#### Salida de Ejemplo: Top CPU

```
TOP PROCESOS POR CPU - 14:32:15
──────────────────────────────────────────────────────────────────────────────────────────
PID      NOMBRE               CPU%     RSS        VIRTUAL    THREADS  COMANDO
──────────────────────────────────────────────────────────────────────────────────────────
4123     java                   45.2% 1.2 GB     4.5 GB          87 java -jar myapp.jar
5678     node                   23.1% 512 MB     2.1 GB          12 node server.js
9012     python                 15.3% 256 MB     1.8 GB           8 python app.py
```

#### Sintaxis de Alertas DSL

El sistema de alertas usa una sintaxis DSL (Domain Specific Language) intuitiva:

**Formato básico:**
```
metric OPERADOR threshold[UNIDAD]
```

**Con ventana de tiempo:**
```
metric OPERADOR threshold[UNIDAD] CONDICION DURACION
```

**Métricas disponibles:**

| Métrica | Aliases | Descripción |
|---------|---------|-------------|
| `cpu` | `cpu%` | Porcentaje de CPU |
| `rss` | `mem`, `memory` | Memoria residente (RAM) |
| `virtual` | `vsz`, `virt` | Memoria virtual |
| `threads` | `thread` | Número de threads |
| `fd` | `fds`, `files` | Descriptores de archivo |
| `read` | `read_bytes` | Bytes leídos |
| `write` | `write_bytes` | Bytes escritos |

**Operadores:** `>` `>=` `<` `<=` `==` `!=`

**Unidades:**
- `%` - Porcentaje (para CPU)
- `B`, `KB`, `MB`, `GB` - Bytes (para memoria/IO)
- Sin unidad - Número raw (para threads, fd)

**Condiciones de ventana:**
- `for` - La condición debe mantenerse durante la duración
- `increasing` - El valor debe estar incrementando durante la duración
- `decreasing` - El valor debe estar decrementando durante la duración

**Duración:** `Ns` (segundos), `Nm` (minutos), `Nh` (horas)

**Ejemplos de alertas:**
```bash
cpu>80%                    # CPU mayor a 80%
rss>1.5GB                  # RAM mayor a 1.5 GB
cpu>80% for 30s            # CPU > 80% sostenido por 30 segundos
threads>100                # Más de 100 threads
fd>1000                    # Más de 1000 file descriptors
rss>2GB increasing 5m      # RAM incrementando por 5 minutos
```

---

### Base64 - Codificación

Codifica y decodifica texto en formato Base64.

#### Opciones

| Opción | Alias | Descripción |
|--------|-------|-------------|
| `--decode` | `-d` | Decodifica en lugar de codificar |
| `--url` | | Usa codificación URL-safe |
| `--mime` | | Usa codificación MIME |

#### Ejemplos

```bash
# Codificar texto
base64 'Hola Mundo'
# Salida: SG9sYSBNdW5kbw==

# Decodificar texto
base64 -d 'SG9sYSBNdW5kbw=='
# Salida: Hola Mundo

# Codificación URL-safe
base64 --url 'data+with/special'
```

---

### JSON - Formateo y Validación

Formatea y valida estructuras JSON.

#### Opciones

| Opción | Alias | Descripción |
|--------|-------|-------------|
| `--validate` | `-v` | Solo valida sin formatear |
| `--compact` | `-c` | Salida compacta (minificada) |
| `--file` | `-f` | Lee desde un archivo |

#### Ejemplos

```bash
# Formatear JSON
json '{"name":"test","value":123}'
# Salida:
# {
#   "name" : "test",
#   "value" : 123
# }

# Validar JSON
json -v '{"invalid'
# Error: JSON invalido

# Formatear desde archivo
json -f data.json

# Compactar JSON
json -c '{ "a": 1, "b": 2 }'
# Salida: {"a":1,"b":2}
```

---

### Echo - Manipulación de Texto

Muestra texto con transformaciones opcionales.

#### Opciones

| Opción | Alias | Descripción |
|--------|-------|-------------|
| `--uppercase` | `-u` | Convierte a mayúsculas |
| `--lowercase` | `-l` | Convierte a minúsculas |
| `--repeat` | `-r` | Número de veces a repetir |
| `--separator` | `-s` | Separador entre repeticiones |

#### Ejemplos

```bash
# Mostrar texto
echo Hola Mundo
# Salida: Hola Mundo

# Convertir a mayúsculas
echo -u 'hola mundo'
# Salida: HOLA MUNDO

# Repetir con separador
echo -r 3 -s '|' test
# Salida: test|test|test
```

---

## Arquitectura

Hefesto sigue una **arquitectura hexagonal** (Ports & Adapters) con principios de Domain-Driven Design. El proyecto está organizado en tres módulos Gradle:

### Estructura Multi-Módulo

```
hefesto/
├── core/                              # Lógica de negocio + CLI
│   └── src/main/java/
│       └── org/iumotionlabs/hefesto/
│           ├── HefestoApplication.java
│           ├── core/                  # Puertos, adaptadores, config
│           ├── command/               # Sistema de comandos
│           ├── help/                  # Sistema de ayuda
│           ├── menu/                  # Shell interactivo
│           └── feature/
│               ├── portinfo/          # Diagnóstico de red
│               │   ├── model/         # PortBinding, HealthCheckResult, SecurityFlag, DockerInfo...
│               │   ├── service/       # PortInfoService, HealthCheckService, DockerService...
│               │   ├── parser/        # LinuxPortParser, MacOsPortParser, WindowsPortParser
│               │   └── output/        # TableFormatter, CsvFormatter, JsonFormatter
│               ├── procwatch/         # Monitor de procesos
│               │   ├── model/         # ProcessSample, AlertRule, JvmMetrics
│               │   ├── service/       # ProcessMonitorService, AlertEngine, JmxService
│               │   └── sampler/       # LinuxProcessSampler, MacOsProcessSampler, WindowsProcessSampler
│               ├── base64/            # Codificación Base64
│               ├── json/              # Formateo JSON
│               └── echo/              # Manipulación de texto
│
├── desktop-api/                       # API de extensibilidad del desktop
│   └── src/main/java/
│       └── org/iumotionlabs/hefesto/desktop/api/
│           ├── feature/               # FeatureProvider SPI, FeatureCategory
│           ├── navigation/            # NavigationContribution
│           ├── widget/                # WidgetDescriptor
│           ├── action/                # ActionDescriptor
│           ├── event/                 # NavigationRequested, LocaleChanged
│           └── preferences/           # PreferencesAccessor
│
├── desktop/                           # Aplicación JavaFX
│   └── src/main/java/
│       └── org/iumotionlabs/hefesto/desktop/
│           ├── HefestoDesktopApp.java # Punto de entrada JavaFX
│           ├── ServiceLocator.java    # DI sin framework
│           ├── mvvm/                  # BaseViewModel
│           ├── event/                 # EventBus (WeakReference)
│           ├── concurrency/           # HefestoExecutors (virtual threads)
│           ├── i18n/                  # I18nService (EN/ES)
│           ├── theme/                 # ThemeManager (light/dark)
│           ├── audit/                 # AuditTrail
│           ├── observability/         # NotificationService
│           ├── cache/                 # Cache utilities
│           ├── shell/                 # ShellView, SidebarView, TabContainerView
│           ├── dashboard/             # DashboardView, WidgetContainer
│           ├── preferences/           # PreferencesView
│           ├── controls/              # Componentes reutilizables
│           │   ├── VirtualizedTableView  # TableView con filtro/sort/export CSV
│           │   ├── KpiCard               # Tarjeta de indicador clave
│           │   ├── StatusBadge           # Badge de severidad con colores
│           │   ├── SearchField           # TextField con debounce
│           │   ├── RefreshButton         # Botón con animación y auto-refresh
│           │   └── ProcessKillDialog     # Diálogo de confirmación para kill
│           ├── chart/                 # TimeSeriesChart, GaugeChart, DistributionChart
│           └── feature/
│               ├── portinfo/          # Network feature
│               │   ├── PortInfoFeatureProvider.java
│               │   └── view/
│               │       ├── NetworkExplorerView/ViewModel
│               │       ├── PortDetailView/ViewModel
│               │       └── PortOverviewWidget
│               ├── procwatch/         # System feature
│               │   ├── ProcWatchFeatureProvider.java
│               │   └── view/
│               │       ├── ProcessExplorerView/ViewModel
│               │       ├── ProcessMonitorView/ViewModel
│               │       └── TopProcessesWidget
│               └── tools/             # Utilities feature
│                   ├── ToolsFeatureProvider.java
│                   └── view/
│                       ├── ToolsView
│                       ├── Base64ToolView
│                       └── JsonToolView
```

### Principios de Diseño

1. **Sealed Classes/Interfaces** - Control estricto de jerarquías (ProcessSampler, PortParser)
2. **Records** - Inmutabilidad para modelos de datos (ProcessSample, PortBinding, HealthCheckResult)
3. **Pattern Matching** - Manejo elegante de resultados (switch expressions)
4. **MVVM** - Separación vista/lógica en desktop (ViewModels con JavaFX properties)
5. **ServiceLoader SPI** - Descubrimiento de features via `FeatureProvider`
6. **Virtual Threads** - Concurrencia ligera para operaciones I/O en desktop
7. **EventBus** - Comunicación desacoplada entre componentes UI

### Diagrama de Flujo

```
                    ┌──────────────────────────────┐
                    │     Desktop (JavaFX)         │
                    │                              │
                    │  ┌──────────┐ ┌───────────┐  │
                    │  │ Process  │ │ Network   │  │
                    │  │ Explorer │ │ Explorer  │  │
                    │  └────┬─────┘ └─────┬─────┘  │
                    │       │             │        │
                    │  ┌────▼─────────────▼─────┐  │
                    │  │    ViewModels (MVVM)    │  │
                    │  └────────────┬────────────┘  │
                    │               │               │
                    └───────────────┼───────────────┘
                                    │
┌───────────────────┐    ┌──────────▼──────────┐
│  CLI / Modo       │    │                     │
│  Interactivo      │    │   Core Services     │
│                   │    │                     │
│  ┌─────────────┐  │    │  PortInfoService    │
│  │  Commands   │──┼───►│  ProcessMonitor     │
│  │  (execute)  │  │    │  HealthCheckService │
│  └─────────────┘  │    │  DockerService      │
│                   │    │  SecurityAnalysis   │
└───────────────────┘    │                     │
                         └──────────┬──────────┘
                                    │
                         ┌──────────▼──────────┐
                         │   OS-Specific       │
                         │   Parsers/Samplers  │
                         │                     │
                         │  Linux / macOS /    │
                         │  Windows            │
                         └─────────────────────┘
```

## Desarrollo

### Agregar un Nuevo Comando (CLI)

1. Crear clase en `core/src/main/java/.../feature/<nombre>/<Nombre>Command.java`
2. Registrar en `HefestoApplication.java`
3. Exportar package en `module-info.java`

### Agregar una Nueva Feature (Desktop)

1. Crear `FeatureProvider` en `desktop/src/main/java/.../feature/<nombre>/`
2. Crear views y viewmodels en subpaquete `view/`
3. Registrar en `module-info.java` (`provides...with`, `opens...to javafx.fxml`, `exports`)
4. Agregar i18n keys en `messages.properties` y `messages_es.properties`

### Ejecutar Tests

```bash
# Todos los tests
./gradlew test

# Tests de un módulo
./gradlew :core:test

# Test específico
./gradlew :core:test --tests "PortBindingTest"
```

### Build y Distribución

```bash
# Compilar todo
./gradlew build

# Solo compilar desktop
./gradlew :desktop:compileJava

# Ejecutar desktop
./gradlew :desktop:run

# Crear distribución
./gradlew distZip distTar

# Limpiar y rebuild
./gradlew clean build
```

## Tecnologías Utilizadas

| Tecnología | Versión | Propósito |
|------------|---------|-----------|
| Java | 25 | Lenguaje principal |
| Gradle | 9.2 | Build tool |
| JavaFX | 24 | UI framework (desktop) |
| Jackson | 3.x | Procesamiento JSON/YAML |
| Log4j2 | 2.24 | Logging |
| Lombok | 1.18 | Reducción de boilerplate |
| JUnit 6 | 6.x | Testing |

## Configuración

### Logging

Configurar `log4j2.xml` para ajustar niveles de log:

```bash
# Modo verbose desde CLI
./gradlew :core:run --args="--verbose portinfo --all"
```

### Preferencias Desktop

La aplicación desktop permite configurar:
- **Tema** - Claro / Oscuro
- **Idioma** - Inglés / Español
- **Auto Refresh** - Intervalo de actualización automática

## Contribuir

1. Fork el repositorio
2. Crear branch: `git checkout -b feature/mi-feature`
3. Commit cambios: `git commit -m 'Add mi feature'`
4. Push: `git push origin feature/mi-feature`
5. Abrir Pull Request

## Licencia

Este proyecto está bajo la licencia MIT. Ver [LICENSE](LICENSE) para más detalles.

---

**Hefesto** - *Forjando herramientas para desarrolladores*

Desarrollado por [IUMotion Labs](https://iumotionlabs.org)
