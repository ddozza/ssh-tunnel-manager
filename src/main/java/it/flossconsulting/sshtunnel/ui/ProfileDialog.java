package it.flossconsulting.sshtunnel.ui;

import it.flossconsulting.sshtunnel.model.Profile;
import it.flossconsulting.sshtunnel.config.DiagnosticLog;
import it.flossconsulting.sshtunnel.ssh.ConnectionFailure;
import it.flossconsulting.sshtunnel.ssh.MinaTunnel;
import it.flossconsulting.sshtunnel.tunnels.TunnelManager;
import javax.swing.*;
import java.awt.*;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

final class ProfileDialog extends JDialog {
    private final JTextField name = new JTextField(24), host = new JTextField(24), sshPort = new JTextField("22", 8),
            user = new JTextField(System.getProperty("user.name", ""), 24), remoteHost = new JTextField("127.0.0.1", 24),
            remotePort = new JTextField(8), localPort = new JTextField(8), key = new JTextField(24);
    private final JCheckBox automatic = new JCheckBox("Select local port automatically", true), browser = new JCheckBox("Open browser after connecting", true);
    private Profile result;
    ProfileDialog(JFrame owner, Profile original) {
        super(owner, original == null ? "Add connection" : "Edit connection", true);
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints(); c.insets = new Insets(4, 6, 4, 6); c.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;
        addRow(form, c, row++, "Name", name);
        addRow(form, c, row++, "Server SSH", host);
        addRow(form, c, row++, "SSH port", sshPort);
        addRow(form, c, row++, "SSH username", user);
        addRow(form, c, row++, "Remote host", remoteHost);
        addRow(form, c, row++, "Remote port", remotePort);
        addRow(form, c, row++, "Local port", localPort);
        addRow(form, c, row++, "", automatic);
        JPanel keyRow = new JPanel(new BorderLayout(4, 0)); keyRow.add(key, BorderLayout.CENTER);
        JButton choose = new JButton("Browse…"); keyRow.add(choose, BorderLayout.EAST);
        choose.addActionListener(e -> { JFileChooser chooser = new JFileChooser(); if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) key.setText(chooser.getSelectedFile().toString()); });
        addRow(form, c, row++, "Private key", keyRow);
        addRow(form, c, row++, "", browser);
        automatic.addActionListener(e -> localPort.setEnabled(!automatic.isSelected()));
        if (original != null) {
            name.setText(original.name()); host.setText(original.sshHost()); sshPort.setText(String.valueOf(original.sshPort()));
            user.setText(original.username()); remoteHost.setText(original.remoteHost()); remotePort.setText(String.valueOf(original.remotePort()));
            localPort.setText(original.automaticPort() ? "" : String.valueOf(original.localPort())); automatic.setSelected(original.automaticPort());
            key.setText(original.identityPath()); browser.setSelected(original.openBrowser());
        }
        localPort.setEnabled(!automatic.isSelected());
        JButton save = new JButton("Save"), cancel = new JButton("Cancel"), test = new JButton("Test");
        test.setVisible(original == null);
        save.addActionListener(e -> {
            try {
                Profile candidate = createProfile(original); candidate.validateKey(); result = candidate; dispose();
            } catch (NumberFormatException ex) { JOptionPane.showMessageDialog(this, "Enter valid numeric ports", "Error", JOptionPane.ERROR_MESSAGE); }
            catch (IllegalArgumentException ex) { JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); }
        });
        test.addActionListener(e -> {
            final Profile candidate;
            try { candidate = createProfile(original); candidate.validateKey(); }
            catch (RuntimeException ex) { JOptionPane.showMessageDialog(this, "Check the fields and key path: " + ex.getMessage(),
                    "Invalid input", JOptionPane.ERROR_MESSAGE); return; }
            test.setEnabled(false); test.setText("Testing…");
            new SwingWorker<Integer, Void>() {
                @Override protected Integer doInBackground() throws Exception {
                    DiagnosticLog log = DiagnosticLog.defaultLog();
                    log.record(candidate.id(), "SERVER", "TEST_START", null);
                    try (MinaTunnel tunnel = new MinaTunnel()) {
                        int port = tunnel.connect(candidate, new SwingPrompts(ProfileDialog.this));
                        log.record(candidate.id(), "FORWARD", "TEST_OK", null);
                        return port;
                    } catch (Exception failure) {
                        ConnectionFailure.Stage stage = failure instanceof ConnectionFailure cf ? cf.stage() : ConnectionFailure.Stage.SERVER;
                        log.record(candidate.id(), stage.name(), "TEST_ERROR", failure.getCause() == null ? failure : failure.getCause());
                        throw failure;
                    }
                }
                @Override protected void done() {
                    test.setText("Test"); test.setEnabled(true);
                    if (!isDisplayable()) return;
                    try { JOptionPane.showMessageDialog(ProfileDialog.this,
                            "SSH connection and local listener verified at 127.0.0.1:" + get() + ".\nThe test tunnel has been closed.",
                            "Test successful", JOptionPane.INFORMATION_MESSAGE); }
                    catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                    catch (ExecutionException ex) { JOptionPane.showMessageDialog(ProfileDialog.this,
                            TunnelManager.explain(ex.getCause()), "Test failed", JOptionPane.ERROR_MESSAGE); }
                }
            }.execute();
        });
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT)); buttons.add(test); buttons.add(cancel); buttons.add(save);
        add(new JScrollPane(form), BorderLayout.CENTER); add(buttons, BorderLayout.SOUTH);
        pack(); setMinimumSize(new Dimension(510, 430)); setLocationRelativeTo(owner);
    }
    private static void addRow(JPanel form, GridBagConstraints c, int row, String label, Component input) {
        c.gridy = row; c.gridx = 0; c.weightx = 0; form.add(new JLabel(label), c);
        c.gridx = 1; c.weightx = 1; form.add(input, c);
    }
    private Profile createProfile(Profile original) {
        return new Profile(original == null ? UUID.randomUUID() : original.id(), name.getText(), host.getText(),
                Integer.parseInt(sshPort.getText().trim()), user.getText(), remoteHost.getText(), Integer.parseInt(remotePort.getText().trim()),
                automatic.isSelected() ? 0 : Integer.parseInt(localPort.getText().trim()), automatic.isSelected(), key.getText(), browser.isSelected());
    }
    Profile result() { setVisible(true); return result; }
}
