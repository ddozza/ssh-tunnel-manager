package it.flossconsulting.sshtunnel.browser;

import java.awt.Desktop;
import java.net.URI;

@FunctionalInterface
public interface Browser {
    void open(int port) throws Exception;

    static Browser system() {
        return port -> Desktop.getDesktop().browse(URI.create("http://127.0.0.1:" + port + "/"));
    }
}
