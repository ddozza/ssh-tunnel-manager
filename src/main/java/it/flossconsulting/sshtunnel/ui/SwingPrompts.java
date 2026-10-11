package it.flossconsulting.sshtunnel.ui;

import it.flossconsulting.sshtunnel.ssh.MinaTunnel;
import javax.swing.*;
import java.awt.Component;
import java.util.Arrays;

final class SwingPrompts implements MinaTunnel.Prompts {
    private final Component owner;
    SwingPrompts(Component owner) { this.owner = owner; }
    @Override public boolean trust(String host, String type, String fingerprint) {
        final boolean[] accepted = {false};
        try { SwingUtilities.invokeAndWait(() -> accepted[0] = JOptionPane.showConfirmDialog(owner,
                "Host SSH sconosciuto: " + host + "\nTipo: " + type + "\nFingerprint: " + fingerprint +
                "\n\nFidarsi di questa host key e aggiungerla a known_hosts?", "Verifica host key",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION); }
        catch (Exception ignored) { return false; }
        return accepted[0];
    }
    @Override public String passphrase() {
        final String[] value = {null};
        try { SwingUtilities.invokeAndWait(() -> {
            JPasswordField input = new JPasswordField(24);
            if (JOptionPane.showConfirmDialog(owner, input, "Passphrase della chiave", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                char[] chars = input.getPassword(); value[0] = new String(chars); Arrays.fill(chars, '\0');
            }
        }); } catch (Exception ignored) { }
        return value[0];
    }
}
