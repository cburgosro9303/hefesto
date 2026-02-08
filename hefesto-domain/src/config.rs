use serde::Deserialize;
use std::sync::OnceLock;
use std::time::Duration;

static CONFIG: OnceLock<HefestoConfig> = OnceLock::new();

/// Central configuration for Hefesto.
/// Loaded from embedded hefesto.yml at startup.
#[derive(Debug, Clone)]
pub struct HefestoConfig {
    // Display settings
    pub max_text_width: i32,
    pub max_process_name_width: i32,
    pub max_command_width: i32,
    pub max_service_width: i32,

    // PortInfo settings
    pub health_check_timeout_ms: u64,
    pub ssl_timeout_ms: u64,
    pub max_port_range: u32,

    // ProcWatch settings
    pub default_interval_ms: u64,
    pub default_top_limit: usize,
    pub alert_history_seconds: u64,
}

#[derive(Debug, Deserialize, Default)]
struct ConfigData {
    #[serde(default)]
    display: DisplayConfig,
    #[serde(default)]
    portinfo: PortInfoConfig,
    #[serde(default)]
    procwatch: ProcWatchConfig,
}

#[derive(Debug, Deserialize)]
struct DisplayConfig {
    #[serde(default = "default_256")]
    #[serde(rename = "maxTextWidth")]
    max_text_width: i32,
    #[serde(default = "default_256")]
    #[serde(rename = "maxProcessNameWidth")]
    max_process_name_width: i32,
    #[serde(default = "default_256")]
    #[serde(rename = "maxCommandWidth")]
    max_command_width: i32,
    #[serde(default = "default_256")]
    #[serde(rename = "maxServiceWidth")]
    max_service_width: i32,
}

impl Default for DisplayConfig {
    fn default() -> Self {
        Self {
            max_text_width: 256,
            max_process_name_width: 256,
            max_command_width: 256,
            max_service_width: 256,
        }
    }
}

#[derive(Debug, Deserialize)]
struct PortInfoConfig {
    #[serde(default = "default_5000")]
    #[serde(rename = "healthCheckTimeoutMs")]
    health_check_timeout_ms: u64,
    #[serde(default = "default_10000")]
    #[serde(rename = "sslTimeoutMs")]
    ssl_timeout_ms: u64,
    #[serde(default = "default_1000u32")]
    #[serde(rename = "maxPortRange")]
    max_port_range: u32,
}

impl Default for PortInfoConfig {
    fn default() -> Self {
        Self {
            health_check_timeout_ms: 5000,
            ssl_timeout_ms: 10000,
            max_port_range: 1000,
        }
    }
}

#[derive(Debug, Deserialize)]
struct ProcWatchConfig {
    #[serde(default = "default_1000u64")]
    #[serde(rename = "defaultIntervalMs")]
    default_interval_ms: u64,
    #[serde(default = "default_10")]
    #[serde(rename = "defaultTopLimit")]
    default_top_limit: usize,
    #[serde(default = "default_600")]
    #[serde(rename = "alertHistorySeconds")]
    alert_history_seconds: u64,
}

impl Default for ProcWatchConfig {
    fn default() -> Self {
        Self {
            default_interval_ms: 1000,
            default_top_limit: 10,
            alert_history_seconds: 600,
        }
    }
}

fn default_256() -> i32 {
    256
}
fn default_5000() -> u64 {
    5000
}
fn default_10000() -> u64 {
    10000
}
fn default_1000u32() -> u32 {
    1000
}
fn default_1000u64() -> u64 {
    1000
}
fn default_10() -> usize {
    10
}
fn default_600() -> u64 {
    600
}

impl HefestoConfig {
    /// Returns the global configuration singleton.
    pub fn get() -> &'static HefestoConfig {
        CONFIG.get_or_init(|| Self::load_default())
    }

    /// Initializes configuration from YAML string.
    pub fn init_from_yaml(yaml: &str) -> &'static HefestoConfig {
        CONFIG.get_or_init(|| Self::from_yaml(yaml))
    }

    /// Parses configuration from a YAML string.
    fn from_yaml(yaml: &str) -> HefestoConfig {
        match serde_yaml::from_str::<ConfigData>(yaml) {
            Ok(data) => Self::from_data(data),
            Err(e) => {
                eprintln!("Warning: Error parsing hefesto.yml: {e}, using defaults");
                Self::from_data(ConfigData::default())
            }
        }
    }

    /// Creates default configuration.
    fn load_default() -> HefestoConfig {
        Self::from_data(ConfigData::default())
    }

    fn from_data(data: ConfigData) -> HefestoConfig {
        HefestoConfig {
            max_text_width: data.display.max_text_width,
            max_process_name_width: data.display.max_process_name_width,
            max_command_width: data.display.max_command_width,
            max_service_width: data.display.max_service_width,
            health_check_timeout_ms: data.portinfo.health_check_timeout_ms,
            ssl_timeout_ms: data.portinfo.ssl_timeout_ms,
            max_port_range: data.portinfo.max_port_range,
            default_interval_ms: data.procwatch.default_interval_ms,
            default_top_limit: data.procwatch.default_top_limit,
            alert_history_seconds: data.procwatch.alert_history_seconds,
        }
    }

    /// Default sampling interval.
    pub fn default_interval(&self) -> Duration {
        Duration::from_millis(self.default_interval_ms)
    }

    /// Alert history duration.
    pub fn alert_history_duration(&self) -> Duration {
        Duration::from_secs(self.alert_history_seconds)
    }

    /// Truncates text to the configured max width.
    pub fn truncate<'a>(&self, text: &'a str) -> String {
        Self::truncate_to(text, self.max_text_width)
    }

    /// Truncates process name according to configuration.
    pub fn truncate_process_name(&self, name: &str) -> String {
        Self::truncate_to(name, self.max_process_name_width)
    }

    /// Truncates command line according to configuration.
    pub fn truncate_command(&self, command: &str) -> String {
        Self::truncate_to(command, self.max_command_width)
    }

    /// Truncates service name according to configuration.
    pub fn truncate_service(&self, service: &str) -> String {
        Self::truncate_to(service, self.max_service_width)
    }

    /// Truncates text to the specified max width.
    /// Returns the original text if max_width is negative (disabled)
    /// or if the text is shorter than the limit.
    pub fn truncate_to(text: &str, max_width: i32) -> String {
        if max_width < 0 || (text.len() as i32) <= max_width {
            return text.to_string();
        }
        let max_width = max_width as usize;
        if max_width <= 3 {
            return text[..max_width].to_string();
        }
        format!("{}...", &text[..max_width - 3])
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_truncate_short_text() {
        assert_eq!(HefestoConfig::truncate_to("hello", 10), "hello");
    }

    #[test]
    fn test_truncate_long_text() {
        assert_eq!(HefestoConfig::truncate_to("hello world", 8), "hello...");
    }

    #[test]
    fn test_truncate_disabled() {
        assert_eq!(HefestoConfig::truncate_to("hello world", -1), "hello world");
    }

    #[test]
    fn test_truncate_exact_length() {
        assert_eq!(HefestoConfig::truncate_to("hello", 5), "hello");
    }
}
