package it.flossconsulting.sshtunnel;

import it.flossconsulting.sshtunnel.model.Profile;
import it.flossconsulting.sshtunnel.ssh.MinaTunnel;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class HostKeyTest {
    @TempDir Path temp;
    @Test void refusedUnknownHostNeverStartsForward() throws Exception {
        SshServer server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1"); server.setPort(0);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(temp.resolve("server.key")));
        server.setPublickeyAuthenticator((user, key, session) -> true);
        String previousHome = System.getProperty("user.home");
        System.setProperty("user.home", temp.toString());
        try {
            server.start();
            Path identity = temp.resolve("unused.key"); Files.writeString(identity, "test");
            Profile p = new Profile(UUID.randomUUID(), "local", "127.0.0.1", server.getPort(), "test",
                    "127.0.0.1", 80, 0, true, identity.toString(), false);
            try (MinaTunnel tunnel = new MinaTunnel()) {
                assertThrows(Exception.class, () -> tunnel.connect(p, new MinaTunnel.Prompts() {
                    public boolean trust(String host, String type, String fingerprint) { return false; }
                    public String passphrase() { fail("Key loading must not begin before trust"); return null; }
                }));
                assertFalse(tunnel.isOpen());
                assertFalse(Files.exists(temp.resolve(".ssh").resolve("known_hosts")));
            }
        } finally { server.stop(true); System.setProperty("user.home", previousHome); }
    }
}
