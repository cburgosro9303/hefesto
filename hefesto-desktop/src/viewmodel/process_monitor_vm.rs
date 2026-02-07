//! Process Monitor view model.
//!
//! Manages monitoring of a single process by PID or name,
//! with periodic sampling and UI updates.

use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;

use hefesto_domain::procwatch::process_sample::ProcessState;
use hefesto_platform::process_sampler::ProcessSampler;
use slint::{ComponentHandle, SharedString};

use crate::AppWindow;

fn state_to_severity(state: &ProcessState) -> i32 {
    match state {
        ProcessState::Running => 1,
        ProcessState::Zombie => 3,
        ProcessState::Stopped => 2,
        _ => 0,
    }
}

/// Wires process monitor callbacks.
pub fn setup_process_monitor(
    window: &AppWindow,
    process_sampler: Arc<dyn ProcessSampler>,
) {
    let monitoring = Arc::new(AtomicBool::new(false));

    // Start monitoring by PID
    {
        let weak = window.as_weak();
        let ps = Arc::clone(&process_sampler);
        let monitoring = Arc::clone(&monitoring);

        window.on_mon_start_by_pid(move || {
            let weak = weak.clone();
            let ps = Arc::clone(&ps);
            let monitoring = Arc::clone(&monitoring);

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

            monitoring.store(true, Ordering::SeqCst);

            let _ = weak.upgrade_in_event_loop(move |w| {
                w.set_mon_monitoring(true);
                w.set_mon_error_message(SharedString::default());
            });

            tokio::spawn(async move {
                loop {
                    if !monitoring.load(Ordering::SeqCst) {
                        break;
                    }

                    let ps_clone = Arc::clone(&ps);
                    let sample = tokio::task::spawn_blocking(move || {
                        ps_clone.sample_by_pid(pid)
                    })
                    .await;

                    let weak_clone = weak.clone();
                    let monitoring_clone = Arc::clone(&monitoring);

                    match sample {
                        Ok(Ok(Some(s))) => {
                            let _ = weak_clone.upgrade_in_event_loop(move |w| {
                                w.set_mon_pid_display(SharedString::from(s.pid.to_string()));
                                w.set_mon_process_name(SharedString::from(&s.name));
                                w.set_mon_thread_count(SharedString::from(
                                    s.thread_count.to_string(),
                                ));
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
                                w.set_mon_error_message(SharedString::from(
                                    "Error sampling process",
                                ));
                            });
                            break;
                        }
                    }

                    tokio::time::sleep(std::time::Duration::from_secs(2)).await;
                }
            });
        });
    }

    // Start monitoring by name
    {
        let weak = window.as_weak();
        let ps = Arc::clone(&process_sampler);
        let monitoring = Arc::clone(&monitoring);

        window.on_mon_start_by_name(move || {
            let weak = weak.clone();
            let ps = Arc::clone(&ps);
            let monitoring = Arc::clone(&monitoring);

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

            tokio::spawn(async move {
                let result = tokio::task::spawn_blocking(move || {
                    ps_clone.sample_by_name(&name)
                })
                .await;

                match result {
                    Ok(Ok(procs)) if !procs.is_empty() => {
                        let pid = procs[0].pid;
                        let pid_str = pid.to_string();

                        let _ = weak_clone.upgrade_in_event_loop(move |w| {
                            w.set_mon_pid_input(SharedString::from(&pid_str));
                        });

                        monitoring_clone.store(true, Ordering::SeqCst);
                        let _ = weak_clone.upgrade_in_event_loop(|w| {
                            w.set_mon_monitoring(true);
                            w.set_mon_error_message(SharedString::default());
                        });

                        let ps2 = Arc::clone(&ps);
                        loop {
                            if !monitoring_clone.load(Ordering::SeqCst) {
                                break;
                            }

                            let ps3 = Arc::clone(&ps2);
                            let sample = tokio::task::spawn_blocking(move || {
                                ps3.sample_by_pid(pid)
                            })
                            .await;

                            let wc = weak_clone.clone();
                            let mc = Arc::clone(&monitoring_clone);

                            match sample {
                                Ok(Ok(Some(s))) => {
                                    let _ = wc.upgrade_in_event_loop(move |w| {
                                        w.set_mon_pid_display(SharedString::from(
                                            s.pid.to_string(),
                                        ));
                                        w.set_mon_process_name(SharedString::from(&s.name));
                                        w.set_mon_thread_count(SharedString::from(
                                            s.thread_count.to_string(),
                                        ));
                                        w.set_mon_uptime(SharedString::from(
                                            s.uptime_formatted(),
                                        ));
                                        w.set_mon_io_read(SharedString::from(
                                            s.io.read_formatted(),
                                        ));
                                        w.set_mon_io_write(SharedString::from(
                                            s.io.write_formatted(),
                                        ));
                                        w.set_mon_cpu_percent(SharedString::from(format!(
                                            "{:.1}%",
                                            s.cpu.percent_instant
                                        )));
                                        w.set_mon_mem_percent(SharedString::from(format!(
                                            "{:.1}%",
                                            s.memory.percent_of_total
                                        )));
                                        w.set_mon_state_text(SharedString::from(
                                            s.state.description(),
                                        ));
                                        w.set_mon_state_severity(state_to_severity(&s.state));
                                    });
                                }
                                _ => {
                                    mc.store(false, Ordering::SeqCst);
                                    let _ = wc.upgrade_in_event_loop(|w| {
                                        w.set_mon_monitoring(false);
                                        w.set_mon_error_message(SharedString::from(
                                            "Process terminated or not found",
                                        ));
                                    });
                                    break;
                                }
                            }

                            tokio::time::sleep(std::time::Duration::from_secs(2)).await;
                        }
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
                            w.set_mon_error_message(SharedString::from("Error searching processes"));
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
