//! Dashboard view model.
//!
//! Aggregates summary data from both port and process services
//! to populate the dashboard KPI cards.

use std::sync::Arc;

use hefesto_platform::port_parser::PortParser;
use hefesto_platform::process_sampler::ProcessSampler;
use slint::ComponentHandle;

use crate::AppWindow;

/// Dashboard data snapshot used to update the UI.
pub struct DashboardData {
    pub total_listening: String,
    pub tcp_count: String,
    pub udp_count: String,
    pub exposed_count: String,
    pub total_processes: String,
    pub system_cpu: String,
    pub system_memory: String,
}

/// Loads dashboard data from platform services.
pub fn load_dashboard_data(
    port_parser: &dyn PortParser,
    process_sampler: &dyn ProcessSampler,
) -> DashboardData {
    let mut total_listening = 0usize;
    let mut tcp = 0usize;
    let mut udp = 0usize;
    let mut exposed = 0usize;

    if let Ok(bindings) = port_parser.find_all(true, true) {
        for b in &bindings {
            if matches!(
                b.state,
                hefesto_domain::portinfo::port_binding::ConnectionState::Listen
            ) {
                total_listening += 1;
            }
            match b.protocol {
                hefesto_domain::portinfo::port_binding::Protocol::Tcp => tcp += 1,
                hefesto_domain::portinfo::port_binding::Protocol::Udp => udp += 1,
            }
            let addr = &b.local_address;
            if addr == "0.0.0.0" || addr == "::" || addr == "*" {
                exposed += 1;
            }
        }
    }

    let mut total_processes = 0usize;
    let mut total_cpu = 0.0f64;
    let mut total_mem = 0.0f64;

    if let Ok(procs) = process_sampler.get_all_processes() {
        total_processes = procs.len();
        for p in &procs {
            total_cpu += p.cpu.percent_instant;
            total_mem += p.memory.percent_of_total;
        }
    }

    DashboardData {
        total_listening: total_listening.to_string(),
        tcp_count: tcp.to_string(),
        udp_count: udp.to_string(),
        exposed_count: exposed.to_string(),
        total_processes: total_processes.to_string(),
        system_cpu: format!("{:.1}%", total_cpu),
        system_memory: format!("{:.1}%", total_mem),
    }
}

/// Wires dashboard callbacks and triggers initial data load.
pub fn setup_dashboard(
    window: &AppWindow,
    port_parser: Arc<dyn PortParser>,
    process_sampler: Arc<dyn ProcessSampler>,
) {
    let weak = window.as_weak();
    let pp = Arc::clone(&port_parser);
    let ps = Arc::clone(&process_sampler);

    // Callback for refresh
    window.on_dash_refresh_requested(move || {
        let weak = weak.clone();
        let pp = Arc::clone(&pp);
        let ps = Arc::clone(&ps);

        tokio::spawn(async move {
            let data =
                tokio::task::spawn_blocking(move || load_dashboard_data(pp.as_ref(), ps.as_ref()))
                    .await;

            if let Ok(data) = data {
                let _ = weak.upgrade_in_event_loop(move |w| {
                    w.set_dash_total_ports(data.total_listening.into());
                    w.set_dash_tcp_count(data.tcp_count.into());
                    w.set_dash_udp_count(data.udp_count.into());
                    w.set_dash_exposed_ports(data.exposed_count.into());
                    w.set_dash_total_processes(data.total_processes.into());
                    w.set_dash_system_cpu(data.system_cpu.into());
                    w.set_dash_system_memory(data.system_memory.into());
                });
            }
        });
    });

    // Initial load
    let weak = window.as_weak();
    let pp = Arc::clone(&port_parser);
    let ps = Arc::clone(&process_sampler);

    tokio::spawn(async move {
        let data =
            tokio::task::spawn_blocking(move || load_dashboard_data(pp.as_ref(), ps.as_ref()))
                .await;

        if let Ok(data) = data {
            let _ = weak.upgrade_in_event_loop(move |w| {
                w.set_dash_total_ports(data.total_listening.into());
                w.set_dash_tcp_count(data.tcp_count.into());
                w.set_dash_udp_count(data.udp_count.into());
                w.set_dash_exposed_ports(data.exposed_count.into());
                w.set_dash_total_processes(data.total_processes.into());
                w.set_dash_system_cpu(data.system_cpu.into());
                w.set_dash_system_memory(data.system_memory.into());
            });
        }
    });
}
