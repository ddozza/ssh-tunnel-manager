package it.flossconsulting.sshtunnel;

import it.flossconsulting.sshtunnel.config.DiagnosticLog;
import it.flossconsulting.sshtunnel.ssh.ConnectionFailure;
import it.flossconsulting.sshtunnel.tunnels.TunnelManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DiagnosticLogTest {
    @TempDir Path temp;
    @Test void recordsStageAndTypeWithoutExceptionMessage() throws Exception {
        Path file = temp.resolve("diagnostic.log");
        DiagnosticLog log = new DiagnosticLog(file);
        log.record(UUID.randomUUID(), "IDENTITY", "ERROR", new IllegalArgumentException("secret host user key path"));
        String content = Files.readString(file);
        assertTrue(content.contains("stage=IDENTITY outcome=ERROR type=IllegalArgumentException"));
        assertFalse(content.contains("secret"));
    }
    @Test void returnsOnlyEventsForSelectedProfile() {
        DiagnosticLog log = new DiagnosticLog(temp.resolve("diagnostic.log"));
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        log.record(first, "SERVER", "CONNECTING", null);
        log.record(second, "FORWARD", "CONNECTED", null);
        log.record(first, "IDENTITY", "ERROR", new java.security.NoSuchAlgorithmException("private path"));
        var events = log.events(first);
        assertEquals(2, events.size());
        assertEquals("CONNECTING", events.get(0).outcome());
        assertEquals("IDENTITY", events.get(1).stage());
        assertEquals("NoSuchAlgorithmException", events.get(1).type());
    }
    @Test void explainsUnsupportedIdentityWithoutExposingItsPath() {
        var error = new ConnectionFailure(ConnectionFailure.Stage.IDENTITY,
                new java.security.NoSuchAlgorithmException("Unsupported key type in /private/example"));
        String description = TunnelManager.explain(error);
        assertTrue(description.contains("key"));
        assertFalse(description.contains("/private/example"));
    }
}
