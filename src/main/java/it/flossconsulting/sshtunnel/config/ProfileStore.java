package it.flossconsulting.sshtunnel.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.flossconsulting.sshtunnel.model.Profile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class ProfileStore {
    private final Path file;
    private final Gson gson = new Gson();
    public ProfileStore(Path file) { this.file = file.toAbsolutePath(); }
    public Path directory() { return file.getParent(); }
    public static ProfileStore defaultStore() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home");
        Path dir;
        if (os.contains("win")) dir = Path.of(System.getenv().getOrDefault("APPDATA", home), "SSH Tunnel Manager");
        else if (os.contains("mac")) dir = Path.of(home, "Library", "Application Support", "SSH Tunnel Manager");
        else dir = Path.of(System.getenv().getOrDefault("XDG_CONFIG_HOME", Path.of(home, ".config").toString()), "ssh-tunnel-manager");
        return new ProfileStore(dir.resolve("profiles.json"));
    }
    public List<Profile> load() throws IOException {
        if (!Files.exists(file)) return new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("schemaVersion").getAsInt() != 1) throw new IOException("Versione configurazione non supportata");
            List<Profile> result = new ArrayList<>();
            Set<UUID> ids = new HashSet<>();
            for (var item : root.getAsJsonArray("profiles")) {
                Profile p = gson.fromJson(item, Profile.class);
                if (!ids.add(p.id())) throw new IOException("ID di profilo duplicato");
                result.add(p);
            }
            return result;
        } catch (RuntimeException e) { throw new IOException("Configurazione non valida: " + e.getClass().getSimpleName(), e); }
    }
    public void save(Collection<Profile> profiles) throws IOException {
        Files.createDirectories(file.getParent());
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.add("profiles", gson.toJsonTree(profiles));
        Path temp = Files.createTempFile(file.getParent(), "profiles-", ".tmp");
        try {
            Files.writeString(temp, gson.toJson(root), StandardCharsets.UTF_8);
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
