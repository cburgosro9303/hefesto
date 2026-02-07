pub mod console;

/// Output port for displaying information to the user.
pub trait OutputPort: Send + Sync {
    /// Prints a line of text.
    fn println(&self, text: &str);

    /// Prints text without a newline.
    fn print(&self, text: &str);

    /// Prints an empty line.
    fn println_empty(&self) {
        self.println("");
    }

    /// Prints an error message.
    fn print_error(&self, text: &str);

    /// Prints a warning message.
    fn print_warning(&self, text: &str);

    /// Prints a success message.
    fn print_success(&self, text: &str);

    /// Prints a header.
    fn print_header(&self, text: &str);

    /// Prints a separator line.
    fn print_separator(&self);

    /// Flushes any buffered output.
    fn flush(&self);
}

/// Input port for reading user input.
pub trait InputPort: Send + Sync {
    /// Reads a line of input from the user.
    fn read_line(&self, prompt: &str) -> Option<String>;

    /// Reads a yes/no confirmation.
    fn confirm(&self, prompt: &str) -> bool;
}

/// Test output adapter that captures output to a vector.
pub struct TestOutput {
    lines: std::sync::Mutex<Vec<String>>,
}

impl TestOutput {
    pub fn new() -> Self {
        Self {
            lines: std::sync::Mutex::new(Vec::new()),
        }
    }

    pub fn lines(&self) -> Vec<String> {
        self.lines.lock().unwrap().clone()
    }

    pub fn contains(&self, text: &str) -> bool {
        self.lines.lock().unwrap().iter().any(|l| l.contains(text))
    }
}

impl Default for TestOutput {
    fn default() -> Self {
        Self::new()
    }
}

impl OutputPort for TestOutput {
    fn println(&self, text: &str) {
        self.lines.lock().unwrap().push(text.to_string());
    }

    fn print(&self, text: &str) {
        self.lines.lock().unwrap().push(text.to_string());
    }

    fn print_error(&self, text: &str) {
        self.lines.lock().unwrap().push(format!("[ERROR] {}", text));
    }

    fn print_warning(&self, text: &str) {
        self.lines.lock().unwrap().push(format!("[WARN] {}", text));
    }

    fn print_success(&self, text: &str) {
        self.lines.lock().unwrap().push(format!("[OK] {}", text));
    }

    fn print_header(&self, text: &str) {
        self.lines.lock().unwrap().push(format!("=== {} ===", text));
    }

    fn print_separator(&self) {
        self.lines
            .lock()
            .unwrap()
            .push("─".repeat(60));
    }

    fn flush(&self) {}
}

/// Test input adapter with predefined responses.
pub struct TestInput {
    responses: std::sync::Mutex<Vec<String>>,
}

impl TestInput {
    pub fn new(responses: Vec<String>) -> Self {
        Self {
            responses: std::sync::Mutex::new(responses),
        }
    }
}

impl InputPort for TestInput {
    fn read_line(&self, _prompt: &str) -> Option<String> {
        let mut responses = self.responses.lock().unwrap();
        if responses.is_empty() {
            None
        } else {
            Some(responses.remove(0))
        }
    }

    fn confirm(&self, prompt: &str) -> bool {
        self.read_line(prompt)
            .map(|s| s.to_lowercase().starts_with('y'))
            .unwrap_or(false)
    }
}
