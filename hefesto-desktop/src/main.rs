//! HEFESTO Desktop - Main entry point.
//!
//! Initializes the Slint UI, creates shared services, wires view model
//! callbacks, applies i18n labels, starts the clock timer, and runs
//! the event loop.

mod i18n;
mod services;
mod viewmodel;

use std::sync::Arc;

use slint::ComponentHandle;

slint::include_modules!();

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    // Initialize tracing
    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "hefesto_desktop=info".into()),
        )
        .init();

    tracing::info!("Starting HEFESTO Desktop");

    // Create shared services
    let factory = Arc::new(services::ServiceFactory::new(i18n::Language::English));

    // Create the Slint window
    let window = AppWindow::new()?;

    // Apply initial i18n labels
    {
        let svc = factory.i18n.lock().unwrap();
        viewmodel::preferences_vm::apply_i18n_labels(&window.as_weak(), &svc);
    }

    // Wire up view models
    viewmodel::dashboard_vm::setup_dashboard(
        &window,
        factory.port_parser(),
        factory.process_sampler(),
    );

    viewmodel::network_explorer_vm::setup_network_explorer(
        &window,
        factory.port_parser(),
    );

    viewmodel::process_explorer_vm::setup_process_explorer(
        &window,
        factory.process_sampler(),
    );

    viewmodel::process_monitor_vm::setup_process_monitor(
        &window,
        factory.process_sampler(),
    );

    viewmodel::tools_vm::setup_tools(&window);

    viewmodel::preferences_vm::setup_preferences(
        &window,
        factory.i18n(),
    );

    // Start clock timer (updates every second)
    {
        let weak = window.as_weak();
        tokio::spawn(async move {
            loop {
                let now = chrono::Local::now();
                let time_str = now.format("%H:%M:%S").to_string();

                let _ = weak.upgrade_in_event_loop(move |w| {
                    w.set_clock_text(slint::SharedString::from(&time_str));
                });

                tokio::time::sleep(std::time::Duration::from_secs(1)).await;
            }
        });
    }

    // Run the event loop
    window.run()?;

    tracing::info!("HEFESTO Desktop shutting down");
    Ok(())
}
