package org.iumotionlabs.hefesto.feature.portinfo.service;

import org.iumotionlabs.hefesto.feature.portinfo.model.ServiceInfo;
import org.iumotionlabs.hefesto.feature.portinfo.model.ServiceInfo.ServiceCategory;

import java.util.*;

/**
 * Registry mapping well-known ports to service information.
 */
public final class ServiceRegistry {

    private static final Map<Integer, ServiceInfo> TCP_SERVICES = new HashMap<>();
    private static final Map<Integer, ServiceInfo> UDP_SERVICES = new HashMap<>();

    static {
        // Database services
        register(3306, "TCP", "MySQL", "MySQL Database", ServiceCategory.DATABASE);
        register(5432, "TCP", "PostgreSQL", "PostgreSQL Database", ServiceCategory.DATABASE);
        register(27017, "TCP", "MongoDB", "MongoDB Database", ServiceCategory.DATABASE);
        register(6379, "TCP", "Redis", "Redis In-Memory Store", ServiceCategory.CACHE);
        register(5984, "TCP", "CouchDB", "CouchDB Database", ServiceCategory.DATABASE);
        register(9042, "TCP", "Cassandra", "Apache Cassandra", ServiceCategory.DATABASE);
        register(7000, "TCP", "Cassandra-Cluster", "Cassandra Cluster", ServiceCategory.DATABASE);
        register(7199, "TCP", "Cassandra-JMX", "Cassandra JMX", ServiceCategory.DATABASE);
        register(1521, "TCP", "Oracle", "Oracle Database", ServiceCategory.DATABASE);
        register(1433, "TCP", "MSSQL", "Microsoft SQL Server", ServiceCategory.DATABASE);
        register(26257, "TCP", "CockroachDB", "CockroachDB", ServiceCategory.DATABASE);
        register(8529, "TCP", "ArangoDB", "ArangoDB", ServiceCategory.DATABASE);
        register(11211, "TCP", "Memcached", "Memcached", ServiceCategory.CACHE);

        // Web servers
        register(80, "TCP", "HTTP", "HTTP Web Server", ServiceCategory.WEB);
        register(443, "TCP", "HTTPS", "HTTPS Web Server", ServiceCategory.WEB);
        register(8080, "TCP", "HTTP-Alt", "Alternative HTTP", ServiceCategory.WEB);
        register(8443, "TCP", "HTTPS-Alt", "Alternative HTTPS", ServiceCategory.WEB);
        register(8000, "TCP", "HTTP-Alt-2", "Alternative HTTP", ServiceCategory.WEB);
        register(3000, "TCP", "Dev-Server", "Development Server", ServiceCategory.DEV);
        register(4200, "TCP", "Angular", "Angular Dev Server", ServiceCategory.DEV);
        register(5173, "TCP", "Vite", "Vite Dev Server", ServiceCategory.DEV);
        register(5174, "TCP", "Vite-Alt", "Vite Dev Server Alt", ServiceCategory.DEV);
        register(3001, "TCP", "Dev-Server-Alt", "Development Server Alt", ServiceCategory.DEV);

        // Messaging
        register(9092, "TCP", "Kafka", "Apache Kafka", ServiceCategory.MESSAGING);
        register(2181, "TCP", "Zookeeper", "Apache Zookeeper", ServiceCategory.MESSAGING);
        register(5672, "TCP", "RabbitMQ", "RabbitMQ AMQP", ServiceCategory.MESSAGING);
        register(15672, "TCP", "RabbitMQ-Mgmt", "RabbitMQ Management", ServiceCategory.MESSAGING);
        register(61616, "TCP", "ActiveMQ", "Apache ActiveMQ", ServiceCategory.MESSAGING);
        register(4222, "TCP", "NATS", "NATS Messaging", ServiceCategory.MESSAGING);
        register(1883, "TCP", "MQTT", "MQTT Broker", ServiceCategory.MESSAGING);
        register(8883, "TCP", "MQTT-SSL", "MQTT over SSL", ServiceCategory.MESSAGING);

        // Search engines
        register(9200, "TCP", "Elasticsearch", "Elasticsearch HTTP", ServiceCategory.SEARCH);
        register(9300, "TCP", "ES-Transport", "Elasticsearch Transport", ServiceCategory.SEARCH);
        register(8983, "TCP", "Solr", "Apache Solr", ServiceCategory.SEARCH);
        register(19530, "TCP", "Milvus", "Milvus Vector DB", ServiceCategory.SEARCH);

        // Infrastructure
        register(22, "TCP", "SSH", "Secure Shell", ServiceCategory.INFRA);
        register(21, "TCP", "FTP", "File Transfer Protocol", ServiceCategory.INFRA);
        register(25, "TCP", "SMTP", "Mail Server", ServiceCategory.INFRA);
        register(53, "TCP", "DNS", "Domain Name System", ServiceCategory.INFRA);
        register(53, "UDP", "DNS", "Domain Name System", ServiceCategory.INFRA);
        register(2375, "TCP", "Docker", "Docker API", ServiceCategory.INFRA);
        register(2376, "TCP", "Docker-TLS", "Docker TLS API", ServiceCategory.INFRA);
        register(6443, "TCP", "K8s-API", "Kubernetes API", ServiceCategory.INFRA);
        register(10250, "TCP", "Kubelet", "Kubernetes Kubelet", ServiceCategory.INFRA);
        register(2379, "TCP", "etcd", "etcd Client", ServiceCategory.INFRA);
        register(2380, "TCP", "etcd-Peer", "etcd Peer", ServiceCategory.INFRA);
        register(8500, "TCP", "Consul", "HashiCorp Consul", ServiceCategory.INFRA);
        register(8600, "TCP", "Consul-DNS", "Consul DNS", ServiceCategory.INFRA);
        register(8200, "TCP", "Vault", "HashiCorp Vault", ServiceCategory.INFRA);

        // Monitoring
        register(9090, "TCP", "Prometheus", "Prometheus Server", ServiceCategory.MONITORING);
        register(3000, "TCP", "Grafana", "Grafana Dashboard", ServiceCategory.MONITORING);
        register(9093, "TCP", "Alertmanager", "Prometheus Alertmanager", ServiceCategory.MONITORING);
        register(9100, "TCP", "Node-Exporter", "Prometheus Node Exporter", ServiceCategory.MONITORING);
        register(8086, "TCP", "InfluxDB", "InfluxDB Time Series", ServiceCategory.MONITORING);
        register(4317, "TCP", "OTLP-gRPC", "OpenTelemetry gRPC", ServiceCategory.MONITORING);
        register(4318, "TCP", "OTLP-HTTP", "OpenTelemetry HTTP", ServiceCategory.MONITORING);
        register(16686, "TCP", "Jaeger", "Jaeger UI", ServiceCategory.MONITORING);
        register(14268, "TCP", "Jaeger-Collector", "Jaeger Collector", ServiceCategory.MONITORING);
        register(9411, "TCP", "Zipkin", "Zipkin Tracing", ServiceCategory.MONITORING);

        // Development/Debug
        register(5005, "TCP", "Java-Debug", "Java Debug Port", ServiceCategory.DEV);
        register(9229, "TCP", "Node-Debug", "Node.js Debug", ServiceCategory.DEV);
        register(5858, "TCP", "Node-Debug-Old", "Node.js Debug (Legacy)", ServiceCategory.DEV);
        register(35729, "TCP", "LiveReload", "LiveReload Server", ServiceCategory.DEV);
        register(6006, "TCP", "TensorBoard", "TensorFlow TensorBoard", ServiceCategory.DEV);
        register(8888, "TCP", "Jupyter", "Jupyter Notebook", ServiceCategory.DEV);

        // Security
        register(636, "TCP", "LDAPS", "LDAP over SSL", ServiceCategory.SECURITY);
        register(389, "TCP", "LDAP", "LDAP Directory", ServiceCategory.SECURITY);
        register(88, "TCP", "Kerberos", "Kerberos Auth", ServiceCategory.SECURITY);
        register(464, "TCP", "Kerberos-Change", "Kerberos Password", ServiceCategory.SECURITY);

        // Other common services
        register(1099, "TCP", "RMI", "Java RMI Registry", ServiceCategory.DEV);
        register(8081, "TCP", "HTTP-Alt-3", "Alternative HTTP", ServiceCategory.WEB);
        register(8082, "TCP", "HTTP-Alt-4", "Alternative HTTP", ServiceCategory.WEB);
        register(9000, "TCP", "SonarQube", "SonarQube Server", ServiceCategory.DEV);
        register(8761, "TCP", "Eureka", "Netflix Eureka", ServiceCategory.INFRA);
        register(8888, "TCP", "Config-Server", "Spring Cloud Config", ServiceCategory.INFRA);
        register(5000, "TCP", "Docker-Registry", "Docker Registry", ServiceCategory.INFRA);
        register(5001, "TCP", "Registry-Alt", "Registry Alternative", ServiceCategory.INFRA);
    }

    private static void register(int port, String protocol, String name, String description, ServiceCategory category) {
        ServiceInfo info = new ServiceInfo(name, description, category);
        if ("TCP".equalsIgnoreCase(protocol)) {
            TCP_SERVICES.put(port, info);
        } else if ("UDP".equalsIgnoreCase(protocol)) {
            UDP_SERVICES.put(port, info);
        }
    }

    /**
     * Looks up service info for a port and protocol.
     */
    public Optional<ServiceInfo> lookup(int port, String protocol) {
        if ("TCP".equalsIgnoreCase(protocol)) {
            return Optional.ofNullable(TCP_SERVICES.get(port));
        } else if ("UDP".equalsIgnoreCase(protocol)) {
            return Optional.ofNullable(UDP_SERVICES.get(port));
        }
        return Optional.empty();
    }

    /**
     * Looks up service info for a TCP port.
     */
    public Optional<ServiceInfo> lookupTcp(int port) {
        return Optional.ofNullable(TCP_SERVICES.get(port));
    }

    /**
     * Looks up service info for a UDP port.
     */
    public Optional<ServiceInfo> lookupUdp(int port) {
        return Optional.ofNullable(UDP_SERVICES.get(port));
    }

    /**
     * Returns all known TCP ports.
     */
    public Set<Integer> allTcpPorts() {
        return Collections.unmodifiableSet(TCP_SERVICES.keySet());
    }

    /**
     * Returns all known UDP ports.
     */
    public Set<Integer> allUdpPorts() {
        return Collections.unmodifiableSet(UDP_SERVICES.keySet());
    }

    /**
     * Returns services by category.
     */
    public List<Map.Entry<Integer, ServiceInfo>> byCategory(ServiceCategory category) {
        List<Map.Entry<Integer, ServiceInfo>> result = new ArrayList<>();
        for (var entry : TCP_SERVICES.entrySet()) {
            if (entry.getValue().category() == category) {
                result.add(entry);
            }
        }
        result.sort(Comparator.comparingInt(Map.Entry::getKey));
        return result;
    }

    /**
     * Checks if a port is a known development port.
     */
    public boolean isDevelopmentPort(int port) {
        ServiceInfo info = TCP_SERVICES.get(port);
        return info != null && info.category() == ServiceCategory.DEV;
    }

    /**
     * Checks if a port is a known database port.
     */
    public boolean isDatabasePort(int port) {
        ServiceInfo info = TCP_SERVICES.get(port);
        return info != null && info.category() == ServiceCategory.DATABASE;
    }

    /**
     * Checks if a port is a debug port.
     */
    public boolean isDebugPort(int port) {
        return port == 5005 || port == 9229 || port == 5858;
    }

    /**
     * Returns common development ports.
     */
    public List<Integer> developmentPorts() {
        return List.of(
            3000, 3001, 4200, 5173, 5174, 8080, 8000, 8081, 8082,
            5005, 9229, 35729, 8888, 9000
        );
    }

    /**
     * Returns common database ports.
     */
    public List<Integer> databasePorts() {
        return List.of(
            3306, 5432, 27017, 6379, 1521, 1433, 5984, 9042
        );
    }
}
