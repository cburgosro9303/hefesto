use hefesto_domain::command::{CommandInfo, CommandResult, Documentation};

use crate::context::ExecutionContext;

/// Base trait for all commands in Hefesto.
pub trait Command: Send + Sync {
    /// Returns metadata about this command.
    fn info(&self) -> &CommandInfo;

    /// Executes the command with the given context and arguments.
    fn execute(&self, ctx: &ExecutionContext, args: &[String]) -> CommandResult;

    /// Returns documentation for help system.
    fn documentation(&self) -> Option<&Documentation> {
        self.info().documentation.as_ref()
    }

    /// Returns the command name (convenience).
    fn name(&self) -> &str {
        &self.info().name
    }

    /// Returns the command description (convenience).
    fn description(&self) -> &str {
        &self.info().description
    }
}

/// Registry of all available commands.
pub struct CommandRegistry {
    commands: Vec<Box<dyn Command>>,
}

impl CommandRegistry {
    pub fn new() -> Self {
        Self {
            commands: Vec::new(),
        }
    }

    /// Registers a command.
    pub fn register(&mut self, command: Box<dyn Command>) {
        self.commands.push(command);
    }

    /// Finds a command by name or alias.
    pub fn find(&self, name: &str) -> Option<&dyn Command> {
        self.commands
            .iter()
            .find(|c| c.info().matches(name))
            .map(|c| c.as_ref())
    }

    /// Returns all registered commands.
    pub fn all(&self) -> &[Box<dyn Command>] {
        &self.commands
    }

    /// Returns commands grouped by category.
    pub fn by_category(&self) -> Vec<(&str, Vec<&dyn Command>)> {
        let mut categories: std::collections::HashMap<&str, Vec<&dyn Command>> =
            std::collections::HashMap::new();

        for cmd in &self.commands {
            categories
                .entry(cmd.info().category.as_str())
                .or_default()
                .push(cmd.as_ref());
        }

        let mut result: Vec<(&str, Vec<&dyn Command>)> = categories.into_iter().collect();
        result.sort_by_key(|(k, _)| *k);
        result
    }
}

impl Default for CommandRegistry {
    fn default() -> Self {
        Self::new()
    }
}
