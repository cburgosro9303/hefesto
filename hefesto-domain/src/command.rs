/// Metadata for a command.
#[derive(Debug, Clone)]
pub struct CommandInfo {
    pub name: String,
    pub description: String,
    pub category: String,
    pub aliases: Vec<String>,
    pub documentation: Option<Documentation>,
}

impl CommandInfo {
    pub fn new(name: impl Into<String>, description: impl Into<String>) -> Self {
        Self {
            name: name.into(),
            description: description.into(),
            category: "general".to_string(),
            aliases: Vec::new(),
            documentation: None,
        }
    }

    pub fn with_category(mut self, category: impl Into<String>) -> Self {
        self.category = category.into();
        self
    }

    pub fn with_aliases(mut self, aliases: Vec<String>) -> Self {
        self.aliases = aliases;
        self
    }

    pub fn with_documentation(mut self, docs: Documentation) -> Self {
        self.documentation = Some(docs);
        self
    }

    /// Checks if this command matches the given name or alias.
    pub fn matches(&self, command_name: &str) -> bool {
        if self.name.eq_ignore_ascii_case(command_name) {
            return true;
        }
        self.aliases
            .iter()
            .any(|a| a.eq_ignore_ascii_case(command_name))
    }
}

/// Result of a command execution.
#[derive(Debug)]
pub enum CommandResult {
    /// Successful execution.
    Success(String),
    /// Failed execution with error.
    Failure {
        error: String,
        cause: Option<String>,
    },
    /// Request to exit the application.
    Exit(i32),
    /// Continue execution (for menu navigation).
    Continue,
}

impl CommandResult {
    pub fn success() -> Self {
        Self::Success(String::new())
    }

    pub fn success_msg(message: impl Into<String>) -> Self {
        Self::Success(message.into())
    }

    pub fn failure(error: impl Into<String>) -> Self {
        Self::Failure {
            error: error.into(),
            cause: None,
        }
    }

    pub fn failure_with_cause(error: impl Into<String>, cause: impl Into<String>) -> Self {
        Self::Failure {
            error: error.into(),
            cause: Some(cause.into()),
        }
    }

    pub fn exit() -> Self {
        Self::Exit(0)
    }

    pub fn exit_with_code(code: i32) -> Self {
        Self::Exit(code)
    }

    pub fn cont() -> Self {
        Self::Continue
    }
}

/// Documentation for a command.
#[derive(Debug, Clone)]
pub struct Documentation {
    pub usage: String,
    pub long_description: String,
    pub options: Vec<OptionDoc>,
    pub examples: Vec<ExampleDoc>,
}

impl Documentation {
    pub fn new(usage: impl Into<String>) -> Self {
        Self {
            usage: usage.into(),
            long_description: String::new(),
            options: Vec::new(),
            examples: Vec::new(),
        }
    }

    pub fn with_long_description(mut self, desc: impl Into<String>) -> Self {
        self.long_description = desc.into();
        self
    }

    pub fn with_option(mut self, opt: OptionDoc) -> Self {
        self.options.push(opt);
        self
    }

    pub fn with_example(mut self, example: ExampleDoc) -> Self {
        self.examples.push(example);
        self
    }
}

/// Documentation for a command option/flag.
#[derive(Debug, Clone)]
pub struct OptionDoc {
    pub name: String,
    pub short: Option<String>,
    pub description: String,
    pub has_value: bool,
}

impl OptionDoc {
    pub fn flag(name: impl Into<String>, description: impl Into<String>) -> Self {
        Self {
            name: name.into(),
            short: None,
            description: description.into(),
            has_value: false,
        }
    }

    pub fn with_value(name: impl Into<String>, description: impl Into<String>) -> Self {
        Self {
            name: name.into(),
            short: None,
            description: description.into(),
            has_value: true,
        }
    }

    pub fn with_short(mut self, short: impl Into<String>) -> Self {
        self.short = Some(short.into());
        self
    }
}

/// Example usage for a command.
#[derive(Debug, Clone)]
pub struct ExampleDoc {
    pub command: String,
    pub description: String,
}

impl ExampleDoc {
    pub fn new(command: impl Into<String>, description: impl Into<String>) -> Self {
        Self {
            command: command.into(),
            description: description.into(),
        }
    }
}
