package it.flossconsulting.sshtunnel;

import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter;
import org.apache.sshd.common.util.io.resource.PathResource;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.security.KeyPair;
import static org.junit.jupiter.api.Assertions.*;

class Ed25519KeyTest {
    @TempDir Path temp;
    @Test void loadsOpenSshEd25519Identity() throws Exception {
        KeyPair generated = SecurityUtils.getKeyPairGenerator("Ed25519").generateKeyPair();
        Path file = temp.resolve("chiave con spazi 🔑");
        try (var output = Files.newOutputStream(file)) {
            OpenSSHKeyPairResourceWriter.INSTANCE.writePrivateKey(generated, "test", null, output);
        }
        try (var input = Files.newInputStream(file)) {
            var loaded = SecurityUtils.loadKeyPairIdentities(null, new PathResource(file), input, null).iterator();
            assertTrue(loaded.hasNext());
            assertEquals(generated.getPublic(), loaded.next().getPublic());
            assertFalse(loaded.hasNext());
        }
    }
}
