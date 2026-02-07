use std::sync::Arc;

use crate::port::{InputPort, OutputPort};
use hefesto_domain::config::HefestoConfig;

/// Execution context providing access to output/input ports and configuration.
pub struct ExecutionContext {
    pub output: Arc<dyn OutputPort>,
    pub input: Arc<dyn InputPort>,
    pub config: &'static HefestoConfig,
}

impl ExecutionContext {
    pub fn new(output: Arc<dyn OutputPort>, input: Arc<dyn InputPort>) -> Self {
        Self {
            output,
            input,
            config: HefestoConfig::get(),
        }
    }
}
