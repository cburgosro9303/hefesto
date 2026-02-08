use std::collections::HashSet;
use std::net::TcpListener;
use std::sync::Arc;

use hefesto_domain::portinfo::enriched_port_binding::EnrichedPortBinding;
use hefesto_domain::portinfo::network_overview::NetworkOverview;
use hefesto_domain::portinfo::port_binding::PortBinding;
use hefesto_platform::port_parser::PortParser;

use super::service_registry::ServiceRegistry;

/// Common development ports that are checked in dev-port discovery.
const DEV_PORTS: &[u16] = &[
    3000, 3001, 4200, 5173, 5174, 8080, 8000, 8081, 8082, 5005, 9229, 35729, 8888, 9000, 5000, 5001,
];

/// Service for orchestrating port information queries.
///
/// Acts as the primary facade over the platform-specific `PortParser` and the
/// `ServiceRegistry`, enriching raw port bindings with well-known service
/// metadata and providing higher-level operations such as free-port detection
/// and network overview generation.
pub struct PortInfoService {
    parser: Arc<dyn PortParser>,
    service_registry: ServiceRegistry,
}

impl PortInfoService {
    /// Creates a new `PortInfoService` with the given platform parser.
    pub fn new(parser: Arc<dyn PortParser>) -> Self {
        Self {
            parser,
            service_registry: ServiceRegistry::new(),
        }
    }

    /// Finds bindings for a specific port (TCP only by default).
    pub fn find_by_port(&self, port: u16) -> Vec<PortBinding> {
        self.parser
            .find_by_port(port, true, false)
            .unwrap_or_default()
    }

    /// Finds bindings for a specific port with protocol selection.
    pub fn find_by_port_with_protocol(&self, port: u16, tcp: bool, udp: bool) -> Vec<PortBinding> {
        self.parser.find_by_port(port, tcp, udp).unwrap_or_default()
    }

    /// Finds all ports associated with a process.
    pub fn find_by_pid(&self, pid: u32) -> Vec<PortBinding> {
        self.parser.find_by_pid(pid).unwrap_or_default()
    }

    /// Finds ports in a range.
    pub fn find_in_range(&self, from: u16, to: u16, listen_only: bool) -> Vec<PortBinding> {
        self.parser
            .find_in_range(from, to, listen_only)
            .unwrap_or_default()
    }

    /// Finds all listening ports.
    pub fn find_all_listening(&self) -> Vec<PortBinding> {
        self.parser.find_all_listening().unwrap_or_default()
    }

    /// Finds all active port bindings.
    pub fn find_all(&self, tcp: bool, udp: bool) -> Vec<PortBinding> {
        self.parser.find_all(tcp, udp).unwrap_or_default()
    }

    /// Finds all active port bindings (TCP and UDP).
    pub fn find_all_both(&self) -> Vec<PortBinding> {
        self.parser.find_all_both().unwrap_or_default()
    }

    /// Finds ports by process name (case-insensitive partial match).
    pub fn find_by_process_name(&self, process_name: &str) -> Vec<PortBinding> {
        self.parser
            .find_by_process_name(process_name)
            .unwrap_or_default()
    }

    /// Finds common development ports that are in use.
    pub fn find_dev_ports(&self) -> Vec<PortBinding> {
        let dev_set: HashSet<u16> = DEV_PORTS.iter().copied().collect();
        let all_listening = self.find_all_listening();

        all_listening
            .into_iter()
            .filter(|b| {
                dev_set.contains(&b.port) || self.service_registry.is_development_port(b.port)
            })
            .collect()
    }

    /// Checks if a port is free (not in use).
    pub fn is_port_free(&self, port: u16) -> bool {
        TcpListener::bind(("127.0.0.1", port)).is_ok()
    }

    /// Finds free ports near the given port.
    /// Returns a list of available ports starting from the given port.
    pub fn find_free_ports(&self, start_port: u16, count: usize) -> Vec<u16> {
        let mut free_ports = Vec::new();
        let max_port = start_port.saturating_add(1000);
        let mut port = start_port;

        while free_ports.len() < count && port <= max_port {
            if self.is_port_free(port) {
                free_ports.push(port);
            }
            port = match port.checked_add(1) {
                Some(p) => p,
                None => break,
            };
        }

        free_ports
    }

    /// Finds alternative ports if the given port is in use.
    /// Returns the original port if free, otherwise finds nearby alternatives.
    pub fn find_alternatives(&self, port: u16, mut count: usize) -> Vec<u16> {
        let mut alternatives = Vec::new();

        if self.is_port_free(port) {
            alternatives.push(port);
            count = count.saturating_sub(1);
        }

        if count > 0 {
            if let Some(next) = port.checked_add(1) {
                alternatives.extend(self.find_free_ports(next, count));
            }
        }

        alternatives
    }

    /// Gets a network overview with all listening ports and statistics.
    pub fn get_network_overview(&self) -> NetworkOverview {
        let all_bindings = self.find_all_listening();
        let enriched = self.enrich_bindings(&all_bindings);
        NetworkOverview::from_bindings(enriched)
    }

    /// Enriches a list of port bindings with service information.
    pub fn enrich_bindings(&self, bindings: &[PortBinding]) -> Vec<EnrichedPortBinding> {
        bindings
            .iter()
            .map(|binding| self.enrich_binding(binding))
            .collect()
    }

    /// Enriches a single port binding with service information.
    pub fn enrich_binding(&self, binding: &PortBinding) -> EnrichedPortBinding {
        let eb = EnrichedPortBinding::from_binding(binding.clone());
        match self
            .service_registry
            .lookup(binding.port, binding.protocol.as_str())
        {
            Some(service) => eb.set_service_info(service.clone()),
            None => eb,
        }
    }

    /// Terminates a process.
    pub fn kill_process(&self, pid: u32, force: bool) -> bool {
        self.parser.kill_process(pid, force).unwrap_or(false)
    }

    /// Returns a reference to the service registry.
    pub fn service_registry(&self) -> &ServiceRegistry {
        &self.service_registry
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use anyhow::Result;
    use hefesto_domain::portinfo::port_binding::{ConnectionState, Protocol};

    /// Mock parser for testing.
    struct MockPortParser {
        bindings: Vec<PortBinding>,
    }

    impl MockPortParser {
        fn new(bindings: Vec<PortBinding>) -> Self {
            Self { bindings }
        }
    }

    impl PortParser for MockPortParser {
        fn find_by_port(&self, port: u16, _tcp: bool, _udp: bool) -> Result<Vec<PortBinding>> {
            Ok(self
                .bindings
                .iter()
                .filter(|b| b.port == port)
                .cloned()
                .collect())
        }

        fn find_by_pid(&self, pid: u32) -> Result<Vec<PortBinding>> {
            Ok(self
                .bindings
                .iter()
                .filter(|b| b.pid == pid)
                .cloned()
                .collect())
        }

        fn find_in_range(
            &self,
            from: u16,
            to: u16,
            _listen_only: bool,
        ) -> Result<Vec<PortBinding>> {
            Ok(self
                .bindings
                .iter()
                .filter(|b| b.port >= from && b.port <= to)
                .cloned()
                .collect())
        }

        fn find_all_listening(&self) -> Result<Vec<PortBinding>> {
            Ok(self
                .bindings
                .iter()
                .filter(|b| b.state == ConnectionState::Listen)
                .cloned()
                .collect())
        }

        fn find_all(&self, _tcp: bool, _udp: bool) -> Result<Vec<PortBinding>> {
            Ok(self.bindings.clone())
        }

        fn find_by_process_name(&self, name: &str) -> Result<Vec<PortBinding>> {
            let lower = name.to_lowercase();
            Ok(self
                .bindings
                .iter()
                .filter(|b| b.process_name.to_lowercase().contains(&lower))
                .cloned()
                .collect())
        }

        fn kill_process(&self, _pid: u32, _force: bool) -> Result<bool> {
            Ok(true)
        }
    }

    fn make_binding(port: u16, pid: u32, name: &str) -> PortBinding {
        PortBinding::listen(port, Protocol::Tcp, pid, name)
    }

    #[test]
    fn test_find_by_port() {
        let bindings = vec![make_binding(8080, 100, "java")];
        let service = PortInfoService::new(Arc::new(MockPortParser::new(bindings)));

        let result = service.find_by_port(8080);
        assert_eq!(result.len(), 1);
        assert_eq!(result[0].process_name, "java");
    }

    #[test]
    fn test_find_by_pid() {
        let bindings = vec![
            make_binding(8080, 100, "java"),
            make_binding(8081, 100, "java"),
            make_binding(3000, 200, "node"),
        ];
        let service = PortInfoService::new(Arc::new(MockPortParser::new(bindings)));

        let result = service.find_by_pid(100);
        assert_eq!(result.len(), 2);
    }

    #[test]
    fn test_find_all_listening() {
        let bindings = vec![
            make_binding(8080, 100, "java"),
            PortBinding {
                port: 443,
                protocol: Protocol::Tcp,
                state: ConnectionState::Established,
                pid: 200,
                process_name: "curl".to_string(),
                command_line: String::new(),
                user: String::new(),
                local_address: "0.0.0.0".to_string(),
                remote_address: String::new(),
                remote_port: 0,
            },
        ];
        let service = PortInfoService::new(Arc::new(MockPortParser::new(bindings)));

        let result = service.find_all_listening();
        assert_eq!(result.len(), 1);
        assert_eq!(result[0].port, 8080);
    }

    #[test]
    fn test_enrich_binding_with_known_service() {
        let binding = make_binding(3306, 100, "mysqld");
        let service = PortInfoService::new(Arc::new(MockPortParser::new(vec![])));

        let enriched = service.enrich_binding(&binding);
        assert!(enriched.service_info.is_some());
        assert_eq!(enriched.service_info.as_ref().unwrap().name, "MySQL");
    }

    #[test]
    fn test_enrich_binding_unknown_port() {
        let binding = make_binding(12345, 100, "custom");
        let service = PortInfoService::new(Arc::new(MockPortParser::new(vec![])));

        let enriched = service.enrich_binding(&binding);
        assert!(enriched.service_info.is_none());
    }

    #[test]
    fn test_find_dev_ports() {
        let bindings = vec![
            make_binding(3000, 100, "node"),
            make_binding(3306, 200, "mysqld"),
            make_binding(8080, 300, "java"),
        ];
        let service = PortInfoService::new(Arc::new(MockPortParser::new(bindings)));

        let result = service.find_dev_ports();
        // 3000 and 8080 are dev ports; 3306 is a database port
        let ports: Vec<u16> = result.iter().map(|b| b.port).collect();
        assert!(ports.contains(&3000));
        assert!(ports.contains(&8080));
        assert!(!ports.contains(&3306));
    }

    #[test]
    fn test_find_by_process_name() {
        let bindings = vec![
            make_binding(8080, 100, "java"),
            make_binding(3000, 200, "node"),
            make_binding(9229, 200, "node"),
        ];
        let service = PortInfoService::new(Arc::new(MockPortParser::new(bindings)));

        let result = service.find_by_process_name("node");
        assert_eq!(result.len(), 2);
    }

    #[test]
    fn test_network_overview() {
        let bindings = vec![
            make_binding(8080, 100, "java"),
            make_binding(3306, 200, "mysqld"),
        ];
        let service = PortInfoService::new(Arc::new(MockPortParser::new(bindings)));

        let overview = service.get_network_overview();
        assert_eq!(overview.statistics.total_listening, 2);
    }
}
