//! Service factory for HEFESTO Desktop.
//!
//! Creates and manages shared service instances using Arc for
//! thread-safe reference counting. All services are created once
//! and shared across view models.

use std::sync::Arc;

use hefesto_platform::port_parser::{self, PortParser};
use hefesto_platform::process_sampler::{ProcessSampler, SysInfoSampler};

use crate::i18n::{I18nService, Language};

/// Holds all shared services used by the application.
pub struct ServiceFactory {
    pub i18n: Arc<std::sync::Mutex<I18nService>>,
    pub port_parser: Arc<dyn PortParser>,
    pub process_sampler: Arc<dyn ProcessSampler>,
}

impl ServiceFactory {
    /// Creates a new ServiceFactory with default service instances.
    pub fn new(language: Language) -> Self {
        let i18n = Arc::new(std::sync::Mutex::new(I18nService::new(language)));
        let port_parser: Arc<dyn PortParser> = Arc::from(port_parser::create_parser());
        let process_sampler: Arc<dyn ProcessSampler> = Arc::new(SysInfoSampler::new());

        tracing::info!("ServiceFactory initialized with language: {}", language.code());

        Self {
            i18n,
            port_parser,
            process_sampler,
        }
    }

    /// Returns a clone of the I18n service handle.
    pub fn i18n(&self) -> Arc<std::sync::Mutex<I18nService>> {
        Arc::clone(&self.i18n)
    }

    /// Returns a clone of the port parser handle.
    pub fn port_parser(&self) -> Arc<dyn PortParser> {
        Arc::clone(&self.port_parser)
    }

    /// Returns a clone of the process sampler handle.
    pub fn process_sampler(&self) -> Arc<dyn ProcessSampler> {
        Arc::clone(&self.process_sampler)
    }
}
