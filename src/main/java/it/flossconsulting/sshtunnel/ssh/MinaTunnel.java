package it.flossconsulting.sshtunnel.ssh;

import it.flossconsulting.sshtunnel.model.Profile;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.client.keyverifier.KnownHostsServerKeyVerifier;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.keyprovider.KeyIdentityProvider;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.common.util.io.resource.PathResource;

import java.io.InputStream;
import java.nio.file.*;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

public final class MinaTunnel implements AutoCloseable {
    public interface Prompts {
        boolean trust(String host, String keyType, String fingerprint);
        String passphrase();
    }
    private SshClient client;
    private ClientSession session;
    private SshdSocketAddress bound;

    public synchronized int connect(Profile profile, Prompts prompts) throws Exception {
        if (client != null) throw new IllegalStateException("Tunnel already started");
        ConnectionFailure.Stage stage = ConnectionFailure.Stage.IDENTITY;
        Path keyPath = Path.of(profile.identityPath());
        Path knownHosts = Path.of(System.getProperty("user.home"), ".ssh", "known_hosts");
        try {
            profile.validateKey();
            stage = ConnectionFailure.Stage.SERVER;
            client = SshClient.setUpDefaultClient();
            client.setKeyIdentityProvider(KeyIdentityProvider.EMPTY_KEYS_PROVIDER);
            client.setServerKeyVerifier(new KnownHostsServerKeyVerifier((s, address, key) -> {
                Path parent = knownHosts.getParent();
                try { Files.createDirectories(parent); } catch (Exception ignored) { return false; }
                if ((Files.exists(knownHosts) && !Files.isWritable(knownHosts))
                        || (!Files.exists(knownHosts) && !Files.isWritable(parent))) return false;
                return prompts.trust(address.toString(), KeyUtils.getKeyType(key), KeyUtils.getFingerPrint(key));
            }, knownHosts) {
                @Override protected void handleKnownHostsFileUpdateFailure(ClientSession s, java.net.SocketAddress address,
                        java.security.PublicKey key, Path file, java.util.Collection<HostEntryPair> entries, Throwable cause) {
                    throw new IllegalStateException("Could not save the host key to known_hosts");
                }
            });
            client.start();
            session = client.connect(profile.username(), profile.sshHost(), profile.sshPort()).verify(10_000).getSession();
            stage = ConnectionFailure.Stage.IDENTITY;
            FilePasswordProvider passwordProvider = (context, resource, retry) -> {
                if (retry > 0) throw new IllegalArgumentException("Incorrect passphrase");
                String password = prompts.passphrase();
                if (password == null) throw new IllegalArgumentException("Passphrase entry cancelled");
                return password;
            };
            List<KeyPair> keys = new ArrayList<>();
            PathResource resource = new PathResource(keyPath);
            try (InputStream input = Files.newInputStream(keyPath)) {
                Iterable<KeyPair> loaded = SecurityUtils.loadKeyPairIdentities(session, resource, input, passwordProvider);
                if (loaded != null) loaded.forEach(keys::add);
            }
            if (keys.isEmpty()) throw new IllegalArgumentException("Unsupported key format");
            for (KeyPair key : keys) session.addPublicKeyIdentity(key);
            stage = ConnectionFailure.Stage.AUTHENTICATION;
            session.auth().verify(10_000);
            stage = ConnectionFailure.Stage.FORWARD;
            bound = session.startLocalPortForwarding(
                    new SshdSocketAddress("127.0.0.1", profile.automaticPort() ? 0 : profile.localPort()),
                    new SshdSocketAddress(profile.remoteHost(), profile.remotePort()));
            return bound.getPort();
        } catch (Exception e) { close(); throw new ConnectionFailure(stage, e); }
    }
    public synchronized boolean isOpen() { return session != null && session.isOpen() && bound != null; }
    @Override public synchronized void close() {
        if (session != null) {
            try { if (bound != null) session.stopLocalPortForwarding(bound); } catch (Exception ignored) { }
            try { session.close(true); } catch (Exception ignored) { }
        }
        if (client != null) { try { client.stop(); } catch (Exception ignored) { } }
        bound = null; session = null; client = null;
    }
}
