use crossterm::style::{Attribute, Color, Stylize};
use std::io::{self, BufRead, Write};

use super::{InputPort, OutputPort};

/// Console output adapter using crossterm for colors.
pub struct ConsoleOutputAdapter;

impl ConsoleOutputAdapter {
    pub fn new() -> Self {
        Self
    }
}

impl Default for ConsoleOutputAdapter {
    fn default() -> Self {
        Self::new()
    }
}

impl OutputPort for ConsoleOutputAdapter {
    fn println(&self, text: &str) {
        println!("{}", text);
    }

    fn print(&self, text: &str) {
        print!("{}", text);
        let _ = io::stdout().flush();
    }

    fn print_error(&self, text: &str) {
        eprintln!("{}", text.with(Color::Red).attribute(Attribute::Bold));
    }

    fn print_warning(&self, text: &str) {
        println!("{}", text.with(Color::Yellow));
    }

    fn print_success(&self, text: &str) {
        println!("{}", text.with(Color::Green));
    }

    fn print_header(&self, text: &str) {
        println!("{}", text.with(Color::Cyan).attribute(Attribute::Bold));
    }

    fn print_separator(&self) {
        println!("{}", "─".repeat(60).with(Color::DarkGrey));
    }

    fn flush(&self) {
        let _ = io::stdout().flush();
    }
}

/// Console input adapter reading from stdin.
pub struct ConsoleInputAdapter;

impl ConsoleInputAdapter {
    pub fn new() -> Self {
        Self
    }
}

impl Default for ConsoleInputAdapter {
    fn default() -> Self {
        Self::new()
    }
}

impl InputPort for ConsoleInputAdapter {
    fn read_line(&self, prompt: &str) -> Option<String> {
        print!("{}", prompt);
        let _ = io::stdout().flush();

        let stdin = io::stdin();
        let mut line = String::new();
        match stdin.lock().read_line(&mut line) {
            Ok(0) => None,
            Ok(_) => Some(line.trim().to_string()),
            Err(_) => None,
        }
    }

    fn confirm(&self, prompt: &str) -> bool {
        self.read_line(&format!("{} [y/N]: ", prompt))
            .map(|s| s.to_lowercase().starts_with('y'))
            .unwrap_or(false)
    }
}
