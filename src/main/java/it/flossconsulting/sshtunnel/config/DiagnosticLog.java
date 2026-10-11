package it.flossconsulting.sshtunnel.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Deliberately records no hostnames, usernames, paths, exception messages or stack traces. */
public final class DiagnosticLog {
    public record Event(String time, String stage, String outcome, String type) { }
    private static final long MAX_BYTES = 1_000_000;
    private static final DiagnosticLog DEFAULT = new DiagnosticLog(ProfileStore.defaultStore().directory().resolve("diagnostic.log"));
    private final Path file;
    public DiagnosticLog(Path file) { this.file = file; }
    public static DiagnosticLog defaultLog() { return DEFAULT; }
    public Path path() { return file; }
    public synchronized void record(UUID profileId, String stage, String outcome, Throwable failure) {
        String type = failure == null ? "" : failure.getClass().getSimpleName();
        String line = Instant.now() + " profile=" + profileId + " stage=" + stage + " outcome=" + outcome
                + (type.isEmpty() ? "" : " type=" + type) + System.lineSeparator();
        try {
            Files.createDirectories(file.getParent());
            if (Files.exists(file) && Files.size(file) > MAX_BYTES)
                Files.move(file, file.resolveSibling(file.getFileName() + ".old"), StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            System.err.println("Diagnostic log unavailable");
        }
    }
    public synchronized List<Event> events(UUID profileId) {
        List<Event> result = new ArrayList<>();
        for (Path source : List.of(file.resolveSibling(file.getFileName() + ".old"), file)) {
            if (!Files.isRegularFile(source)) continue;
            try {
                for (String line : Files.readAllLines(source, StandardCharsets.UTF_8)) {
                    String[] parts = line.split(" ");
                    if (parts.length < 4 || !parts[1].equals("profile=" + profileId)
                            || !parts[2].startsWith("stage=") || !parts[3].startsWith("outcome=")) continue;
                    String type = parts.length > 4 && parts[4].startsWith("type=") ? parts[4].substring(5) : "";
                    result.add(new Event(parts[0], parts[2].substring(6), parts[3].substring(8), type));
                }
            } catch (IOException ignored) { /* The live status remains available if the log cannot be read. */ }
        }
        return result.size() > 200 ? new ArrayList<>(result.subList(result.size() - 200, result.size())) : result;
    }
}
