//! Network Explorer view model.
//!
//! Manages port binding data, filtering, and search for the
//! network explorer table view.

use std::sync::{Arc, Mutex};

use hefesto_domain::portinfo::port_binding::{ConnectionState, PortBinding, Protocol};
use hefesto_platform::port_parser::PortParser;
use slint::{ComponentHandle, ModelRc, SharedString, VecModel};

use crate::AppWindow;
use crate::PortEntry;

/// Shared state for the network explorer, allowing filter/search from callbacks.
struct NetworkState {
    all_bindings: Vec<PortBinding>,
    search_text: String,
    protocol_filter: String, // "ALL", "TCP", "UDP"
}

impl NetworkState {
    fn new() -> Self {
        Self {
            all_bindings: Vec::new(),
            search_text: String::new(),
            protocol_filter: "ALL".to_string(),
        }
    }

    fn filtered(&self) -> Vec<&PortBinding> {
        self.all_bindings
            .iter()
            .filter(|b| {
                // Protocol filter
                let proto_ok = match self.protocol_filter.as_str() {
                    "TCP" => matches!(b.protocol, Protocol::Tcp),
                    "UDP" => matches!(b.protocol, Protocol::Udp),
                    _ => true,
                };

                // Search filter
                let search_ok = if self.search_text.is_empty() {
                    true
                } else {
                    let lower = self.search_text.to_lowercase();
                    b.port.to_string().contains(&lower)
                        || b.process_name.to_lowercase().contains(&lower)
                        || b.local_address.to_lowercase().contains(&lower)
                };

                proto_ok && search_ok
            })
            .collect()
    }

    fn compute_stats(&self) -> (usize, usize, usize) {
        let filtered = self.filtered();
        let total = filtered
            .iter()
            .filter(|b| matches!(b.state, ConnectionState::Listen))
            .count();
        let tcp = filtered
            .iter()
            .filter(|b| matches!(b.protocol, Protocol::Tcp))
            .count();
        let udp = filtered
            .iter()
            .filter(|b| matches!(b.protocol, Protocol::Udp))
            .count();
        (total, tcp, udp)
    }
}

fn bindings_to_port_entries(bindings: &[&PortBinding]) -> Vec<PortEntry> {
    bindings
        .iter()
        .map(|b| {
            let is_exposed = b.local_address == "0.0.0.0"
                || b.local_address == "::"
                || b.local_address == "*";
            PortEntry {
                port: SharedString::from(b.port.to_string()),
                protocol: SharedString::from(b.protocol.as_str()),
                address: SharedString::from(&b.local_address),
                state: SharedString::from(b.state.as_str()),
                process_name: SharedString::from(&b.process_name),
                pid: SharedString::from(b.pid.to_string()),
                security: SharedString::from(if is_exposed { "EXPOSED" } else { "LOCAL" }),
                is_exposed,
            }
        })
        .collect()
}

fn update_ui(weak: &slint::Weak<AppWindow>, state: &NetworkState) {
    let filtered = state.filtered();
    let (total, tcp, udp) = state.compute_stats();
    let entries = bindings_to_port_entries(&filtered);

    let total_str = total.to_string();
    let tcp_str = tcp.to_string();
    let udp_str = udp.to_string();

    let _ = weak.upgrade_in_event_loop(move |w| {
        w.set_net_total_listening(SharedString::from(&total_str));
        w.set_net_tcp_count(SharedString::from(&tcp_str));
        w.set_net_udp_count(SharedString::from(&udp_str));

        let model = VecModel::from(entries);
        w.set_net_ports(ModelRc::new(model));
    });
}

/// Wires network explorer callbacks and triggers initial data load.
pub fn setup_network_explorer(
    window: &AppWindow,
    port_parser: Arc<dyn PortParser>,
) {
    let state = Arc::new(Mutex::new(NetworkState::new()));

    // Refresh callback
    {
        let weak = window.as_weak();
        let pp = Arc::clone(&port_parser);
        let state = Arc::clone(&state);

        window.on_net_refresh_requested(move || {
            let weak = weak.clone();
            let pp = Arc::clone(&pp);
            let state = Arc::clone(&state);

            tokio::spawn(async move {
                let bindings = tokio::task::spawn_blocking(move || {
                    pp.find_all(true, true).unwrap_or_default()
                })
                .await
                .unwrap_or_default();

                {
                    let mut s = state.lock().unwrap();
                    s.all_bindings = bindings;
                }

                let s = state.lock().unwrap();
                update_ui(&weak, &s);
            });
        });
    }

    // Search callback
    {
        let weak = window.as_weak();
        let state = Arc::clone(&state);

        window.on_net_search_changed(move |text| {
            let mut s = state.lock().unwrap();
            s.search_text = text.to_string();
            update_ui(&weak, &s);
        });
    }

    // Protocol filter callback
    {
        let weak = window.as_weak();
        let state = Arc::clone(&state);

        window.on_net_protocol_filter_changed(move |protocol| {
            let mut s = state.lock().unwrap();
            s.protocol_filter = protocol.to_string();
            update_ui(&weak, &s);
        });
    }

    // Row selection callback
    {
        window.on_net_row_selected(move |idx| {
            tracing::debug!("Network row selected: {}", idx);
        });
    }

    // Export callback (placeholder)
    {
        window.on_net_export_requested(move || {
            tracing::info!("Network CSV export requested");
        });
    }

    // Initial load
    {
        let weak = window.as_weak();
        let pp = Arc::clone(&port_parser);
        let state = Arc::clone(&state);

        tokio::spawn(async move {
            let bindings = tokio::task::spawn_blocking(move || {
                pp.find_all(true, true).unwrap_or_default()
            })
            .await
            .unwrap_or_default();

            {
                let mut s = state.lock().unwrap();
                s.all_bindings = bindings;
            }

            let s = state.lock().unwrap();
            update_ui(&weak, &s);
        });
    }
}
