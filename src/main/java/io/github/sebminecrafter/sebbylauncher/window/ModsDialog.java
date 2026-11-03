package io.github.sebminecrafter.sebbylauncher.window;

import io.github.sebminecrafter.sebbylauncher.Instance;
import io.github.sebminecrafter.sebbylauncher.mods.ModrinthClient;
import io.github.sebminecrafter.sebbylauncher.mods.ModrinthClient.ContentType;
import io.github.sebminecrafter.sebbylauncher.mods.ModrinthClient.ContentUpdate;
import io.github.sebminecrafter.sebbylauncher.mods.ModrinthClient.InstalledContent;
import io.github.sebminecrafter.sebbylauncher.mods.ModrinthClient.Project;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static io.github.sebminecrafter.sebbylauncher.window.Window.*;

final class ModsDialog {
    private final Instance instance;
    private final ModrinthClient client = new ModrinthClient();
    private final JDialog dialog;
    private final JLabel status = new JLabel("Manage instance mods and packs with Modrinth.");
    private final Map<ContentType, ContentTab> contentTabs = new EnumMap<>(ContentType.class);

    private ModsDialog(Instance instance) {
        this.instance = instance;
        dialog = new JDialog(frame, "Mods & Packs — " + instance.name(), true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.getRootPane().setBorder(new EmptyBorder(12, 12, 12, 12));
        dialog.setMinimumSize(new Dimension(720, 500));
        dialog.setSize(900, 650);
        dialog.setLocationRelativeTo(frame);

        JTabbedPane categories = new JTabbedPane();
        for (ContentType type : ContentType.values()) {
            ContentTab tab = new ContentTab(type);
            contentTabs.put(type, tab);
            categories.addTab(tabTitle(type), tab.panel);
        }
        dialog.add(categories, BorderLayout.CENTER);
        status.setBorder(new EmptyBorder(4, 0, 0, 0));
        dialog.add(status, BorderLayout.SOUTH);
    }

    static void open(Instance instance) {
        ModsDialog modsDialog = new ModsDialog(instance);
        modsDialog.contentTabs.values().forEach(ContentTab::refreshInstalled);
        modsDialog.dialog.setVisible(true);
    }

    private static String tabTitle(ContentType type) {
        return switch (type) {
            case MOD -> "Mods";
            case RESOURCE_PACK -> "Resource Packs";
            case DATA_PACK -> "Data Packs";
            case SHADER_PACK -> "Shader Packs";
        };
    }

    private void showFailure(String title, Exception error) {
        Throwable cause = error instanceof ExecutionException && error.getCause() != null
                ? error.getCause() : error;
        String message = cause.getMessage() == null ? cause.toString() : cause.getMessage();
        logger.error(title + ": " + message);
        status.setText(title + ".");
        JOptionPane.showMessageDialog(dialog, message, title, JOptionPane.ERROR_MESSAGE);
    }

    private final class ContentTab {
        private final ContentType type;
        private final JPanel panel = new JPanel(new BorderLayout(8, 8));
        private final JTextField queryField = new JTextField();
        private final JButton searchButton = new JButton("Search Modrinth");
        private final DefaultListModel<Project> projects = new DefaultListModel<>();
        private final JList<Project> projectList = new JList<>(projects);
        private final JEditorPane projectDescription = new JEditorPane("text/html", "");
        private final JButton installButton = new JButton("Install selected");
        private final InstalledTableModel installedModel = new InstalledTableModel();
        private final JTable installedTable = new JTable(installedModel);
        private final JButton checkUpdatesButton = new JButton("Check for updates");
        private final JButton updateSelectedButton = new JButton("Update selected");
        private final JButton updateAllButton = new JButton("Update all available");
        private final JButton removeButton = new JButton("Remove selected");
        private List<ContentUpdate> updates = List.of();

        private ContentTab(ContentType type) {
            this.type = type;
            JTabbedPane views = new JTabbedPane();
            views.addTab("Browse", createBrowsePanel());
            views.addTab("Installed", createInstalledPanel());
            panel.add(views, BorderLayout.CENTER);

            searchButton.addActionListener(_ -> search());
            queryField.addActionListener(_ -> search());
            installButton.addActionListener(_ -> installSelected());
            checkUpdatesButton.addActionListener(_ -> checkUpdates());
            updateSelectedButton.addActionListener(_ -> updateSelected());
            updateAllButton.addActionListener(_ -> updateAll());
            removeButton.addActionListener(_ -> removeSelected());
        }

        private JPanel createBrowsePanel() {
            JPanel browse = new JPanel(new BorderLayout(8, 8));
            JPanel searchBar = new JPanel(new BorderLayout(8, 0));
            searchBar.add(new JLabel("Search"), BorderLayout.WEST);
            searchBar.add(queryField, BorderLayout.CENTER);
            searchBar.add(searchButton, BorderLayout.EAST);
            browse.add(searchBar, BorderLayout.NORTH);

            projectList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            projectList.setCellRenderer(new ProjectRenderer());
            projectList.addListSelectionListener(_ -> showProjectDetails());
            projectDescription.setEditable(false);
            JSplitPane browseSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                    new JScrollPane(projectList), new JScrollPane(projectDescription));
            browseSplit.setResizeWeight(0.7);
            browseSplit.setBorder(null);
            JPanel resultPanel = new JPanel(new BorderLayout(8, 8));
            resultPanel.add(new JLabel("Minecraft " + instance.version() + " · " + instance.loader()
                    + " · compatible " + type.pluralLabel()), BorderLayout.NORTH);
            resultPanel.add(browseSplit, BorderLayout.CENTER);
            browse.add(resultPanel, BorderLayout.CENTER);

            JPanel installRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            installRow.add(installButton);
            browse.add(installRow, BorderLayout.SOUTH);
            installButton.setEnabled(false);
            return browse;
        }

        private JPanel createInstalledPanel() {
            JPanel installed = new JPanel(new BorderLayout(8, 8));
            installed.add(new JLabel(type.pluralLabel() + " in "
                    + instance.gameDirectory().resolve(type.directoryName())), BorderLayout.NORTH);
            installedTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            installedTable.setFillsViewportHeight(true);
            installedTable.setRowHeight(26);
            installedTable.getColumnModel().getColumn(1).setMaxWidth(90);
            installed.add(new JScrollPane(installedTable), BorderLayout.CENTER);

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            actions.add(checkUpdatesButton);
            actions.add(updateSelectedButton);
            actions.add(updateAllButton);
            actions.add(removeButton);
            installed.add(actions, BorderLayout.SOUTH);
            checkUpdatesButton.setEnabled(false);
            updateSelectedButton.setEnabled(false);
            updateAllButton.setEnabled(false);
            removeButton.setEnabled(false);
            installedTable.getSelectionModel().addListSelectionListener(_ -> updateActionStates());
            return installed;
        }

        private void search() {
            String query = queryField.getText().trim();
            searchButton.setEnabled(false);
            status.setText("Searching Modrinth for " + type.pluralLabel() + "…");
            new SwingWorker<List<Project>, Void>() {
                @Override
                protected List<Project> doInBackground() throws Exception {
                    return client.search(query, instance.version(), instance.loader(), type);
                }

                @Override
                protected void done() {
                    searchButton.setEnabled(true);
                    try {
                        List<Project> found = get();
                        projects.clear();
                        found.forEach(projects::addElement);
                        projectDescription.setText("");
                        status.setText(found.size() + " compatible " + type.pluralLabel() + " found.");
                    } catch (Exception e) {
                        showFailure("Modrinth search failed", e);
                    }
                }
            }.execute();
        }

        private void showProjectDetails() {
            Project selected = projectList.getSelectedValue();
            installButton.setEnabled(selected != null);
            if (selected == null) {
                projectDescription.setText("");
                return;
            }
            String description = selected.description() == null ? "" : selected.description();
            projectDescription.setText("<html><body style='font-family:sans-serif;padding:8px'>"
                    + "<h2>" + PlayPage.escapeHtml(selected.title()) + "</h2><p>"
                    + PlayPage.escapeHtml(description).replace("\n", "<br>")
                    + "</p><p>" + String.format("%,d downloads", selected.downloads())
                    + "</p></body></html>");
            projectDescription.setCaretPosition(0);
        }

        private void installSelected() {
            Project selected = projectList.getSelectedValue();
            if (selected == null) return;
            installButton.setEnabled(false);
            status.setText("Downloading " + selected.title() + "…");
            new SwingWorker<InstalledContent, Void>() {
                @Override
                protected InstalledContent doInBackground() throws Exception {
                    return client.install(instance, selected);
                }

                @Override
                protected void done() {
                    installButton.setEnabled(projectList.getSelectedValue() != null);
                    try {
                        InstalledContent installed = get();
                        status.setText("Installed " + installed.title() + " as " + installed.fileName() + ".");
                        refreshInstalled();
                    } catch (Exception e) {
                        showFailure(type.label() + " could not be installed", e);
                    }
                }
            }.execute();
        }

        private void refreshInstalled() {
            setActionsEnabled(false);
            new SwingWorker<List<InstalledContent>, Void>() {
                @Override
                protected List<InstalledContent> doInBackground() throws Exception {
                    return client.listInstalled(instance, type);
                }

                @Override
                protected void done() {
                    try {
                        List<InstalledContent> found = get();
                        installedModel.setRows(found.stream().map(content -> new InstalledRow(content, null)).toList());
                        updates = List.of();
                        installedTable.setEnabled(true);
                        checkUpdatesButton.setEnabled(!found.isEmpty());
                        removeButton.setEnabled(false);
                        updateSelectedButton.setEnabled(false);
                        updateAllButton.setEnabled(false);
                        status.setText(found.size() + " " + type.pluralLabel() + " installed.");
                    } catch (Exception e) {
                        installedTable.setEnabled(true);
                        showFailure("Installed " + type.pluralLabel() + " could not be loaded", e);
                    }
                }
            }.execute();
        }

        private void checkUpdates() {
            checkUpdatesButton.setEnabled(false);
            updateSelectedButton.setEnabled(false);
            updateAllButton.setEnabled(false);
            installedTable.setEnabled(false);
            status.setText("Checking installed " + type.pluralLabel() + " by SHA-1 hash with Modrinth…");
            new SwingWorker<List<ContentUpdate>, Void>() {
                @Override
                protected List<ContentUpdate> doInBackground() throws Exception {
                    return client.checkForUpdates(instance, type);
                }

                @Override
                protected void done() {
                    installedTable.setEnabled(true);
                    checkUpdatesButton.setEnabled(installedModel.getRowCount() > 0);
                    try {
                        updates = get();
                        Map<String, ContentUpdate> byFile = new java.util.HashMap<>();
                        updates.forEach(update -> byFile.put(update.installed().fileName(), update));
                        List<InstalledRow> rows = new ArrayList<>();
                        for (int i = 0; i < installedModel.getRowCount(); i++) {
                            InstalledContent content = installedModel.rowAt(i).installed();
                            ContentUpdate update = byFile.get(content.fileName());
                            InstalledContent identified = update == null ? content : update.installed();
                            rows.add(new InstalledRow(identified, update));
                        }
                        installedModel.setRows(rows);
                        updateAllButton.setEnabled(!updates.isEmpty());
                        updateActionStates();
                        status.setText(updates.isEmpty()
                                ? "No Modrinth updates found for the installed " + type.pluralLabel() + "."
                                : updates.size() + " " + type.label().toLowerCase() + " update(s) available.");
                    } catch (Exception e) {
                        showFailure(type.label() + " updates could not be checked", e);
                    }
                }
            }.execute();
        }

        private void updateSelected() {
            InstalledRow selected = selectedRow();
            if (selected == null || selected.update() == null) {
                status.setText("Select an item with an available update.");
                return;
            }
            runUpdates(List.of(selected.update()));
        }

        private void updateAll() {
            if (!updates.isEmpty()) runUpdates(new ArrayList<>(updates));
        }

        private void runUpdates(List<ContentUpdate> pending) {
            setActionsEnabled(false);
            status.setText("Updating " + pending.size() + " " + type.pluralLabel() + "…");
            new SwingWorker<Integer, Void>() {
                @Override
                protected Integer doInBackground() throws Exception {
                    int completed = 0;
                    for (ContentUpdate update : pending) {
                        client.update(instance, update);
                        completed++;
                    }
                    return completed;
                }

                @Override
                protected void done() {
                    try {
                        int completed = get();
                        status.setText("Updated " + completed + " " + type.pluralLabel() + ".");
                    } catch (Exception e) {
                        showFailure("One or more " + type.pluralLabel() + " could not be updated", e);
                    }
                    refreshInstalled();
                }
            }.execute();
        }

        private void removeSelected() {
            InstalledRow selected = selectedRow();
            if (selected == null) return;
            int choice = JOptionPane.showConfirmDialog(dialog,
                    "Remove " + selected.installed().fileName() + " from this instance?",
                    "Remove " + type.label(), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) return;
            setActionsEnabled(false);
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    client.remove(instance, selected.installed());
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                        status.setText("Removed " + selected.installed().fileName() + ".");
                        refreshInstalled();
                    } catch (Exception e) {
                        showFailure(type.label() + " could not be removed", e);
                        refreshInstalled();
                    }
                }
            }.execute();
        }

        private void setEnabled(InstalledContent content, boolean enabled) {
            setActionsEnabled(false);
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    client.setEnabled(instance, content, enabled);
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                        status.setText((enabled ? "Enabled " : "Disabled ") + content.fileName() + ".");
                        refreshInstalled();
                    } catch (Exception e) {
                        showFailure(type.label() + " could not be toggled", e);
                        refreshInstalled();
                    }
                }
            }.execute();
        }

        private InstalledRow selectedRow() {
            int selected = installedTable.getSelectedRow();
            return selected < 0 ? null : installedModel.rowAt(installedTable.convertRowIndexToModel(selected));
        }

        private void updateActionStates() {
            InstalledRow selected = selectedRow();
            updateSelectedButton.setEnabled(selected != null && selected.update() != null);
            removeButton.setEnabled(selected != null);
        }

        private void setActionsEnabled(boolean enabled) {
            installedTable.setEnabled(enabled);
            checkUpdatesButton.setEnabled(enabled && installedModel.getRowCount() > 0);
            updateSelectedButton.setEnabled(enabled && selectedRow() != null && selectedRow().update() != null);
            updateAllButton.setEnabled(enabled && !updates.isEmpty());
            removeButton.setEnabled(enabled && selectedRow() != null);
        }

        private final class InstalledTableModel extends AbstractTableModel {
            private List<InstalledRow> rows = new ArrayList<>();

            void setRows(List<InstalledRow> rows) {
                this.rows = new ArrayList<>(rows);
                fireTableDataChanged();
            }

            InstalledRow rowAt(int row) {
                return rows.get(row);
            }

            @Override
            public int getRowCount() {
                return rows.size();
            }

            @Override
            public int getColumnCount() {
                return 2;
            }

            @Override
            public String getColumnName(int column) {
                return column == 0 ? "Name" : "Enabled";
            }

            @Override
            public Class<?> getColumnClass(int column) {
                return column == 0 ? String.class : Boolean.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 1;
            }

            @Override
            public Object getValueAt(int row, int column) {
                InstalledRow installedRow = rows.get(row);
                if (column == 1) return installedRow.installed().enabled();
                String name = installedRow.installed().toString();
                return installedRow.update() == null ? name
                        : name + "  →  " + installedRow.update().latestVersionNumber() + " available";
            }

            @Override
            public void setValueAt(Object value, int row, int column) {
                if (column != 1 || !(value instanceof Boolean enabled)) return;
                InstalledRow current = rows.get(row);
                if (current.installed().enabled() == enabled) return;
                rows.set(row, new InstalledRow(current.installed().withEnabled(enabled), current.update()));
                fireTableRowsUpdated(row, row);
                ContentTab.this.setEnabled(current.installed(), enabled);
            }
        }
    }

    private record InstalledRow(InstalledContent installed, ContentUpdate update) {}

    private static final class ProjectRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean selected, boolean focus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
            if (value instanceof Project project) {
                label.setText("<html><b>" + PlayPage.escapeHtml(project.title()) + "</b>  ·  "
                        + String.format("%,d downloads", project.downloads()) + "<br>"
                        + PlayPage.escapeHtml(project.description()) + "</html>");
                label.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
            }
            return label;
        }
    }
}
