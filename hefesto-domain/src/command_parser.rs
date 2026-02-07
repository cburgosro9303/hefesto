use std::collections::HashMap;

/// Tokenizes a command line string respecting quoted strings.
pub fn tokenize(input: &str) -> Vec<String> {
    let input = input.trim();
    if input.is_empty() {
        return Vec::new();
    }

    let mut tokens = Vec::new();
    let mut chars = input.chars().peekable();
    let mut current = String::new();
    let mut in_double_quote = false;
    let mut in_single_quote = false;

    while let Some(&c) = chars.peek() {
        chars.next();
        match c {
            '"' if !in_single_quote => {
                if in_double_quote {
                    in_double_quote = false;
                    tokens.push(std::mem::take(&mut current));
                } else {
                    in_double_quote = true;
                    if !current.is_empty() {
                        tokens.push(std::mem::take(&mut current));
                    }
                }
            }
            '\'' if !in_double_quote => {
                if in_single_quote {
                    in_single_quote = false;
                    tokens.push(std::mem::take(&mut current));
                } else {
                    in_single_quote = true;
                    if !current.is_empty() {
                        tokens.push(std::mem::take(&mut current));
                    }
                }
            }
            ' ' | '\t' if !in_double_quote && !in_single_quote => {
                if !current.is_empty() {
                    tokens.push(std::mem::take(&mut current));
                }
            }
            _ => {
                current.push(c);
            }
        }
    }

    if !current.is_empty() {
        tokens.push(current);
    }

    tokens
}

/// Parsed arguments with flags and positional args.
#[derive(Debug, Clone)]
pub struct ParsedArgs {
    pub flags: HashMap<String, String>,
    pub positional: Vec<String>,
}

impl ParsedArgs {
    pub fn has_flag(&self, name: &str) -> bool {
        self.flags.contains_key(name)
    }

    pub fn get_flag(&self, name: &str) -> Option<&str> {
        self.flags.get(name).map(|s| s.as_str())
    }

    pub fn get_flag_or(&self, name: &str, default: &str) -> String {
        self.flags
            .get(name)
            .cloned()
            .unwrap_or_else(|| default.to_string())
    }

    pub fn get_flag_as_int(&self, name: &str) -> Option<i32> {
        self.flags.get(name).and_then(|s| s.parse::<i32>().ok())
    }

    pub fn get_flag_as_int_or(&self, name: &str, default: i32) -> i32 {
        self.get_flag_as_int(name).unwrap_or(default)
    }

    pub fn get_boolean(&self, name: &str) -> bool {
        self.has_flag(name)
    }

    pub fn positional(&self, index: usize) -> Option<&str> {
        self.positional.get(index).map(|s| s.as_str())
    }

    pub fn has_positional(&self) -> bool {
        !self.positional.is_empty()
    }

    pub fn positional_count(&self) -> usize {
        self.positional.len()
    }
}

/// Parses arguments into flags and positional arguments.
///
/// Flags can be:
/// - `--flag value`
/// - `--flag=value`
/// - `-f value`
/// - `-f` (boolean flag)
/// - `-abc` (multiple boolean short flags)
pub fn parse(args: &[String]) -> ParsedArgs {
    let mut flags = HashMap::new();
    let mut positional = Vec::new();

    let mut i = 0;
    while i < args.len() {
        let arg = &args[i];

        if let Some(flag_part) = arg.strip_prefix("--") {
            // Long flag
            if let Some(eq_idx) = flag_part.find('=') {
                // --flag=value
                flags.insert(
                    flag_part[..eq_idx].to_string(),
                    flag_part[eq_idx + 1..].to_string(),
                );
            } else if i + 1 < args.len() && !args[i + 1].starts_with('-') {
                // --flag value
                flags.insert(flag_part.to_string(), args[i + 1].clone());
                i += 1;
            } else {
                // --flag (boolean)
                flags.insert(flag_part.to_string(), "true".to_string());
            }
        } else if arg.starts_with('-') && arg.len() > 1 {
            // Short flag(s)
            let flag_chars = &arg[1..];

            if flag_chars.len() == 1 {
                // Single short flag
                if i + 1 < args.len() && !args[i + 1].starts_with('-') {
                    flags.insert(flag_chars.to_string(), args[i + 1].clone());
                    i += 1;
                } else {
                    flags.insert(flag_chars.to_string(), "true".to_string());
                }
            } else {
                // Multiple short flags (-abc = -a -b -c)
                for c in flag_chars.chars() {
                    flags.insert(c.to_string(), "true".to_string());
                }
            }
        } else {
            positional.push(arg.clone());
        }

        i += 1;
    }

    ParsedArgs { flags, positional }
}

/// Parses a raw command line string.
pub fn parse_line(command_line: &str) -> ParsedArgs {
    let tokens = tokenize(command_line);
    parse(&tokens)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_tokenize_simple() {
        let tokens = tokenize("hello world");
        assert_eq!(tokens, vec!["hello", "world"]);
    }

    #[test]
    fn test_tokenize_double_quoted() {
        let tokens = tokenize(r#"echo "hello world""#);
        assert_eq!(tokens, vec!["echo", "hello world"]);
    }

    #[test]
    fn test_tokenize_single_quoted() {
        let tokens = tokenize("echo 'hello world'");
        assert_eq!(tokens, vec!["echo", "hello world"]);
    }

    #[test]
    fn test_tokenize_empty() {
        assert!(tokenize("").is_empty());
        assert!(tokenize("   ").is_empty());
    }

    #[test]
    fn test_parse_long_flags() {
        let args = tokenize("--port 8080 --verbose");
        let parsed = parse(&args);
        assert_eq!(parsed.get_flag("port"), Some("8080"));
        assert!(parsed.get_boolean("verbose"));
    }

    #[test]
    fn test_parse_flag_with_equals() {
        let args = tokenize("--port=8080");
        let parsed = parse(&args);
        assert_eq!(parsed.get_flag("port"), Some("8080"));
    }

    #[test]
    fn test_parse_short_flags() {
        let args = tokenize("-p 8080 -v");
        let parsed = parse(&args);
        assert_eq!(parsed.get_flag("p"), Some("8080"));
        assert!(parsed.get_boolean("v"));
    }

    #[test]
    fn test_parse_combined_short_flags() {
        let args = tokenize("-abc");
        let parsed = parse(&args);
        assert!(parsed.get_boolean("a"));
        assert!(parsed.get_boolean("b"));
        assert!(parsed.get_boolean("c"));
    }

    #[test]
    fn test_parse_positional() {
        // --all consumes 8080 as its value since it doesn't start with -
        let args = tokenize("portinfo --all 8080");
        let parsed = parse(&args);
        assert_eq!(parsed.positional(0), Some("portinfo"));
        assert_eq!(parsed.get_flag("all"), Some("8080"));

        // To have 8080 as positional, put it before the flag
        let args2 = tokenize("portinfo 8080 --all");
        let parsed2 = parse(&args2);
        assert_eq!(parsed2.positional(0), Some("portinfo"));
        assert_eq!(parsed2.positional(1), Some("8080"));
        assert!(parsed2.get_boolean("all"));
    }

    #[test]
    fn test_get_flag_as_int() {
        let args = tokenize("--port 8080");
        let parsed = parse(&args);
        assert_eq!(parsed.get_flag_as_int("port"), Some(8080));
        assert_eq!(parsed.get_flag_as_int("missing"), None);
    }
}
