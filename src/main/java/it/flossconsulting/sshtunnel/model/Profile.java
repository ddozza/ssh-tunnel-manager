package it.flossconsulting.sshtunnel.model;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public record Profile(UUID id, String name, String sshHost, int sshPort, String username,
                      String remoteHost, int remotePort, int localPort, boolean automaticPort,
                      String identityPath, boolean openBrowser) {
    public Profile {
        if (id == null) throw new IllegalArgumentException("ID mancante");
        if (blank(name)) throw new IllegalArgumentException("Nome mancante");
        if (blank(sshHost) || invalidHost(sshHost)) throw new IllegalArgumentException("Server SSH non valido");
        if (blank(username)) throw new IllegalArgumentException("Utente SSH mancante");
        if (blank(remoteHost) || invalidHost(remoteHost)) throw new IllegalArgumentException("Destinazione remota non valida");
        if (!port(sshPort) || !port(remotePort) || (!automaticPort && !port(localPort)))
            throw new IllegalArgumentException("Le porte devono essere tra 1 e 65535");
        if (blank(identityPath)) throw new IllegalArgumentException("Seleziona una chiave privata");
        name = name.trim();
        sshHost = sshHost.trim();
        username = username.trim();
        remoteHost = remoteHost.trim();
        identityPath = identityPath.trim();
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static boolean port(int n) { return n >= 1 && n <= 65535; }
    private static boolean invalidHost(String s) {
        return s.contains("://") || s.chars().anyMatch(c -> Character.isWhitespace(c) || c == '/' || c == '\\');
    }
    public void validateKey() {
        try {
            Path path = Path.of(identityPath);
            if (!Files.isRegularFile(path) || !Files.isReadable(path)) throw new IllegalArgumentException("Chiave privata non leggibile");
        } catch (java.nio.file.InvalidPathException e) { throw new IllegalArgumentException("Percorso della chiave non valido"); }
    }
    public Profile duplicate() {
        return new Profile(UUID.randomUUID(), name + " (copia)", sshHost, sshPort, username, remoteHost,
                remotePort, localPort, automaticPort, identityPath, openBrowser);
    }
}
