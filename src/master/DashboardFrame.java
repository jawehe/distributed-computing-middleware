package master;

import common.Snapshot;
import common.WorkerStatus;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/** Dashboard graphique (Swing) : Workers, charge, état, tâches, historique. */
public class DashboardFrame extends JFrame {
    private final MasterImpl master;
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"Worker", "Adresse", "État", "Charge", "En cours", "Terminées", "Dernier heartbeat"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JLabel stats = new JLabel(" ");
    private final JTextArea history = new JTextArea();

    public DashboardFrame(MasterImpl master) {
        super("Master - Dashboard d'orchestration");
        this.master = master;
        JTable table = new JTable(model);
        table.setRowHeight(24);
        table.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                String s = String.valueOf(v);
                comp.setForeground(Color.WHITE);
                comp.setBackground(s.equals("ONLINE") ? new Color(0x2e7d32) : s.equals("BUSY") ? new Color(0xef6c00) : new Color(0xc62828));
                return comp;
            }
        });
        stats.setFont(stats.getFont().deriveFont(Font.BOLD, 14f));
        stats.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        history.setEditable(false);
        history.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), new JScrollPane(history));
        split.setResizeWeight(0.35);
        setLayout(new BorderLayout());
        add(stats, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        setSize(1000, 650);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        new Timer(1000, e -> refresh()).start();
    }

    private void refresh() {
        Snapshot s = master.snapshot();
        long online = s.workers.stream().filter(w -> w.status != WorkerStatus.FAILED).count();
        stats.setText(String.format("Workers connectés : %d/%d   |   En attente : %d   |   En cours : %d   |   Terminées : %d   |   Réassignées : %d   |   Jobs : %d",
                online, s.workers.size(), s.pending, s.running, s.completed, s.retried, s.jobsDone));
        model.setRowCount(0);
        for (Snapshot.WorkerRow w : s.workers)
            model.addRow(new Object[]{w.id, w.host + ":" + w.port, w.status.name(), Math.round(w.load * 100) + " %",
                    w.inFlight + "/" + w.capacity, w.completed, String.format("il y a %.1f s", w.lastBeatAgoMs / 1000.0)});
        history.setText(String.join("\n", s.history));
        history.setCaretPosition(0);
    }
}
