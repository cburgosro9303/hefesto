//! Internationalization service for HEFESTO Desktop.
//!
//! Loads translations from embedded JSON files and provides
//! key-based lookups with fallback to the key itself.

use std::collections::HashMap;

/// Embedded English translations.
const EN_JSON: &str = include_str!("../i18n/en.json");

/// Embedded Spanish translations.
const ES_JSON: &str = include_str!("../i18n/es.json");

/// Supported languages.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Language {
    English,
    Spanish,
}

impl Language {
    /// Returns the language code.
    pub fn code(&self) -> &str {
        match self {
            Language::English => "en",
            Language::Spanish => "es",
        }
    }

    /// Parses a language code into a Language variant.
    pub fn from_code(code: &str) -> Self {
        match code.to_lowercase().as_str() {
            "es" | "spanish" | "espanol" => Language::Spanish,
            _ => Language::English,
        }
    }
}

/// Internationalization service that provides translated strings.
pub struct I18nService {
    current: Language,
    translations: HashMap<String, String>,
}

impl I18nService {
    /// Creates a new I18nService with the given default language.
    pub fn new(language: Language) -> Self {
        let mut service = Self {
            current: language,
            translations: HashMap::new(),
        };
        service.load_translations();
        service
    }

    /// Returns the current language.
    pub fn language(&self) -> Language {
        self.current
    }

    /// Switches to a different language and reloads translations.
    pub fn set_language(&mut self, language: Language) {
        if self.current != language {
            self.current = language;
            self.load_translations();
        }
    }

    /// Translates a key to the localized string.
    /// Returns the key itself if no translation is found.
    pub fn t(&self, key: &str) -> String {
        self.translations
            .get(key)
            .cloned()
            .unwrap_or_else(|| key.to_string())
    }

    /// Translates a key and replaces {0}, {1}, ... with the provided arguments.
    pub fn t_args(&self, key: &str, args: &[&str]) -> String {
        let mut result = self.t(key);
        for (i, arg) in args.iter().enumerate() {
            let placeholder = format!("{{{}}}", i);
            result = result.replace(&placeholder, arg);
        }
        result
    }

    /// Returns all translations as a reference.
    pub fn translations(&self) -> &HashMap<String, String> {
        &self.translations
    }

    fn load_translations(&mut self) {
        let json_str = match self.current {
            Language::English => EN_JSON,
            Language::Spanish => ES_JSON,
        };

        match serde_json::from_str::<HashMap<String, String>>(json_str) {
            Ok(map) => {
                self.translations = map;
                tracing::info!(
                    "Loaded {} translations for language '{}'",
                    self.translations.len(),
                    self.current.code()
                );
            }
            Err(e) => {
                tracing::error!(
                    "Failed to load translations for '{}': {}",
                    self.current.code(),
                    e
                );
                self.translations.clear();
            }
        }
    }
}

impl Default for I18nService {
    fn default() -> Self {
        Self::new(Language::English)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_english_translations_load() {
        let svc = I18nService::new(Language::English);
        assert_eq!(svc.t("app.title"), "HEFESTO Desktop");
        assert_eq!(svc.t("sidebar.dashboard"), "Dashboard");
    }

    #[test]
    fn test_spanish_translations_load() {
        let svc = I18nService::new(Language::Spanish);
        assert_eq!(svc.t("app.title"), "HEFESTO Escritorio");
        assert_eq!(svc.t("sidebar.dashboard"), "Tablero");
    }

    #[test]
    fn test_missing_key_returns_key() {
        let svc = I18nService::new(Language::English);
        assert_eq!(svc.t("nonexistent.key"), "nonexistent.key");
    }

    #[test]
    fn test_t_args() {
        let svc = I18nService::new(Language::English);
        let result = svc.t_args("portinfo.kill.confirm", &["nginx", "80"]);
        assert_eq!(result, "Kill process nginx on port 80?");
    }

    #[test]
    fn test_language_switch() {
        let mut svc = I18nService::new(Language::English);
        assert_eq!(svc.t("common.refresh"), "Refresh");

        svc.set_language(Language::Spanish);
        assert_eq!(svc.t("common.refresh"), "Actualizar");
    }

    #[test]
    fn test_language_from_code() {
        assert_eq!(Language::from_code("en"), Language::English);
        assert_eq!(Language::from_code("es"), Language::Spanish);
        assert_eq!(Language::from_code("fr"), Language::English); // fallback
    }
}
