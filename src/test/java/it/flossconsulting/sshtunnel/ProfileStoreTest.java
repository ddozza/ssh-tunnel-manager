package it.flossconsulting.sshtunnel;

import it.flossconsulting.sshtunnel.config.ProfileStore;
import it.flossconsulting.sshtunnel.model.Profile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ProfileStoreTest {
    @TempDir Path temp;
    @Test void savesSeveralProfilesWithStableIdsAndUnicodePaths() throws Exception {
        Profile a = profile("First", "key with spaces 🔑");
        Profile b = profile("First", "another key");
        ProfileStore store = new ProfileStore(temp.resolve("config").resolve("profiles.json"));
        store.save(List.of(a, b));
        assertEquals(List.of(a, b), store.load());
        assertNotEquals(a.id(), b.id());
        assertNotEquals(a.id(), a.duplicate().id());
    }
    @Test void rejectsInvalidPortsAndTargets() {
        assertThrows(IllegalArgumentException.class, () -> new Profile(UUID.randomUUID(), "x", "example.org", 22,
                "user", "http://localhost", 80, 0, true, "key", true));
        assertThrows(IllegalArgumentException.class, () -> new Profile(UUID.randomUUID(), "x", "example.org", 22,
                "user", "127.0.0.1", 0, 0, true, "key", true));
    }
    private Profile profile(String name, String key) {
        return new Profile(UUID.randomUUID(), name, "example.org", 22, "user", "127.0.0.1", 18000, 0, true,
                temp.resolve(key).toString(), true);
    }
}
