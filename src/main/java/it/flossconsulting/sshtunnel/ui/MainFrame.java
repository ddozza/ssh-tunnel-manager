package it.flossconsulting.sshtunnel.ui;

import it.flossconsulting.sshtunnel.config.ProfileStore;
import it.flossconsulting.sshtunnel.config.DiagnosticLog;
import it.flossconsulting.sshtunnel.browser.Browser;
import it.flossconsulting.sshtunnel.model.*;
import it.flossconsulting.sshtunnel.tunnels.TunnelManager;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public final class MainFrame extends JFrame {
    private final ProfileStore store = ProfileStore.defaultStore();
    private final Browser browser = Browser.system();
    private final DiagnosticLog diagnosticLog = DiagnosticLog.defaultLog();
    private final List<Profile> profiles = new ArrayList<>();
    private final Set<UUID> deleting = new HashSet<>();
    private boolean storageReady = true;
    private final Table tableModel = new Table();
    private final JTable table = new JTable(tableModel);
    private final JTextArea logArea = new JTextArea();
    private final JLabel logTitle = new JLabel("Connection log");
    private final List<JMenuItem> actions;
    private final TunnelManager tunnels = new TunnelManager((id, status) -> SwingUtilities.invokeLater(() -> {
        refreshTable();
        Profile p = profiles.stream().filter(x -> x.id().equals(id)).findFirst().orElse(null);
        if (p != null && status.state() == ConnectionState.CONNECTED && p.openBrowser()) openBrowser(id);
        if (p != null && status.state() == ConnectionState.ERROR) JOptionPane.showMessageDialog(this, status.message(), p.name(), JOptionPane.ERROR_MESSAGE);
    }));
    public MainFrame() {
        super("SSH Tunnel Manager");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) { tunnels.close(); dispose(); } });
        try { profiles.addAll(store.load()); }
        catch (Exception e) { storageReady = false; JOptionPane.showMessageDialog(this,
                "Could not read profiles: " + e.getMessage() + "\nThe file will not be overwritten.", "Error", JOptionPane.ERROR_MESSAGE); }
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        table.setRowHeight(28);
        table.getColumnModel().getColumn(4).setCellRenderer(new StatusRenderer());
        table.getColumnModel().getColumn(4).setPreferredWidth(140);
        table.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) { updateActions(); refreshLog(); } });

        JPopupMenu rowMenu = new JPopupMenu();
        JPopupMenu emptyMenu = new JPopupMenu();
        JMenuItem newItem = menuItem("New", this::addProfile);
        newItem.setEnabled(storageReady);
        emptyMenu.add(newItem);
        actions = List.of(menuItem("Edit", this::edit), menuItem("Duplicate", this::duplicate),
                menuItem("Delete", this::delete), menuItem("Connect", this::connect),
                menuItem("Disconnect", this::disconnect), menuItem("Open in browser", () -> {
                    Profile p = selected(); if (p != null) openBrowser(p.id());
                }));
        for (int i = 0; i < actions.size(); i++) {
            if (i == 3 || i == 5) rowMenu.addSeparator();
            rowMenu.add(actions.get(i));
        }
        JScrollPane tableScroll = new JScrollPane(table);
        MouseAdapter popupHandler = new MouseAdapter() {
            private void showPopup(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                Point tablePoint = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), table);
                int row = table.rowAtPoint(tablePoint);
                if (row < 0) {
                    table.clearSelection();
                    emptyMenu.show(e.getComponent(), e.getX(), e.getY());
                } else {
                    table.setRowSelectionInterval(row, row);
                    updateActions();
                    rowMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
            @Override public void mousePressed(MouseEvent e) { showPopup(e); }
            @Override public void mouseReleased(MouseEvent e) { showPopup(e); }
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) edit();
            }
        };
        table.addMouseListener(popupHandler);
        tableScroll.getViewport().addMouseListener(popupHandler);
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("shift F10"), "contextMenu");
        table.getActionMap().put("contextMenu", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                int row = table.getSelectedRow();
                if (row >= 0) {
                    Rectangle cell = table.getCellRect(row, 0, true);
                    updateActions(); rowMenu.show(table, cell.x + 12, cell.y + cell.height);
                } else emptyMenu.show(table, 12, 12);
            }
        });

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setText("Select a tunnel to view its connection log.");
        logTitle.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.add(logTitle, BorderLayout.NORTH);
        logPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, logPanel);
        split.setResizeWeight(0.55);
        split.setDividerLocation(290);
        add(split, BorderLayout.CENTER);
        add(new JLabel("Right-click an empty area for New, or a tunnel for its actions."), BorderLayout.SOUTH);
        setSize(1050, 590); setLocationRelativeTo(null); updateActions();
    }
    private JMenuItem menuItem(String name, Runnable action) {
        JMenuItem item = new JMenuItem(name); item.addActionListener(e -> action.run()); return item;
    }
    private Profile selected() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) return null;
        int modelRow = table.convertRowIndexToModel(row);
        return modelRow >= profiles.size() ? null : profiles.get(modelRow);
    }
    private void refreshTable() {
        Profile focus = selected();
        tableModel.fireTableDataChanged();
        if (focus != null) selectProfile(focus.id());
        updateActions(); refreshLog();
    }
    private void selectProfile(UUID id) {
        for (int modelRow = 0; modelRow < profiles.size(); modelRow++) {
            if (profiles.get(modelRow).id().equals(id)) {
                int viewRow = table.convertRowIndexToView(modelRow);
                if (viewRow >= 0) table.setRowSelectionInterval(viewRow, viewRow);
                return;
            }
        }
        table.clearSelection();
    }
    private void refreshLog() {
        Profile p = selected();
        if (p == null) {
            logTitle.setText("Connection log");
            logArea.setText("Select a tunnel to view its connection log.");
            return;
        }
        TunnelManager.Status current = tunnels.status(p.id());
        logTitle.setText("Connection log — " + p.name());
        StringBuilder content = new StringBuilder();
        for (DiagnosticLog.Event event : diagnosticLog.events(p.id())) {
            content.append(event.time()).append("  ").append(describe(event)).append('\n');
        }
        if (content.isEmpty()) content.append("No events recorded.\n");
        content.append("\nCurrent state: ").append(stateText(current.state()));
        if (current.localPort() != null) content.append(" — 127.0.0.1:").append(current.localPort());
        if (current.state() == ConnectionState.ERROR && !current.message().isBlank())
            content.append(" — ").append(current.message());
        logArea.setText(content.toString());
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }
    private static String describe(DiagnosticLog.Event event) {
        String stage = switch (event.stage()) {
            case "SERVER" -> "SSH connection / host key";
            case "IDENTITY" -> "key loading";
            case "AUTHENTICATION" -> "authentication";
            case "FORWARD" -> "local port forwarding";
            case "SESSION" -> "SSH session";
            default -> event.stage();
        };
        return switch (event.outcome()) {
            case "CONNECTING" -> "Connecting";
            case "CONNECTED" -> "Connected; local listener active";
            case "DISCONNECTED" -> "Disconnected";
            case "DROPPED" -> "Connection dropped";
            case "ERROR" -> "Error during " + stage + (event.type().isEmpty() ? "" : " (" + event.type() + ")");
            default -> event.outcome() + " — " + stage;
        };
    }
    private static String stateText(ConnectionState state) {
        return switch (state) {
            case DISCONNECTED -> "Disconnected";
            case CONNECTING -> "Connecting";
            case CONNECTED -> "Connected";
            case ERROR -> "Error";
        };
    }
    private void updateActions() {
        if (actions == null) return;
        if (!storageReady) { for (JMenuItem item : actions) item.setEnabled(false); return; }
        Profile p = selected();
        for (JMenuItem item : actions) item.setEnabled(p != null);
        if (p == null) return;
        if (deleting.contains(p.id())) { for (JMenuItem item : actions) item.setEnabled(false); return; }
        TunnelManager.Status s = tunnels.status(p.id());
        actions.get(3).setEnabled(s.state() != ConnectionState.CONNECTING && s.state() != ConnectionState.CONNECTED);
        actions.get(4).setEnabled(s.state() == ConnectionState.CONNECTING || s.state() == ConnectionState.CONNECTED);
        actions.get(5).setEnabled(s.state() == ConnectionState.CONNECTED);
    }
    private void persist() {
        try { store.save(profiles); refreshTable(); }
        catch (Exception e) { JOptionPane.showMessageDialog(this, "Could not save profiles: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); }
    }
    private boolean confirmName(Profile p) {
        boolean duplicate = profiles.stream().anyMatch(other -> !other.id().equals(p.id()) && other.name().equalsIgnoreCase(p.name()));
        return !duplicate || JOptionPane.showConfirmDialog(this, "A profile with this name already exists. Continue?",
                "Duplicate name", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }
    private void addProfile() { Profile p = new ProfileDialog(this, null).result(); if (p != null && confirmName(p)) { profiles.add(p); persist(); selectProfile(p.id()); } }
    private void edit() {
        Profile p = selected(); if (p == null || deleting.contains(p.id())) return;
        Profile edited = new ProfileDialog(this, p).result(); if (edited != null && confirmName(edited)) { profiles.set(profiles.indexOf(p), edited); persist(); }
    }
    private void duplicate() { Profile p = selected(); if (p != null) { Profile copy = p.duplicate(); profiles.add(copy); persist(); selectProfile(copy.id()); } }
    private void delete() {
        Profile p = selected(); if (p == null) return;
        String message = "Delete profile '" + p.name() + "'?";
        if (tunnels.status(p.id()).state() == ConnectionState.CONNECTED || tunnels.status(p.id()).state() == ConnectionState.CONNECTING)
            message += "\nIts tunnel will be disconnected first.";
        if (JOptionPane.showConfirmDialog(this, message, "Confirm deletion", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        deleting.add(p.id()); updateActions();
        tunnels.disconnectAsync(p.id()).thenRun(() -> SwingUtilities.invokeLater(() -> {
            profiles.remove(p); deleting.remove(p.id()); persist();
        }));
    }
    private void connect() {
        Profile p = selected(); if (p == null) return;
        tunnels.connect(p, new SwingPrompts(this));
        refreshTable();
    }
    private void disconnect() { Profile p = selected(); if (p != null) { tunnels.disconnect(p.id()); refreshTable(); } }
    private void openBrowser(UUID id) {
        Integer port = tunnels.status(id).localPort(); if (port == null) return;
        Thread task = new Thread(() -> {
            try { browser.open(port); }
            catch (Exception e) { SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                    "Browser unavailable: " + e.getClass().getSimpleName())); }
        }, "open-browser");
        task.setDaemon(true); task.start();
    }
    private final class Table extends AbstractTableModel {
        private final String[] columns = {"Name", "SSH server", "Remote destination", "Local endpoint", "State"};
        @Override public int getRowCount() { return profiles.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int col) { return columns[col]; }
        @Override public Class<?> getColumnClass(int col) { return col == 4 ? ConnectionState.class : String.class; }
        @Override public Object getValueAt(int row, int col) {
            Profile p = profiles.get(row); TunnelManager.Status s = tunnels.status(p.id());
            return switch (col) {
                case 0 -> p.name(); case 1 -> p.sshHost() + ":" + p.sshPort();
                case 2 -> p.remoteHost() + ":" + p.remotePort();
                case 3 -> s.localPort() == null ? "" : "127.0.0.1:" + s.localPort();
                default -> s.state();
            };
        }
    }
    private static final class StatusRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            ConnectionState state = value instanceof ConnectionState s ? s : ConnectionState.DISCONNECTED;
            Color color = switch (state) {
                case CONNECTED -> new Color(0x198754);
                case CONNECTING -> new Color(0xB77900);
                case DISCONNECTED, ERROR -> new Color(0xC62828);
            };
            setIcon(new DotIcon(color));
            setIconTextGap(8);
            setText(stateText(state));
            setToolTipText(stateText(state));
            return this;
        }
    }
    private record DotIcon(Color color) implements Icon {
        @Override public int getIconWidth() { return 12; }
        @Override public int getIconHeight() { return 12; }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(color); g.fillOval(x + 1, y + 1, 10, 10);
            } finally { g.dispose(); }
        }
    }
}
