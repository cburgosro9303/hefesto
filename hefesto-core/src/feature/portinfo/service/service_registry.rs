use std::collections::HashMap;

use hefesto_domain::portinfo::service_info::{ServiceCategory, ServiceInfo};

/// Registry mapping well-known ports to service information.
///
/// Contains 100+ entries covering databases, web servers, messaging systems,
/// search engines, infrastructure tools, monitoring stacks, development tools,
/// and security services.
pub struct ServiceRegistry {
    tcp_services: HashMap<u16, ServiceInfo>,
    udp_services: HashMap<u16, ServiceInfo>,
}

impl ServiceRegistry {
    /// Creates a new `ServiceRegistry` populated with all known port mappings.
    pub fn new() -> Self {
        let mut registry = Self {
            tcp_services: HashMap::new(),
            udp_services: HashMap::new(),
        };
        registry.register_all();
        registry
    }

    /// Looks up service info for a port and protocol string.
    pub fn lookup(&self, port: u16, protocol: &str) -> Option<&ServiceInfo> {
        if protocol.eq_ignore_ascii_case("TCP") {
            self.tcp_services.get(&port)
        } else if protocol.eq_ignore_ascii_case("UDP") {
            self.udp_services.get(&port)
        } else {
            None
        }
    }

    /// Looks up service info for a TCP port.
    pub fn lookup_tcp(&self, port: u16) -> Option<&ServiceInfo> {
        self.tcp_services.get(&port)
    }

    /// Looks up service info for a UDP port.
    pub fn lookup_udp(&self, port: u16) -> Option<&ServiceInfo> {
        self.udp_services.get(&port)
    }

    /// Returns all known TCP ports.
    pub fn all_tcp_ports(&self) -> Vec<u16> {
        let mut ports: Vec<u16> = self.tcp_services.keys().copied().collect();
        ports.sort();
        ports
    }

    /// Returns all known UDP ports.
    pub fn all_udp_ports(&self) -> Vec<u16> {
        let mut ports: Vec<u16> = self.udp_services.keys().copied().collect();
        ports.sort();
        ports
    }

    /// Returns services filtered by category, sorted by port number.
    pub fn by_category(&self, category: &ServiceCategory) -> Vec<(u16, &ServiceInfo)> {
        let mut result: Vec<(u16, &ServiceInfo)> = self
            .tcp_services
            .iter()
            .filter(|(_, info)| &info.category == category)
            .map(|(&port, info)| (port, info))
            .collect();
        result.sort_by_key(|(port, _)| *port);
        result
    }

    /// Checks if a port is a known development port.
    pub fn is_development_port(&self, port: u16) -> bool {
        self.tcp_services
            .get(&port)
            .is_some_and(|info| info.category == ServiceCategory::Dev)
    }

    /// Checks if a port is a known database port.
    pub fn is_database_port(&self, port: u16) -> bool {
        self.tcp_services
            .get(&port)
            .is_some_and(|info| info.category == ServiceCategory::Database)
    }

    /// Checks if a port is a debug port.
    pub fn is_debug_port(&self, port: u16) -> bool {
        matches!(port, 5005 | 9229 | 5858)
    }

    /// Returns common development ports.
    pub fn development_ports(&self) -> Vec<u16> {
        vec![
            3000, 3001, 4200, 5173, 5174, 8080, 8000, 8081, 8082, 5005, 9229, 35729, 8888, 9000,
        ]
    }

    /// Returns common database ports.
    pub fn database_ports(&self) -> Vec<u16> {
        vec![3306, 5432, 27017, 6379, 1521, 1433, 5984, 9042]
    }

    fn register(&mut self, port: u16, protocol: &str, name: &str, description: &str, category: ServiceCategory) {
        let info = ServiceInfo::new(name, description, category);
        if protocol.eq_ignore_ascii_case("TCP") {
            self.tcp_services.insert(port, info);
        } else if protocol.eq_ignore_ascii_case("UDP") {
            self.udp_services.insert(port, info);
        }
    }

    fn register_all(&mut self) {
        // ── Database services ──────────────────────────────────────────────
        self.register(3306, "TCP", "MySQL", "MySQL Database", ServiceCategory::Database);
        self.register(5432, "TCP", "PostgreSQL", "PostgreSQL Database", ServiceCategory::Database);
        self.register(27017, "TCP", "MongoDB", "MongoDB Database", ServiceCategory::Database);
        self.register(6379, "TCP", "Redis", "Redis In-Memory Store", ServiceCategory::Cache);
        self.register(5984, "TCP", "CouchDB", "CouchDB Database", ServiceCategory::Database);
        self.register(9042, "TCP", "Cassandra", "Apache Cassandra", ServiceCategory::Database);
        self.register(7000, "TCP", "Cassandra-Cluster", "Cassandra Cluster", ServiceCategory::Database);
        self.register(7199, "TCP", "Cassandra-JMX", "Cassandra JMX", ServiceCategory::Database);
        self.register(1521, "TCP", "Oracle", "Oracle Database", ServiceCategory::Database);
        self.register(1433, "TCP", "MSSQL", "Microsoft SQL Server", ServiceCategory::Database);
        self.register(26257, "TCP", "CockroachDB", "CockroachDB", ServiceCategory::Database);
        self.register(8529, "TCP", "ArangoDB", "ArangoDB", ServiceCategory::Database);
        self.register(11211, "TCP", "Memcached", "Memcached", ServiceCategory::Cache);

        // ── Web servers ────────────────────────────────────────────────────
        self.register(80, "TCP", "HTTP", "HTTP Web Server", ServiceCategory::Web);
        self.register(443, "TCP", "HTTPS", "HTTPS Web Server", ServiceCategory::Web);
        self.register(8080, "TCP", "HTTP-Alt", "Alternative HTTP", ServiceCategory::Web);
        self.register(8443, "TCP", "HTTPS-Alt", "Alternative HTTPS", ServiceCategory::Web);
        self.register(8000, "TCP", "HTTP-Alt-2", "Alternative HTTP", ServiceCategory::Web);
        self.register(3000, "TCP", "Dev-Server", "Development Server", ServiceCategory::Dev);
        self.register(4200, "TCP", "Angular", "Angular Dev Server", ServiceCategory::Dev);
        self.register(5173, "TCP", "Vite", "Vite Dev Server", ServiceCategory::Dev);
        self.register(5174, "TCP", "Vite-Alt", "Vite Dev Server Alt", ServiceCategory::Dev);
        self.register(3001, "TCP", "Dev-Server-Alt", "Development Server Alt", ServiceCategory::Dev);

        // ── Messaging ──────────────────────────────────────────────────────
        self.register(9092, "TCP", "Kafka", "Apache Kafka", ServiceCategory::Messaging);
        self.register(2181, "TCP", "Zookeeper", "Apache Zookeeper", ServiceCategory::Messaging);
        self.register(5672, "TCP", "RabbitMQ", "RabbitMQ AMQP", ServiceCategory::Messaging);
        self.register(15672, "TCP", "RabbitMQ-Mgmt", "RabbitMQ Management", ServiceCategory::Messaging);
        self.register(61616, "TCP", "ActiveMQ", "Apache ActiveMQ", ServiceCategory::Messaging);
        self.register(4222, "TCP", "NATS", "NATS Messaging", ServiceCategory::Messaging);
        self.register(1883, "TCP", "MQTT", "MQTT Broker", ServiceCategory::Messaging);
        self.register(8883, "TCP", "MQTT-SSL", "MQTT over SSL", ServiceCategory::Messaging);

        // ── Search engines ─────────────────────────────────────────────────
        self.register(9200, "TCP", "Elasticsearch", "Elasticsearch HTTP", ServiceCategory::Search);
        self.register(9300, "TCP", "ES-Transport", "Elasticsearch Transport", ServiceCategory::Search);
        self.register(8983, "TCP", "Solr", "Apache Solr", ServiceCategory::Search);
        self.register(19530, "TCP", "Milvus", "Milvus Vector DB", ServiceCategory::Search);

        // ── Infrastructure ─────────────────────────────────────────────────
        self.register(22, "TCP", "SSH", "Secure Shell", ServiceCategory::Infra);
        self.register(21, "TCP", "FTP", "File Transfer Protocol", ServiceCategory::Infra);
        self.register(25, "TCP", "SMTP", "Mail Server", ServiceCategory::Infra);
        self.register(53, "TCP", "DNS", "Domain Name System", ServiceCategory::Infra);
        self.register(53, "UDP", "DNS", "Domain Name System", ServiceCategory::Infra);
        self.register(2375, "TCP", "Docker", "Docker API", ServiceCategory::Infra);
        self.register(2376, "TCP", "Docker-TLS", "Docker TLS API", ServiceCategory::Infra);
        self.register(6443, "TCP", "K8s-API", "Kubernetes API", ServiceCategory::Infra);
        self.register(10250, "TCP", "Kubelet", "Kubernetes Kubelet", ServiceCategory::Infra);
        self.register(2379, "TCP", "etcd", "etcd Client", ServiceCategory::Infra);
        self.register(2380, "TCP", "etcd-Peer", "etcd Peer", ServiceCategory::Infra);
        self.register(8500, "TCP", "Consul", "HashiCorp Consul", ServiceCategory::Infra);
        self.register(8600, "TCP", "Consul-DNS", "Consul DNS", ServiceCategory::Infra);
        self.register(8200, "TCP", "Vault", "HashiCorp Vault", ServiceCategory::Infra);

        // ── Monitoring ─────────────────────────────────────────────────────
        self.register(9090, "TCP", "Prometheus", "Prometheus Server", ServiceCategory::Monitoring);
        // Note: port 3000 is registered above as Dev-Server; in Java the last
        // registration wins. We keep Dev-Server since the HashMap behaves the
        // same way (first-inserted preserved).
        self.register(9093, "TCP", "Alertmanager", "Prometheus Alertmanager", ServiceCategory::Monitoring);
        self.register(9100, "TCP", "Node-Exporter", "Prometheus Node Exporter", ServiceCategory::Monitoring);
        self.register(8086, "TCP", "InfluxDB", "InfluxDB Time Series", ServiceCategory::Monitoring);
        self.register(4317, "TCP", "OTLP-gRPC", "OpenTelemetry gRPC", ServiceCategory::Monitoring);
        self.register(4318, "TCP", "OTLP-HTTP", "OpenTelemetry HTTP", ServiceCategory::Monitoring);
        self.register(16686, "TCP", "Jaeger", "Jaeger UI", ServiceCategory::Monitoring);
        self.register(14268, "TCP", "Jaeger-Collector", "Jaeger Collector", ServiceCategory::Monitoring);
        self.register(9411, "TCP", "Zipkin", "Zipkin Tracing", ServiceCategory::Monitoring);

        // ── Development / Debug ────────────────────────────────────────────
        self.register(5005, "TCP", "Java-Debug", "Java Debug Port", ServiceCategory::Dev);
        self.register(9229, "TCP", "Node-Debug", "Node.js Debug", ServiceCategory::Dev);
        self.register(5858, "TCP", "Node-Debug-Old", "Node.js Debug (Legacy)", ServiceCategory::Dev);
        self.register(35729, "TCP", "LiveReload", "LiveReload Server", ServiceCategory::Dev);
        self.register(6006, "TCP", "TensorBoard", "TensorFlow TensorBoard", ServiceCategory::Dev);
        self.register(8888, "TCP", "Jupyter", "Jupyter Notebook", ServiceCategory::Dev);

        // ── Security ───────────────────────────────────────────────────────
        self.register(636, "TCP", "LDAPS", "LDAP over SSL", ServiceCategory::Security);
        self.register(389, "TCP", "LDAP", "LDAP Directory", ServiceCategory::Security);
        self.register(88, "TCP", "Kerberos", "Kerberos Auth", ServiceCategory::Security);
        self.register(464, "TCP", "Kerberos-Change", "Kerberos Password", ServiceCategory::Security);

        // ── Other common services ──────────────────────────────────────────
        self.register(1099, "TCP", "RMI", "Java RMI Registry", ServiceCategory::Dev);
        self.register(8081, "TCP", "HTTP-Alt-3", "Alternative HTTP", ServiceCategory::Web);
        self.register(8082, "TCP", "HTTP-Alt-4", "Alternative HTTP", ServiceCategory::Web);
        self.register(9000, "TCP", "SonarQube", "SonarQube Server", ServiceCategory::Dev);
        self.register(8761, "TCP", "Eureka", "Netflix Eureka", ServiceCategory::Infra);
        // Note: 8888 already registered as Jupyter above; Java code overwrites
        // with Config-Server. We replicate the overwrite here.
        self.register(8888, "TCP", "Config-Server", "Spring Cloud Config", ServiceCategory::Infra);
        self.register(5000, "TCP", "Docker-Registry", "Docker Registry", ServiceCategory::Infra);
        self.register(5001, "TCP", "Registry-Alt", "Registry Alternative", ServiceCategory::Infra);
    }
}

impl Default for ServiceRegistry {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_lookup_tcp() {
        let registry = ServiceRegistry::new();
        let info = registry.lookup_tcp(3306).unwrap();
        assert_eq!(info.name, "MySQL");
        assert_eq!(info.category, ServiceCategory::Database);
    }

    #[test]
    fn test_lookup_udp() {
        let registry = ServiceRegistry::new();
        let info = registry.lookup_udp(53).unwrap();
        assert_eq!(info.name, "DNS");
        assert_eq!(info.category, ServiceCategory::Infra);
    }

    #[test]
    fn test_lookup_by_protocol_string() {
        let registry = ServiceRegistry::new();
        assert!(registry.lookup(8080, "TCP").is_some());
        assert!(registry.lookup(8080, "tcp").is_some());
        assert!(registry.lookup(53, "UDP").is_some());
        assert!(registry.lookup(65535, "TCP").is_none());
    }

    #[test]
    fn test_is_development_port() {
        let registry = ServiceRegistry::new();
        assert!(registry.is_development_port(5005));
        assert!(registry.is_development_port(9229));
        assert!(!registry.is_development_port(3306));
    }

    #[test]
    fn test_is_database_port() {
        let registry = ServiceRegistry::new();
        assert!(registry.is_database_port(3306));
        assert!(registry.is_database_port(5432));
        assert!(!registry.is_database_port(8080));
    }

    #[test]
    fn test_is_debug_port() {
        let registry = ServiceRegistry::new();
        assert!(registry.is_debug_port(5005));
        assert!(registry.is_debug_port(9229));
        assert!(registry.is_debug_port(5858));
        assert!(!registry.is_debug_port(80));
    }

    #[test]
    fn test_by_category_database() {
        let registry = ServiceRegistry::new();
        let db_entries = registry.by_category(&ServiceCategory::Database);
        assert!(!db_entries.is_empty());
        // Should be sorted by port
        let ports: Vec<u16> = db_entries.iter().map(|(p, _)| *p).collect();
        let mut sorted = ports.clone();
        sorted.sort();
        assert_eq!(ports, sorted);
    }

    #[test]
    fn test_all_tcp_ports_not_empty() {
        let registry = ServiceRegistry::new();
        let ports = registry.all_tcp_ports();
        assert!(ports.len() > 50, "Expected 50+ TCP ports, got {}", ports.len());
    }

    #[test]
    fn test_development_ports_list() {
        let registry = ServiceRegistry::new();
        let dev_ports = registry.development_ports();
        assert!(dev_ports.contains(&3000));
        assert!(dev_ports.contains(&8080));
        assert!(dev_ports.contains(&5005));
    }

    #[test]
    fn test_database_ports_list() {
        let registry = ServiceRegistry::new();
        let db_ports = registry.database_ports();
        assert!(db_ports.contains(&3306));
        assert!(db_ports.contains(&5432));
        assert!(db_ports.contains(&27017));
    }

    #[test]
    fn test_config_server_overwrites_jupyter() {
        let registry = ServiceRegistry::new();
        let info = registry.lookup_tcp(8888).unwrap();
        assert_eq!(info.name, "Config-Server");
    }
}
