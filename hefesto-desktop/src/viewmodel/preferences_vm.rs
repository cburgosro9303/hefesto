//! Preferences view model.
//!
//! Manages application preferences: theme (dark/light mode),
//! language selection, and auto-refresh configuration.

use std::sync::Arc;

use slint::{ComponentHandle, SharedString};

use crate::i18n::{I18nService, Language};
use crate::AppWindow;

/// Wires preferences callbacks and sets initial state.
pub fn setup_preferences(
    window: &AppWindow,
    i18n: Arc<std::sync::Mutex<I18nService>>,
) {
    // Dark mode toggle
    {
        let weak = window.as_weak();
        window.on_pref_dark_mode_toggled(move |dark| {
            tracing::info!("Theme changed to: {}", if dark { "dark" } else { "light" });
            let _ = weak.upgrade_in_event_loop(move |w| {
                w.set_pref_dark_mode(dark);
            });
        });
    }

    // Language change
    {
        let weak = window.as_weak();
        let i18n = Arc::clone(&i18n);

        window.on_pref_language_changed(move |lang_code| {
            let language = Language::from_code(lang_code.as_str());

            {
                let mut svc = i18n.lock().unwrap();
                svc.set_language(language);
            }

            tracing::info!("Language changed to: {}", lang_code);

            let svc = i18n.lock().unwrap();
            let weak = weak.clone();
            apply_i18n_labels(&weak, &svc);
        });
    }

    // Auto-refresh toggle
    {
        window.on_pref_auto_refresh_toggled(move |enabled| {
            tracing::info!("Auto-refresh toggled: {}", enabled);
        });
    }

    // Refresh interval change
    {
        window.on_pref_refresh_interval_changed(move |interval| {
            tracing::info!("Refresh interval changed to: {}s", interval);
        });
    }
}

/// Applies all i18n labels to the window properties.
pub fn apply_i18n_labels(weak: &slint::Weak<AppWindow>, i18n: &I18nService) {
    let labels = I18nLabels {
        // Sidebar
        sidebar_dashboard: i18n.t("sidebar.dashboard"),
        sidebar_network: i18n.t("portinfo.network.explorer"),
        sidebar_proc_explorer: i18n.t("procwatch.explorer"),
        sidebar_proc_monitor: i18n.t("procwatch.monitor"),
        sidebar_tools: i18n.t("tools.toolkit"),
        sidebar_preferences: i18n.t("sidebar.preferences"),
        sidebar_group_network: i18n.t("portinfo").to_uppercase(),
        sidebar_group_system: i18n.t("procwatch").to_uppercase(),
        sidebar_group_utilities: i18n.t("tools").to_uppercase(),

        // Status
        status_ready: i18n.t("status.ready"),

        // Dashboard
        dash_title: i18n.t("dashboard.title"),
        dash_label_total_ports: i18n.t("portinfo.total.listening"),
        dash_label_total_procs: i18n.t("procwatch.total.processes"),
        dash_label_sys_cpu: i18n.t("procwatch.system.cpu"),
        dash_label_sys_mem: i18n.t("procwatch.system.memory"),
        dash_label_exposed: i18n.t("portinfo.exposed"),
        dash_label_tcp: i18n.t("portinfo.tcp.count"),
        dash_label_udp: i18n.t("portinfo.udp.count"),

        // Network
        net_label_total_listening: i18n.t("portinfo.total.listening"),
        net_label_tcp_count: i18n.t("portinfo.tcp.count"),
        net_label_udp_count: i18n.t("portinfo.udp.count"),
        net_label_port: i18n.t("portinfo.port"),
        net_label_protocol: i18n.t("portinfo.protocol"),
        net_label_address: i18n.t("portinfo.address"),
        net_label_state: i18n.t("portinfo.state"),
        net_label_process: i18n.t("portinfo.process"),
        net_label_pid: i18n.t("portinfo.pid"),
        net_label_security: i18n.t("portinfo.security"),
        net_label_refresh: i18n.t("common.refresh"),
        net_label_export: i18n.t("portinfo.export.csv"),

        // Process Explorer
        proc_label_total: i18n.t("procwatch.total.processes"),
        proc_label_sys_cpu: i18n.t("procwatch.system.cpu"),
        proc_label_sys_mem: i18n.t("procwatch.system.memory"),
        proc_label_pid: i18n.t("procwatch.pid"),
        proc_label_process: i18n.t("procwatch.title"),
        proc_label_state: i18n.t("procwatch.state"),
        proc_label_cpu: i18n.t("procwatch.cpu"),
        proc_label_memory: i18n.t("procwatch.memory"),
        proc_label_rss: i18n.t("procwatch.rss"),
        proc_label_threads: i18n.t("procwatch.threads"),
        proc_label_user: i18n.t("procwatch.user"),
        proc_label_refresh: i18n.t("common.refresh"),
        proc_label_export: i18n.t("portinfo.export.csv"),
        proc_label_sort_cpu: i18n.t("procwatch.sort.cpu"),
        proc_label_sort_mem: i18n.t("procwatch.sort.memory"),
        proc_label_auto: i18n.t("procwatch.auto.refresh"),

        // Process Monitor
        mon_label_start: i18n.t("execution.start"),
        mon_label_stop: i18n.t("execution.stop"),
        mon_label_search: i18n.t("common.search").replace("...", ""),
        mon_label_kill: i18n.t("procwatch.kill.process"),
        mon_label_state: i18n.t("procwatch.state"),
        mon_label_io_read: i18n.t("procwatch.io.read"),
        mon_label_io_write: i18n.t("procwatch.io.write"),
        mon_label_search_name: i18n.t("procwatch.search.name"),

        // Tools
        tools_label_base64: i18n.t("tools.base64"),
        tools_label_json: i18n.t("tools.json"),
        tools_label_encode: i18n.t("tools.base64.encode"),
        tools_label_decode: i18n.t("tools.base64.decode"),
        tools_label_url_safe: i18n.t("tools.base64.url.safe"),
        tools_label_mime: i18n.t("tools.base64.mime"),
        tools_label_format: i18n.t("tools.json.format"),
        tools_label_compact: i18n.t("tools.json.compact"),
        tools_label_validate: i18n.t("tools.json.validate"),
        tools_label_copy: i18n.t("tools.copy"),
        tools_label_clear: i18n.t("tools.clear"),
        tools_label_input: i18n.t("tools.input"),
        tools_label_output: i18n.t("tools.output"),
        tools_label_valid: i18n.t("tools.json.valid"),
        tools_label_invalid: i18n.t("tools.json.invalid"),

        // Preferences
        pref_label_title: i18n.t("prefs.title"),
        pref_label_theme: i18n.t("prefs.theme"),
        pref_label_dark: i18n.t("prefs.theme.dark"),
        pref_label_light: i18n.t("prefs.theme.light"),
        pref_label_language: i18n.t("prefs.language"),
        pref_label_auto: i18n.t("prefs.auto.refresh"),
        pref_label_interval: i18n.t("prefs.auto.refresh.interval"),
    };

    let _ = weak.upgrade_in_event_loop(move |w| {
        // Sidebar
        w.set_sidebar_label_dashboard(SharedString::from(&labels.sidebar_dashboard));
        w.set_sidebar_label_network(SharedString::from(&labels.sidebar_network));
        w.set_sidebar_label_proc_explorer(SharedString::from(&labels.sidebar_proc_explorer));
        w.set_sidebar_label_proc_monitor(SharedString::from(&labels.sidebar_proc_monitor));
        w.set_sidebar_label_tools(SharedString::from(&labels.sidebar_tools));
        w.set_sidebar_label_preferences(SharedString::from(&labels.sidebar_preferences));
        w.set_sidebar_group_network(SharedString::from(&labels.sidebar_group_network));
        w.set_sidebar_group_system(SharedString::from(&labels.sidebar_group_system));
        w.set_sidebar_group_utilities(SharedString::from(&labels.sidebar_group_utilities));

        // Status
        w.set_status_text(SharedString::from(&labels.status_ready));

        // Dashboard
        w.set_dash_title(SharedString::from(&labels.dash_title));
        w.set_dash_label_total_ports(SharedString::from(&labels.dash_label_total_ports));
        w.set_dash_label_total_procs(SharedString::from(&labels.dash_label_total_procs));
        w.set_dash_label_sys_cpu(SharedString::from(&labels.dash_label_sys_cpu));
        w.set_dash_label_sys_mem(SharedString::from(&labels.dash_label_sys_mem));
        w.set_dash_label_exposed(SharedString::from(&labels.dash_label_exposed));
        w.set_dash_label_tcp(SharedString::from(&labels.dash_label_tcp));
        w.set_dash_label_udp(SharedString::from(&labels.dash_label_udp));

        // Network
        w.set_net_label_total_listening(SharedString::from(&labels.net_label_total_listening));
        w.set_net_label_tcp_count(SharedString::from(&labels.net_label_tcp_count));
        w.set_net_label_udp_count(SharedString::from(&labels.net_label_udp_count));
        w.set_net_label_port(SharedString::from(&labels.net_label_port));
        w.set_net_label_protocol(SharedString::from(&labels.net_label_protocol));
        w.set_net_label_address(SharedString::from(&labels.net_label_address));
        w.set_net_label_state(SharedString::from(&labels.net_label_state));
        w.set_net_label_process(SharedString::from(&labels.net_label_process));
        w.set_net_label_pid(SharedString::from(&labels.net_label_pid));
        w.set_net_label_security(SharedString::from(&labels.net_label_security));
        w.set_net_label_refresh(SharedString::from(&labels.net_label_refresh));
        w.set_net_label_export(SharedString::from(&labels.net_label_export));

        // Process Explorer
        w.set_proc_label_total(SharedString::from(&labels.proc_label_total));
        w.set_proc_label_sys_cpu(SharedString::from(&labels.proc_label_sys_cpu));
        w.set_proc_label_sys_mem(SharedString::from(&labels.proc_label_sys_mem));
        w.set_proc_label_pid(SharedString::from(&labels.proc_label_pid));
        w.set_proc_label_process(SharedString::from(&labels.proc_label_process));
        w.set_proc_label_state(SharedString::from(&labels.proc_label_state));
        w.set_proc_label_cpu(SharedString::from(&labels.proc_label_cpu));
        w.set_proc_label_memory(SharedString::from(&labels.proc_label_memory));
        w.set_proc_label_rss(SharedString::from(&labels.proc_label_rss));
        w.set_proc_label_threads(SharedString::from(&labels.proc_label_threads));
        w.set_proc_label_user(SharedString::from(&labels.proc_label_user));
        w.set_proc_label_refresh(SharedString::from(&labels.proc_label_refresh));
        w.set_proc_label_export(SharedString::from(&labels.proc_label_export));
        w.set_proc_label_sort_cpu(SharedString::from(&labels.proc_label_sort_cpu));
        w.set_proc_label_sort_mem(SharedString::from(&labels.proc_label_sort_mem));
        w.set_proc_label_auto(SharedString::from(&labels.proc_label_auto));

        // Process Monitor
        w.set_mon_label_start(SharedString::from(&labels.mon_label_start));
        w.set_mon_label_stop(SharedString::from(&labels.mon_label_stop));
        w.set_mon_label_search(SharedString::from(&labels.mon_label_search));
        w.set_mon_label_kill(SharedString::from(&labels.mon_label_kill));
        w.set_mon_label_state(SharedString::from(&labels.mon_label_state));
        w.set_mon_label_io_read(SharedString::from(&labels.mon_label_io_read));
        w.set_mon_label_io_write(SharedString::from(&labels.mon_label_io_write));
        w.set_mon_label_search_name(SharedString::from(&labels.mon_label_search_name));

        // Tools
        w.set_tools_label_base64(SharedString::from(&labels.tools_label_base64));
        w.set_tools_label_json(SharedString::from(&labels.tools_label_json));
        w.set_tools_label_encode(SharedString::from(&labels.tools_label_encode));
        w.set_tools_label_decode(SharedString::from(&labels.tools_label_decode));
        w.set_tools_label_url_safe(SharedString::from(&labels.tools_label_url_safe));
        w.set_tools_label_mime(SharedString::from(&labels.tools_label_mime));
        w.set_tools_label_format(SharedString::from(&labels.tools_label_format));
        w.set_tools_label_compact(SharedString::from(&labels.tools_label_compact));
        w.set_tools_label_validate(SharedString::from(&labels.tools_label_validate));
        w.set_tools_label_copy(SharedString::from(&labels.tools_label_copy));
        w.set_tools_label_clear(SharedString::from(&labels.tools_label_clear));
        w.set_tools_label_input(SharedString::from(&labels.tools_label_input));
        w.set_tools_label_output(SharedString::from(&labels.tools_label_output));
        w.set_tools_label_valid(SharedString::from(&labels.tools_label_valid));
        w.set_tools_label_invalid(SharedString::from(&labels.tools_label_invalid));

        // Preferences
        w.set_pref_label_title(SharedString::from(&labels.pref_label_title));
        w.set_pref_label_theme(SharedString::from(&labels.pref_label_theme));
        w.set_pref_label_dark(SharedString::from(&labels.pref_label_dark));
        w.set_pref_label_light(SharedString::from(&labels.pref_label_light));
        w.set_pref_label_language(SharedString::from(&labels.pref_label_language));
        w.set_pref_label_auto(SharedString::from(&labels.pref_label_auto));
        w.set_pref_label_interval(SharedString::from(&labels.pref_label_interval));
    });
}

/// Holds all i18n labels for a single transfer to the UI thread.
struct I18nLabels {
    sidebar_dashboard: String,
    sidebar_network: String,
    sidebar_proc_explorer: String,
    sidebar_proc_monitor: String,
    sidebar_tools: String,
    sidebar_preferences: String,
    sidebar_group_network: String,
    sidebar_group_system: String,
    sidebar_group_utilities: String,
    status_ready: String,
    dash_title: String,
    dash_label_total_ports: String,
    dash_label_total_procs: String,
    dash_label_sys_cpu: String,
    dash_label_sys_mem: String,
    dash_label_exposed: String,
    dash_label_tcp: String,
    dash_label_udp: String,
    net_label_total_listening: String,
    net_label_tcp_count: String,
    net_label_udp_count: String,
    net_label_port: String,
    net_label_protocol: String,
    net_label_address: String,
    net_label_state: String,
    net_label_process: String,
    net_label_pid: String,
    net_label_security: String,
    net_label_refresh: String,
    net_label_export: String,
    proc_label_total: String,
    proc_label_sys_cpu: String,
    proc_label_sys_mem: String,
    proc_label_pid: String,
    proc_label_process: String,
    proc_label_state: String,
    proc_label_cpu: String,
    proc_label_memory: String,
    proc_label_rss: String,
    proc_label_threads: String,
    proc_label_user: String,
    proc_label_refresh: String,
    proc_label_export: String,
    proc_label_sort_cpu: String,
    proc_label_sort_mem: String,
    proc_label_auto: String,
    mon_label_start: String,
    mon_label_stop: String,
    mon_label_search: String,
    mon_label_kill: String,
    mon_label_state: String,
    mon_label_io_read: String,
    mon_label_io_write: String,
    mon_label_search_name: String,
    tools_label_base64: String,
    tools_label_json: String,
    tools_label_encode: String,
    tools_label_decode: String,
    tools_label_url_safe: String,
    tools_label_mime: String,
    tools_label_format: String,
    tools_label_compact: String,
    tools_label_validate: String,
    tools_label_copy: String,
    tools_label_clear: String,
    tools_label_input: String,
    tools_label_output: String,
    tools_label_valid: String,
    tools_label_invalid: String,
    pref_label_title: String,
    pref_label_theme: String,
    pref_label_dark: String,
    pref_label_light: String,
    pref_label_language: String,
    pref_label_auto: String,
    pref_label_interval: String,
}
