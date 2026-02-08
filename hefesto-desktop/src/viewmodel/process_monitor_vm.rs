//! Process Monitor view model.
//!
//! Manages monitoring of a single process by PID or name,
//! with periodic sampling, UI updates, and CPU/Memory history
//! for graphing (circular buffer of last 60 samples).

use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};

use hefesto_domain::procwatch::process_sample::ProcessState;
use hefesto_platform::process_sampler::ProcessSampler;
use slint::{ComponentHandle, ModelRc, SharedString, VecModel};

use crate::{AppWindow, GraphPoint};

const MAX_HISTORY: usize = 60;

/// Circular buffer for graph history data.
struct HistoryBuffer {
    cpu_samples: Vec<f32>,
    mem_samples: Vec<f32>,
}

impl HistoryBuffer {
    fn new() -> Self {
        Self {
            cpu_samples: Vec::with_capacity(MAX_HISTORY),
            mem_samples: Vec::with_capacity(MAX_HISTORY),
        }
    }

    fn push(&mut self, cpu: f32, mem: f32) {
        if self.cpu_samples.len() >= MAX_HISTORY {
            self.cpu_samples.remove(0);
            self.mem_samples.remove(0);
        }
        self.cpu_samples.push(cpu);
        self.mem_samples.push(mem);
    }

    fn clear(&mut self) {
        self.cpu_samples.clear();
        self.mem_samples.clear();
    }

    fn cpu_graph_points(&self) -> Vec<GraphPoint> {
        self.cpu_samples
            .iter()
            .map(|&v| GraphPoint { value: v })
            .collect()
    }

    fn mem_graph_points(&self) -> Vec<GraphPoint> {
        self.mem_samples
            .iter()
            .map(|&v| GraphPoint { value: v })
            .collect()
    }
}

fn state_to_severity(state: &ProcessState) -> i32 {
    match state {
        ProcessState::Running => 1,
        ProcessState::Zombie => 3,
        ProcessState::Stopped => 2,
        _ => 0,
    }
}

fn update_history_ui(weak: &slint::Weak<AppWindow>, history: &HistoryBuffer) {
    let cpu_points = history.cpu_graph_points();
    let mem_points = history.mem_graph_points();

    let _ = weak.upgrade_in_event_loop(move |w| {
        w.set_mon_cpu_history(ModelRc::new(VecModel::from(cpu_points)));
        w.set_mon_mem_history(ModelRc::new(VecModel::from(mem_points)));
    });
}

/// Shared monitoring loop that samples by PID and updates UI + history.
fn spawn_monitor_loop(
    weak: slint::Weak<AppWindow>,
    ps: Arc<dyn ProcessSampler>,
    monitoring: Arc<AtomicBool>,
    history: Arc<Mutex<HistoryBuffer>>,
    pid: u32,
) {
    tokio::spawn(async move {
        loop {
            if !monitoring.load(Ordering::SeqCst) {
                break;
            }

            let ps_clone = Arc::clone(&ps);
            let sample = tokio::task::spawn_blocking(move || ps_clone.sample_by_pid(pid)).await;

            let weak_clone = weak.clone();
            let monitoring_clone = Arc::clone(&monitoring);
            let history_clone = Arc::clone(&history);

            match sample {
                Ok(Ok(Some(s))) => {
                    let cpu_val = s.cpu.percent_instant as f32;
                    let mem_val = s.memory.percent_of_total as f32;

                    // Update history buffer
                    {
                        let mut h = history_clone.lock().unwrap();
                        h.push(cpu_val, mem_val);
                        update_history_ui(&weak_clone, &h);
                    }

                    let _ = weak_clone.upgrade_in_event_loop(move |w| {
                        w.set_mon_pid_display(SharedString::from(s.pid.to_string()));
                        w.set_mon_process_name(SharedString::from(&s.name));
                        w.set_mon_thread_count(SharedString::from(s.thread_count.to_string()));
                        w.set_mon_uptime(SharedString::from(s.uptime_formatted()));
                        w.set_mon_io_read(SharedString::from(s.io.read_formatted()));
                        w.set_mon_io_write(SharedString::from(s.io.write_formatted()));
                        w.set_mon_cpu_percent(SharedString::from(format!(
                            "{:.1}%",
                            s.cpu.percent_instant
                        )));
                        w.set_mon_mem_percent(SharedString::from(format!(
                            "{:.1}%",
                            s.memory.percent_of_total
                        )));
                        w.set_mon_state_text(SharedString::from(s.state.description()));
                        w.set_mon_state_severity(state_to_severity(&s.state));
                        w.set_mon_error_message(SharedString::default());
                    });
                }
                Ok(Ok(None)) => {
                    monitoring_clone.store(false, Ordering::SeqCst);
                    let _ = weak_clone.upgrade_in_event_loop(|w| {
                        w.set_mon_monitoring(false);
                        w.set_mon_error_message(SharedString::from(
                            "Process not found or has terminated",
                        ));
                    });
                    break;
                }
                _ => {
                    monitoring_clone.store(false, Ordering::SeqCst);
                    let _ = weak_clone.upgrade_in_event_loop(|w| {
                        w.set_mon_monitoring(false);
                        w.set_mon_error_message(SharedString::from("Error sampling process"));
                    });
                    break;
                }
            }

            tokio::time::sleep(std::time::Duration::from_secs(2)).await;
        }
    });
}

/// Wires process monitor callbacks.
pub fn setup_process_monitor(window: &AppWindow, process_sampler: Arc<dyn ProcessSampler>) {
    let monitoring = Arc::new(AtomicBool::new(false));
    let history = Arc::new(Mutex::new(HistoryBuffer::new()));

    // Start monitoring by PID
    {
        let weak = window.as_weak();
        let ps = Arc::clone(&process_sampler);
        let monitoring = Arc::clone(&monitoring);
        let history = Arc::clone(&history);

        window.on_mon_start_by_pid(move || {
            let weak = weak.clone();
            let ps = Arc::clone(&ps);
            let monitoring = Arc::clone(&monitoring);
            let history = Arc::clone(&history);

            // Read the PID from the UI (we're on the UI thread in a callback)
            let pid_str = weak
                .upgrade()
                .map(|w| w.get_mon_pid_input().to_string())
                .unwrap_or_default();

            let pid: u32 = match pid_str.trim().parse() {
                Ok(p) => p,
                Err(_) => {
                    let _ = weak.upgrade_in_event_loop(|w| {
                        w.set_mon_error_message(SharedString::from("Invalid PID"));
                    });
                    return;
                }
            };

            // Clear history for new monitoring session
            {
                let mut h = history.lock().unwrap();
                h.clear();
            }

            monitoring.store(true, Ordering::SeqCst);

            let _ = weak.upgrade_in_event_loop(move |w| {
                w.set_mon_monitoring(true);
                w.set_mon_error_message(SharedString::default());
            });

            spawn_monitor_loop(weak, ps, monitoring, history, pid);
        });
    }

    // Start monitoring by name
    {
        let weak = window.as_weak();
        let ps = Arc::clone(&process_sampler);
        let monitoring = Arc::clone(&monitoring);
        let history = Arc::clone(&history);

        window.on_mon_start_by_name(move || {
            let weak = weak.clone();
            let ps = Arc::clone(&ps);
            let monitoring = Arc::clone(&monitoring);
            let history = Arc::clone(&history);

            // Read name from UI (we're on the UI thread)
            let name = weak
                .upgrade()
                .map(|w| w.get_mon_name_input().to_string())
                .unwrap_or_default();

            if name.trim().is_empty() {
                let _ = weak.upgrade_in_event_loop(|w| {
                    w.set_mon_error_message(SharedString::from("Enter a process name"));
                });
                return;
            }

            let ps_clone = Arc::clone(&ps);
            let weak_clone = weak.clone();
            let monitoring_clone = Arc::clone(&monitoring);
            let history_clone = Arc::clone(&history);

            tokio::spawn(async move {
                let result =
                    tokio::task::spawn_blocking(move || ps_clone.sample_by_name(&name)).await;

                match result {
                    Ok(Ok(procs)) if !procs.is_empty() => {
                        let pid = procs[0].pid;
                        let pid_str = pid.to_string();

                        let _ = weak_clone.upgrade_in_event_loop(move |w| {
                            w.set_mon_pid_input(SharedString::from(&pid_str));
                        });

                        // Clear history for new monitoring session
                        {
                            let mut h = history_clone.lock().unwrap();
                            h.clear();
                        }

                        monitoring_clone.store(true, Ordering::SeqCst);
                        let _ = weak_clone.upgrade_in_event_loop(|w| {
                            w.set_mon_monitoring(true);
                            w.set_mon_error_message(SharedString::default());
                        });

                        spawn_monitor_loop(weak_clone, ps, monitoring_clone, history_clone, pid);
                    }
                    Ok(Ok(_)) => {
                        let _ = weak_clone.upgrade_in_event_loop(|w| {
                            w.set_mon_error_message(SharedString::from(
                                "No process found with that name",
                            ));
                        });
                    }
                    _ => {
                        let _ = weak_clone.upgrade_in_event_loop(|w| {
                            w.set_mon_error_message(SharedString::from(
                                "Error searching processes",
                            ));
                        });
                    }
                }
            });
        });
    }

    // Stop monitoring
    {
        let weak = window.as_weak();
        let monitoring = Arc::clone(&monitoring);

        window.on_mon_stop_monitoring(move || {
            monitoring.store(false, Ordering::SeqCst);
            let _ = weak.upgrade_in_event_loop(|w| {
                w.set_mon_monitoring(false);
            });
        });
    }

    // Kill process
    {
        let weak = window.as_weak();

        window.on_mon_kill_process(move || {
            // Read PID from UI (we're on the UI thread)
            let pid_str = weak
                .upgrade()
                .map(|w| w.get_mon_pid_display().to_string())
                .unwrap_or_default();

            if let Ok(pid) = pid_str.parse::<u32>() {
                tracing::info!("Kill process requested for PID: {}", pid);
            }
        });
    }
}
