use serde::Serialize;

/// Categories of services.
#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize)]
pub enum ServiceCategory {
    Database,
    Web,
    Messaging,
    Cache,
    Search,
    Dev,
    Infra,
    Monitoring,
    Security,
    Other,
}

impl ServiceCategory {
    pub fn display_name(&self) -> &str {
        match self {
            ServiceCategory::Database => "Database",
            ServiceCategory::Web => "Web Server",
            ServiceCategory::Messaging => "Messaging",
            ServiceCategory::Cache => "Cache",
            ServiceCategory::Search => "Search",
            ServiceCategory::Dev => "Development",
            ServiceCategory::Infra => "Infrastructure",
            ServiceCategory::Monitoring => "Monitoring",
            ServiceCategory::Security => "Security",
            ServiceCategory::Other => "Other",
        }
    }
}

impl std::fmt::Display for ServiceCategory {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(self.display_name())
    }
}

/// Information about a known service mapped to a port.
#[derive(Debug, Clone, Serialize)]
pub struct ServiceInfo {
    pub name: String,
    pub description: String,
    pub category: ServiceCategory,
}

impl ServiceInfo {
    pub fn new(
        name: impl Into<String>,
        description: impl Into<String>,
        category: ServiceCategory,
    ) -> Self {
        Self {
            name: name.into(),
            description: description.into(),
            category,
        }
    }

    /// Returns a formatted string representation for display.
    pub fn to_display_string(&self) -> String {
        format!("[{} - {}]", self.name, self.description)
    }

    /// Returns a short tag format.
    pub fn to_tag(&self) -> String {
        format!("[{}]", self.name)
    }
}
