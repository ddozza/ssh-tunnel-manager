package it.flossconsulting.sshtunnel;

import it.flossconsulting.sshtunnel.ui.MainFrame;
import javax.swing.*;

public final class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) { }
            new MainFrame().setVisible(true);
        });
    }
}
