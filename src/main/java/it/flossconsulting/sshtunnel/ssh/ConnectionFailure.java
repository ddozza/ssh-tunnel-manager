package it.flossconsulting.sshtunnel.ssh;

public final class ConnectionFailure extends Exception {
    public enum Stage { SERVER, IDENTITY, AUTHENTICATION, FORWARD }
    private final Stage stage;
    public ConnectionFailure(Stage stage, Exception cause) { super(stage.name(), cause); this.stage = stage; }
    public Stage stage() { return stage; }
}
