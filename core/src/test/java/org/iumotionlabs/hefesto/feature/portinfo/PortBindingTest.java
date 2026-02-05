package org.iumotionlabs.hefesto.feature.portinfo;

import org.iumotionlabs.hefesto.feature.portinfo.model.PortBinding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PortBinding Tests")
class PortBindingTest {

    @Test
    @DisplayName("toText should format basic binding")
    void toTextFormatsBasicBinding() {
        var binding = new PortBinding(
            8080, "TCP", "LISTEN", 1234, "java", "", "", "0.0.0.0", "", 0
        );

        String text = binding.toText();

        assertTrue(text.contains("TCP"));
        assertTrue(text.contains("8080"));
        assertTrue(text.contains("LISTEN"));
        assertTrue(text.contains("pid=1234"));
        assertTrue(text.contains("java"));
    }

    @Test
    @DisplayName("toText should include command line when present")
    void toTextIncludesCommandLine() {
        var binding = new PortBinding(
            8080, "TCP", "LISTEN", 1234, "java", "/usr/bin/java -jar app.jar", "user", "0.0.0.0", "", 0
        );

        String text = binding.toText();

        assertTrue(text.contains("cmd=\""));
        assertTrue(text.contains("user=user"));
    }

    @Test
    @DisplayName("toText should include remote address for established connections")
    void toTextIncludesRemoteForEstablished() {
        var binding = new PortBinding(
            8080, "TCP", "ESTABLISHED", 1234, "java", "", "", "127.0.0.1", "192.168.1.1", 54321
        );

        String text = binding.toText();

        assertTrue(text.contains("-> 192.168.1.1:54321"));
    }

    @Test
    @DisplayName("toCompact should return short format")
    void toCompactReturnsShortFormat() {
        var binding = new PortBinding(
            8080, "TCP", "LISTEN", 1234, "java", "", "", "0.0.0.0", "", 0
        );

        String compact = binding.toCompact();

        assertEquals("TCP :8080 LISTEN java (pid 1234)", compact);
    }

    @Test
    @DisplayName("listen factory should create LISTEN binding")
    void listenFactoryCreatesListenBinding() {
        var binding = PortBinding.listen(8080, "TCP", 1234, "java");

        assertEquals(8080, binding.port());
        assertEquals("TCP", binding.protocol());
        assertEquals("LISTEN", binding.state());
        assertEquals(1234, binding.pid());
        assertEquals("java", binding.processName());
    }

    @Test
    @DisplayName("toText should truncate long command lines")
    void toTextTruncatesLongCommandLine() {
        String longCommand = "a".repeat(300);
        var binding = new PortBinding(
            8080, "TCP", "LISTEN", 1234, "java", longCommand, "", "0.0.0.0", "", 0
        );

        String text = binding.toText();

        assertTrue(text.contains("..."));
        assertTrue(text.length() < longCommand.length() + 100);
    }
}
