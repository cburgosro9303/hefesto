//! Process Explorer view model.
//!
//! Manages process list data, sorting, filtering, and auto-refresh
//! for the process explorer table view.

use std::sync::{Arc, Mutex};

use hefesto_domain::procwatch::process_sample::{ProcessSample, ProcessState};
use hefesto_platform::port_parser::PortParser;
use hefesto_platform::process_sampler::ProcessSampler;
use slint::{ComponentHandle, ModelRc, SharedString, VecModel};

use crate::AppWindow;
use crate::ProcessEntry;

/// Sort mode for the process list.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SortMode {
    Cpu,
    Memory,
    Pid,
    Name,
}

/// Shared state for the process explorer.
struct ProcessState_ {
    all_processes: Vec<ProcessSample>,
    search_text: String,
    sort_mode: SortMode,
}

impl ProcessState_ {
    fn new() -> Self {
        Self {
            all_processes: Vec::new(),
            search_text: String::new(),
            sort_mode: SortMode::Cpu,
        }
    }

    fn filtered_sorted(&self) -> Vec<&ProcessSample> {
        let mut filtered: Vec<&ProcessSample> = self
            .all_processes
            .iter()
            .filter(|p| {
                if self.search_text.is_empty() {
                    true
                } else {
                    let lower = self.search_text.to_lowercase();
                    p.name.to_lowercase().contains(&lower)
                        || p.pid.to_string().contains(&lower)
                        || p.user.to_lowercase().contains(&lower)
                }
            })
            .collect();

        match self.sort_mode {
            SortMode::Cpu => {
                filtered.sort_by(|a, b| {
                    b.cpu
                        .percent_instant
                        .partial_cmp(&a.cpu.percent_instant)
                        .unwrap_or(std::cmp::Ordering::Equal)
                });
            }
            SortMode::Memory => {
                filtered.sort_by(|a, b| b.memory.rss_bytes.cmp(&a.memory.rss_bytes));
            }
            SortMode::Pid => {
                filtered.sort_by(|a, b| a.pid.cmp(&b.pid));
            }
            SortMode::Name => {
                filtered.sort_by(|a, b| a.name.to_lowercase().cmp(&b.name.to_lowercase()));
            }
        }

        filtered
    }
}

fn state_to_severity(state: &ProcessState) -> i32 {
    match state {
        ProcessState::Running => 1,  // success
        ProcessState::Zombie => 3,   // critical
        ProcessState::Stopped => 2,  // warning
        _ => 0,                      // info
    }
}

fn processes_to_entries(procs: &[&ProcessSample]) -> Vec<ProcessEntry> {
    procs
        .iter()
        .map(|p| ProcessEntry {
            pid: SharedString::from(p.pid.to_string()),
            name: SharedString::from(&p.name),
            state: SharedString::from(p.state.description()),
            state_severity: state_to_severity(&p.state),
            cpu: SharedString::from(format!("{:.1}", p.cpu.percent_instant)),
            memory: SharedString::from(format!("{:.1}", p.memory.percent_of_total)),
            rss: SharedString::from(p.memory.rss_formatted()),
            threads: SharedString::from(p.thread_count.to_string()),
            user: SharedString::from(&p.user),
        })
        .collect()
}

fn update_ui(weak: &slint::Weak<AppWindow>, state: &ProcessState_) {
    let filtered = state.filtered_sorted();
    let total = state.all_processes.len();
    let total_cpu: f64 = state
        .all_processes
        .iter()
        .map(|p| p.cpu.percent_instant)
        .sum();
    let total_mem: f64 = state
        .all_processes
        .iter()
        .map(|p| p.memory.percent_of_total)
        .sum();

    let entries = processes_to_entries(&filtered);

    let total_str = total.to_string();
    let cpu_str = format!("{:.1}%", total_cpu);
    let mem_str = format!("{:.1}%", total_mem);

    let _ = weak.upgrade_in_event_loop(move |w| {
        w.set_proc_total_processes(SharedString::from(&total_str));
        w.set_proc_system_cpu(SharedString::from(&cpu_str));
        w.set_proc_system_memory(SharedString::from(&mem_str));

        let model = VecModel::from(entries);
        w.set_proc_processes(ModelRc::new(model));
    });
}

/// Wires process explorer callbacks and triggers initial data load.
pub fn setup_process_explorer(
    window: &AppWindow,
    process_sampler: Arc<dyn ProcessSampler>,
    port_parser: Arc<dyn PortParser>,
) {
    let state = Arc::new(Mutex::new(ProcessState_::new()));

    // Refresh callback
    {
        let weak = window.as_weak();
        let ps = Arc::clone(&process_sampler);
        let state = Arc::clone(&state);

        window.on_proc_refresh_requested(move || {
            let weak = weak.clone();
            let ps = Arc::clone(&ps);
            let state = Arc::clone(&state);

            tokio::spawn(async move {
                let procs = tokio::task::spawn_blocking(move || {
                    ps.get_all_processes().unwrap_or_default()
                })
                .await
                .unwrap_or_default();

                {
                    let mut s = state.lock().unwrap();
                    s.all_processes = procs;
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

        window.on_proc_search_changed(move |text| {
            let mut s = state.lock().unwrap();
            s.search_text = text.to_string();
            update_ui(&weak, &s);
        });
    }

    // Sort callback
    {
        let weak = window.as_weak();
        let state = Arc::clone(&state);

        window.on_proc_sort_changed(move |sort_value| {
            let mut s = state.lock().unwrap();
            let sort_str = sort_value.as_str();
            s.sort_mode = if sort_str.contains("CPU") || sort_str.contains("cpu") {
                SortMode::Cpu
            } else if sort_str.contains("Memory") || sort_str.contains("memory") || sort_str.contains("Mem") {
                SortMode::Memory
            } else if sort_str == "PID" {
                SortMode::Pid
            } else {
                SortMode::Name
            };
            update_ui(&weak, &s);
        });
    }

    // Row selection callback
    {
        window.on_proc_row_selected(move |idx| {
            tracing::debug!("Process row selected: {}", idx);
        });
    }

    // Double-click callback (navigate to monitor)
    {
        let weak = window.as_weak();
        let state = Arc::clone(&state);

        window.on_proc_row_double_clicked(move |idx| {
            let s = state.lock().unwrap();
            let filtered = s.filtered_sorted();
            if let Some(proc) = filtered.get(idx as usize) {
                let pid_str = proc.pid.to_string();
                let _ = weak.upgrade_in_event_loop(move |w| {
                    w.set_mon_pid_input(SharedString::from(&pid_str));
                    w.set_active_view(3); // switch to process monitor
                });
            }
        });
    }

    // Auto-refresh toggle
    {
        window.on_proc_auto_refresh_toggled(move |enabled| {
            tracing::info!("Process auto-refresh toggled: {}", enabled);
        });
    }

    // Kill process callback
    {
        let weak = window.as_weak();
        let pp = Arc::clone(&port_parser);
        let ps_kill = Arc::clone(&process_sampler);
        let state_kill = Arc::clone(&state);

        window.on_proc_kill_requested(move |idx| {
            let pid_to_kill: Option<u32> = {
                let s = state_kill.lock().unwrap();
                let filtered = s.filtered_sorted();
                filtered
                    .get(idx as usize)
                    .map(|p| p.pid)
            };

            if let Some(pid) = pid_to_kill {
                tracing::info!("Terminate process requested for PID: {}", pid);
                let pp = Arc::clone(&pp);
                let ps = Arc::clone(&ps_kill);
                let weak = weak.clone();
                let state = Arc::clone(&state_kill);

                tokio::spawn(async move {
                    let pp_clone = Arc::clone(&pp);
                    let result = tokio::task::spawn_blocking(move || {
                        pp_clone.kill_process(pid, false)
                    })
                    .await;

                    match result {
                        Ok(Ok(true)) => {
                            tracing::info!("Process {} terminated successfully", pid);
                        }
                        _ => {
                            tracing::warn!("Failed to terminate process {}", pid);
                        }
                    }

                    // Refresh the process list after kill
                    let ps_clone = Arc::clone(&ps);
                    let procs = tokio::task::spawn_blocking(move || {
                        ps_clone.get_all_processes().unwrap_or_default()
                    })
                    .await
                    .unwrap_or_default();

                    {
                        let mut s = state.lock().unwrap();
                        s.all_processes = procs;
                    }

                    let s = state.lock().unwrap();
                    update_ui(&weak, &s);
                });
            }
        });
    }

    // Export callback (placeholder)
    {
        window.on_proc_export_requested(move || {
            tracing::info!("Process CSV export requested");
        });
    }

    // Initial load
    {
        let weak = window.as_weak();
        let ps = Arc::clone(&process_sampler);
        let state = Arc::clone(&state);

        tokio::spawn(async move {
            let procs = tokio::task::spawn_blocking(move || {
                ps.get_all_processes().unwrap_or_default()
            })
            .await
            .unwrap_or_default();

            {
                let mut s = state.lock().unwrap();
                s.all_processes = procs;
            }

            let s = state.lock().unwrap();
            update_ui(&weak, &s);
        });
    }
}
