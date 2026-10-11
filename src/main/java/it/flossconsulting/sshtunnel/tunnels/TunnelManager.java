package it.flossconsulting.sshtunnel.tunnels;

import it.flossconsulting.sshtunnel.model.*;
import it.flossconsulting.sshtunnel.config.DiagnosticLog;
import it.flossconsulting.sshtunnel.ssh.ConnectionFailure;
import it.flossconsulting.sshtunnel.ssh.MinaTunnel;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

public final class TunnelManager implements AutoCloseable {
    public record Status(ConnectionState state, Integer localPort, String message) { }
    private static final class Entry {
        final MinaTunnel tunnel = new MinaTunnel();
        volatile Status status = new Status(ConnectionState.DISCONNECTED, null, "");
        volatile boolean cancel;
    }
    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> closing = new ConcurrentHashMap<>();
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private final ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
    private final BiConsumer<UUID, Status> listener;
    private final DiagnosticLog log = DiagnosticLog.defaultLog();
    private volatile boolean closed;

    public TunnelManager(BiConsumer<UUID, Status> listener) {
        this.listener = listener;
        monitor.scheduleAtFixedRate(this::checkDropped, 2, 2, TimeUnit.SECONDS);
    }
    public Status status(UUID id) {
        Entry e = entries.get(id);
        return e == null ? new Status(ConnectionState.DISCONNECTED, null, "") : e.status;
    }
    private void publish(UUID id, Entry entry, Status status) {
        if (entries.get(id) != entry) return;
        entry.status = status;
        listener.accept(id, status);
    }
    public synchronized void connect(Profile profile, MinaTunnel.Prompts prompts) {
        if (closed) return;
        Entry e = entries.computeIfAbsent(profile.id(), ignored -> new Entry());
        if (e.status.state() == ConnectionState.CONNECTING || e.status.state() == ConnectionState.CONNECTED) return;
        e.cancel = false;
        CompletableFuture<Void> previousClose = closing.get(profile.id());
        log.record(profile.id(), "SERVER", "CONNECTING", null);
        publish(profile.id(), e, new Status(ConnectionState.CONNECTING, null, "Connecting"));
        workers.execute(() -> {
            try {
                if (previousClose != null) previousClose.join();
                if (e.cancel || closed) return;
                int port = e.tunnel.connect(profile, prompts);
                synchronized (TunnelManager.this) {
                    if (e.cancel || closed) { e.tunnel.close(); publish(profile.id(), e, new Status(ConnectionState.DISCONNECTED, null, "")); }
                    else {
                        log.record(profile.id(), "FORWARD", "CONNECTED", null);
                        publish(profile.id(), e, new Status(ConnectionState.CONNECTED, port, ""));
                    }
                }
            } catch (Exception failure) {
                synchronized (TunnelManager.this) {
                    if (e.cancel || closed) publish(profile.id(), e, new Status(ConnectionState.DISCONNECTED, null, ""));
                    else {
                        ConnectionFailure.Stage stage = failure instanceof ConnectionFailure cf ? cf.stage() : ConnectionFailure.Stage.SERVER;
                        log.record(profile.id(), stage.name(), "ERROR", failure.getCause() == null ? failure : failure.getCause());
                        publish(profile.id(), e, new Status(ConnectionState.ERROR, null, explain(failure)));
                    }
                }
            }
        });
    }
    public synchronized CompletableFuture<Void> disconnectAsync(UUID id) {
        Entry e = entries.remove(id);
        if (e == null) return CompletableFuture.completedFuture(null);
        e.cancel = true;
        log.record(id, "SESSION", "DISCONNECTED", null);
        listener.accept(id, new Status(ConnectionState.DISCONNECTED, null, ""));
        CompletableFuture<Void> done = CompletableFuture.runAsync(e.tunnel::close, workers);
        closing.put(id, done);
        done.whenComplete((ignored, failure) -> closing.remove(id, done));
        return done;
    }
    public void disconnect(UUID id) { disconnectAsync(id); }
    private void checkDropped() {
        entries.forEach((id, e) -> {
            if (e.status.state() == ConnectionState.CONNECTED && !e.tunnel.isOpen()) {
                synchronized (this) {
                    if (e.status.state() == ConnectionState.CONNECTED) {
                        log.record(id, "SESSION", "DROPPED", null);
                        publish(id, e, new Status(ConnectionState.ERROR, null, "SSH connection dropped"));
                    }
                }
            }
        });
    }
    public static String explain(Throwable failure) {
        ConnectionFailure.Stage stage = failure instanceof ConnectionFailure cf ? cf.stage() : ConnectionFailure.Stage.SERVER;
        Throwable cause = failure.getCause() == null ? failure : failure.getCause();
        String message = String.valueOf(cause.getMessage()).toLowerCase(Locale.ROOT);
        return switch (stage) {
            case IDENTITY -> {
                if (message.contains("passphrase")) yield "Incorrect or cancelled key passphrase";
                if (cause instanceof java.security.NoSuchAlgorithmException || message.contains("unsupported"))
                    yield "Unsupported key format or algorithm";
                yield "Could not read the selected private key";
            }
            case AUTHENTICATION -> "Authentication failed: check the username and key";
            case FORWARD -> message.contains("bind") || message.contains("already in use")
                    ? "Local port is already in use" : "Could not open the local listener";
            case SERVER -> {
                if (message.contains("host key") || message.contains("verify")) yield "Host key rejected or changed";
                if (message.contains("timed out") || message.contains("refused") || message.contains("unreachable"))
                    yield "SSH server is unreachable";
                yield "SSH connection failed: check the server, port, and host key";
            }
        };
    }
    @Override public synchronized void close() {
        closed = true;
        entries.forEach((id,e) -> { e.cancel = true; workers.execute(e.tunnel::close); });
        monitor.shutdownNow(); workers.shutdown();
    }
}
