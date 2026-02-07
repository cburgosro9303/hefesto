//! Tools view model.
//!
//! Handles Base64 encoding/decoding and JSON formatting/validation
//! operations for the tools panel.

use base64::Engine;
use slint::{ComponentHandle, SharedString};

use crate::AppWindow;

/// Wires all tools callbacks.
pub fn setup_tools(window: &AppWindow) {
    // ---- Base64 Encode ----
    {
        let weak = window.as_weak();
        window.on_tools_b64_encode(move || {
            let weak = weak.clone();
            let _ = weak.upgrade_in_event_loop(|w| {
                let input = w.get_tools_b64_input().to_string();
                if input.is_empty() {
                    return;
                }

                let url_safe = w.get_tools_b64_url_safe();
                let mime = w.get_tools_b64_mime();

                let encoded = if mime {
                    base64::engine::general_purpose::STANDARD.encode(input.as_bytes())
                } else if url_safe {
                    base64::engine::general_purpose::URL_SAFE.encode(input.as_bytes())
                } else {
                    base64::engine::general_purpose::STANDARD.encode(input.as_bytes())
                };

                w.set_tools_b64_output(SharedString::from(&encoded));
            });
        });
    }

    // ---- Base64 Decode ----
    {
        let weak = window.as_weak();
        window.on_tools_b64_decode(move || {
            let weak = weak.clone();
            let _ = weak.upgrade_in_event_loop(|w| {
                let input = w.get_tools_b64_input().to_string();
                if input.is_empty() {
                    return;
                }

                let url_safe = w.get_tools_b64_url_safe();
                let trimmed = input.trim();

                let result = if url_safe {
                    base64::engine::general_purpose::URL_SAFE.decode(trimmed)
                } else {
                    base64::engine::general_purpose::STANDARD.decode(trimmed)
                };

                match result {
                    Ok(bytes) => match String::from_utf8(bytes) {
                        Ok(decoded) => {
                            w.set_tools_b64_output(SharedString::from(&decoded));
                        }
                        Err(e) => {
                            w.set_tools_b64_output(SharedString::from(format!(
                                "Error: decoded bytes are not valid UTF-8: {}",
                                e
                            )));
                        }
                    },
                    Err(e) => {
                        w.set_tools_b64_output(SharedString::from(format!(
                            "Invalid Base64 input: {}",
                            e
                        )));
                    }
                }
            });
        });
    }

    // ---- Base64 Clear ----
    {
        let weak = window.as_weak();
        window.on_tools_b64_clear(move || {
            let _ = weak.upgrade_in_event_loop(|w| {
                w.set_tools_b64_input(SharedString::default());
                w.set_tools_b64_output(SharedString::default());
            });
        });
    }

    // ---- Base64 Copy Output ----
    {
        let weak = window.as_weak();
        window.on_tools_b64_copy_output(move || {
            let _ = weak.upgrade_in_event_loop(|w| {
                let output = w.get_tools_b64_output().to_string();
                if !output.is_empty() {
                    tracing::info!("Base64 output copied to clipboard (clipboard integration pending)");
                }
            });
        });
    }

    // ---- JSON Format ----
    {
        let weak = window.as_weak();
        window.on_tools_json_format(move || {
            let weak = weak.clone();
            let _ = weak.upgrade_in_event_loop(|w| {
                let input = w.get_tools_json_input().to_string();
                if input.is_empty() {
                    return;
                }

                match serde_json::from_str::<serde_json::Value>(&input) {
                    Ok(value) => {
                        let formatted = serde_json::to_string_pretty(&value)
                            .unwrap_or_else(|e| format!("Error: {}", e));
                        w.set_tools_json_output(SharedString::from(&formatted));
                        w.set_tools_json_valid(1);
                    }
                    Err(e) => {
                        w.set_tools_json_output(SharedString::from(format!("Error: {}", e)));
                        w.set_tools_json_valid(0);
                    }
                }
            });
        });
    }

    // ---- JSON Compact ----
    {
        let weak = window.as_weak();
        window.on_tools_json_compact(move || {
            let weak = weak.clone();
            let _ = weak.upgrade_in_event_loop(|w| {
                let input = w.get_tools_json_input().to_string();
                if input.is_empty() {
                    return;
                }

                match serde_json::from_str::<serde_json::Value>(&input) {
                    Ok(value) => {
                        let compacted = serde_json::to_string(&value)
                            .unwrap_or_else(|e| format!("Error: {}", e));
                        w.set_tools_json_output(SharedString::from(&compacted));
                        w.set_tools_json_valid(1);
                    }
                    Err(e) => {
                        w.set_tools_json_output(SharedString::from(format!("Error: {}", e)));
                        w.set_tools_json_valid(0);
                    }
                }
            });
        });
    }

    // ---- JSON Validate ----
    {
        let weak = window.as_weak();
        window.on_tools_json_validate(move || {
            let weak = weak.clone();
            let _ = weak.upgrade_in_event_loop(|w| {
                let input = w.get_tools_json_input().to_string();
                if input.is_empty() {
                    return;
                }

                match serde_json::from_str::<serde_json::Value>(&input) {
                    Ok(_) => {
                        w.set_tools_json_output(SharedString::from(&input));
                        w.set_tools_json_valid(1);
                    }
                    Err(e) => {
                        w.set_tools_json_output(SharedString::from(format!(
                            "Validation error: {}",
                            e
                        )));
                        w.set_tools_json_valid(0);
                    }
                }
            });
        });
    }

    // ---- JSON Clear ----
    {
        let weak = window.as_weak();
        window.on_tools_json_clear(move || {
            let _ = weak.upgrade_in_event_loop(|w| {
                w.set_tools_json_input(SharedString::default());
                w.set_tools_json_output(SharedString::default());
                w.set_tools_json_valid(-1);
            });
        });
    }

    // ---- JSON Copy Output ----
    {
        let weak = window.as_weak();
        window.on_tools_json_copy_output(move || {
            let _ = weak.upgrade_in_event_loop(|w| {
                let output = w.get_tools_json_output().to_string();
                if !output.is_empty() {
                    tracing::info!("JSON output copied to clipboard (clipboard integration pending)");
                }
            });
        });
    }
}
