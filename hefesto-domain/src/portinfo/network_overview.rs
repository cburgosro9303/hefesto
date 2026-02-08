use chrono::{DateTime, Utc};
use serde::Serialize;
use std::collections::HashMap;

use super::enriched_port_binding::EnrichedPortBinding;
use super::port_binding::ConnectionState;
use super::service_info::ServiceCategory;

/// Network statistics.
#[derive(Debug, Clone, Serialize)]
pub struct NetworkStatistics {
    pub total_listening: usize,
    pub total_established: usize,
    pub tcp_count: usize,
    pub udp_count: usize,
    pub exposed_count: usize,
    pub local_only_count: usize,
}

impl NetworkStatistics {
    /// Creates statistics from a list of bindings.
    pub fn from_bindings(bindings: &[EnrichedPortBinding]) -> Self {
        let mut listening = 0;
        let mut established = 0;
        let mut tcp = 0;
        let mut udp = 0;
        let mut exposed = 0;
        let mut local_only = 0;

        for b in bindings {
            match b.state() {
                ConnectionState::Listen => listening += 1,
                ConnectionState::Established => established += 1,
                _ => {}
            }

            match b.protocol() {
                super::port_binding::Protocol::Tcp => tcp += 1,
                super::port_binding::Protocol::Udp => udp += 1,
            }

            if b.is_exposed() {
                exposed += 1;
            } else if b.is_local_only() {
                local_only += 1;
            }
        }

        Self {
            total_listening: listening,
            total_established: established,
            tcp_count: tcp,
            udp_count: udp,
            exposed_count: exposed,
            local_only_count: local_only,
        }
    }

    /// Returns a formatted summary line.
    pub fn to_summary_line(&self) -> String {
        format!(
            "Listening: {} | Established: {} | TCP: {} | UDP: {} | Exposed: {} | Local: {}",
            self.total_listening,
            self.total_established,
            self.tcp_count,
            self.udp_count,
            self.exposed_count,
            self.local_only_count
        )
    }
}

/// A complete network overview with statistics.
#[derive(Debug, Clone, Serialize)]
pub struct NetworkOverview {
    pub bindings: Vec<EnrichedPortBinding>,
    pub statistics: NetworkStatistics,
    pub generated_at: DateTime<Utc>,
}

impl NetworkOverview {
    /// Creates a NetworkOverview from a list of enriched bindings.
    pub fn from_bindings(bindings: Vec<EnrichedPortBinding>) -> Self {
        let statistics = NetworkStatistics::from_bindings(&bindings);
        Self {
            bindings,
            statistics,
            generated_at: Utc::now(),
        }
    }

    /// Returns only listening ports.
    pub fn listening_ports(&self) -> Vec<&EnrichedPortBinding> {
        self.bindings
            .iter()
            .filter(|b| matches!(b.state(), ConnectionState::Listen))
            .collect()
    }

    /// Returns only exposed ports (bound to 0.0.0.0 or ::).
    pub fn exposed_ports(&self) -> Vec<&EnrichedPortBinding> {
        self.bindings.iter().filter(|b| b.is_exposed()).collect()
    }

    /// Returns only local-only ports (bound to 127.0.0.1 or ::1).
    pub fn local_only_ports(&self) -> Vec<&EnrichedPortBinding> {
        self.bindings.iter().filter(|b| b.is_local_only()).collect()
    }

    /// Returns bindings grouped by process name.
    pub fn by_process_name(&self) -> HashMap<String, Vec<&EnrichedPortBinding>> {
        let mut map: HashMap<String, Vec<&EnrichedPortBinding>> = HashMap::new();
        for b in &self.bindings {
            map.entry(b.process_name().to_string()).or_default().push(b);
        }
        map
    }

    /// Returns bindings grouped by PID.
    pub fn by_pid(&self) -> HashMap<u32, Vec<&EnrichedPortBinding>> {
        let mut map: HashMap<u32, Vec<&EnrichedPortBinding>> = HashMap::new();
        for b in &self.bindings {
            map.entry(b.pid()).or_default().push(b);
        }
        map
    }

    /// Returns bindings grouped by service category.
    pub fn by_service_category(&self) -> HashMap<ServiceCategory, Vec<&EnrichedPortBinding>> {
        let mut map: HashMap<ServiceCategory, Vec<&EnrichedPortBinding>> = HashMap::new();
        for b in &self.bindings {
            if let Some(ref si) = b.service_info {
                map.entry(si.category.clone()).or_default().push(b);
            }
        }
        map
    }

    /// Returns unique process names.
    pub fn unique_process_names(&self) -> Vec<String> {
        let mut names: Vec<String> = self
            .bindings
            .iter()
            .map(|b| b.process_name().to_string())
            .collect();
        names.sort();
        names.dedup();
        names
    }

    /// Returns unique PIDs.
    pub fn unique_pids(&self) -> Vec<u32> {
        let mut pids: Vec<u32> = self.bindings.iter().map(|b| b.pid()).collect();
        pids.sort();
        pids.dedup();
        pids
    }

    /// Filters bindings by process name (case-insensitive partial match).
    pub fn filter_by_process_name(&self, name: &str) -> Vec<&EnrichedPortBinding> {
        let lower_name = name.to_lowercase();
        self.bindings
            .iter()
            .filter(|b| b.process_name().to_lowercase().contains(&lower_name))
            .collect()
    }

    /// Returns count of Docker containers.
    pub fn docker_container_count(&self) -> usize {
        self.bindings.iter().filter(|b| b.is_docker()).count()
    }
}
