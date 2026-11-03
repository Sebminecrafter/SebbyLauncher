package io.github.sebminecrafter.sebbylauncher.window;

import io.github.sebminecrafter.sebbylauncher.Instance;
import io.github.sebminecrafter.sebbylauncher.mods.ModrinthClient;
import io.github.sebminecrafter.sebbylauncher.mods.ModrinthClient.InstalledMod;
import io.github.sebminecrafter.sebbylauncher.mods.ModrinthClient.ModUpdate;
import io.github.sebminecrafter.sebbylauncher.mods.ModrinthClient.Project;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static io.github.sebminecrafter.sebbylauncher.window.Window.*;

final class ModsDialog {
    private final Instance instance;
    private final ModrinthClient client = new ModrinthClient();
    private final JDialog dialog;
    private final JTextField queryField = new JTextField();
    private final JButton searchButton = new JButton("Search");
    private final DefaultListModel<Project> projects = new DefaultListModel<>();
    private final JList<Project> projectList = new JList<>(projects);
    private final JEditorPane projectDescription = new JEditorPane("text/html", "");
    private final JButton installButton = new JButton("Install selected mod");
    private final DefaultListModel<InstalledRow> installedMods = new DefaultListModel<>();
    private final JList<InstalledRow> installedList = new JList<>(installedMods);
    private final JButton checkUpdatesButton = new JButton("Check for updates");
    private final JButton updateSelectedButton = new JButton("Update selected");
    private final JButton updateAllButton = new JButton("Update all");
    private final JLabel status = new JLabel("Search Modrinth for mods compatible with this instance.");
    private List<ModUpdate> updates = List.of();

    private ModsDialog(Instance instance) {
        this.instance = instance;
        dialog = new JDialog(frame, "Mods — " + instance.name(), true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.getRootPane().setBorder(new EmptyBorder(12, 12, 12, 12));
        dialog.setMinimumSize(new Dimension(620, 420));
        dialog.setSize(760, 560);
        dialog.setLocationRelativeTo(frame);

        JPanel searchBar = new JPanel(new BorderLayout(8, 0));
        searchBar.add(new JLabel("Search"), BorderLayout.WEST);
        searchBar.add(queryField, BorderLayout.CENTER);
        searchBar.add(searchButton, BorderLayout.EAST);
        dialog.add(searchBar, BorderLayout.NORTH);

        projectList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        projectList.setCellRenderer(new ProjectRenderer());
        projectList.addListSelectionListener(_ -> showProjectDetails());
        projectDescription.setEditable(false);
        JSplitPane browseSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(projectList), new JScrollPane(projectDescription));
        browseSplit.setResizeWeight(0.7);
        browseSplit.setBorder(null);
        JPanel browse = new JPanel(new BorderLayout(8, 8));
        browse.add(new JLabel("Minecraft " + instance.version() + " · " + instance.loader()
                + " · results filtered for this instance"), BorderLayout.NORTH);
        browse.add(browseSplit, BorderLayout.CENTER);
        JPanel installRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        installRow.add(installButton);
        browse.add(installRow, BorderLayout.SOUTH);

        installedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel installed = new JPanel(new BorderLayout(8, 8));
        installed.add(new JLabel("Mods installed in " + instance.gameDirectory().resolve("mods")),
                BorderLayout.NORTH);
        installed.add(new JScrollPane(installedList), BorderLayout.CENTER);
        JPanel installedActions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        installedActions.add(checkUpdatesButton);
        installedActions.add(updateSelectedButton);
        installedActions.add(updateAllButton);
        installed.add(installedActions, BorderLayout.SOUTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Browse", browse);
        tabs.addTab("Installed", installed);
        dialog.add(tabs, BorderLayout.CENTER);
        status.setBorder(new EmptyBorder(4, 0, 0, 0));
        dialog.add(status, BorderLayout.SOUTH);

        searchButton.addActionListener(_ -> search());
        queryField.addActionListener(_ -> search());
        installButton.addActionListener(_ -> installSelected());
        checkUpdatesButton.addActionListener(_ -> checkUpdates());
        updateSelectedButton.addActionListener(_ -> updateSelected());
        updateAllButton.addActionListener(_ -> updateAll());
        updateSelectedButton.setEnabled(false);
        updateAllButton.setEnabled(false);
        installButton.setEnabled(false);
    }

    static void open(Instance instance) {
        ModsDialog modsDialog = new ModsDialog(instance);
        modsDialog.refreshInstalled();
        modsDialog.dialog.setVisible(true);
    }

    private void search() {
        String query = queryField.getText().trim();
        searchButton.setEnabled(false);
        status.setText("Searching Modrinth…");
        new SwingWorker<List<Project>, Void>() {
            @Override
            protected List<Project> doInBackground() throws Exception {
                return client.search(query, instance.version(), instance.loader());
            }

            @Override
            protected void done() {
                searchButton.setEnabled(true);
                try {
                    List<Project> found = get();
                    projects.clear();
                    found.forEach(projects::addElement);
                    projectDescription.setText("");
                    status.setText(found.size() + " compatible mod(s) found.");
                } catch (Exception e) {
                    showFailure("Mod search failed", e);
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
        new SwingWorker<InstalledMod, Void>() {
            @Override
            protected InstalledMod doInBackground() throws Exception {
                return client.install(instance, selected);
            }

            @Override
            protected void done() {
                installButton.setEnabled(projectList.getSelectedValue() != null);
                try {
                    InstalledMod installed = get();
                    status.setText("Installed " + installed.title() + " as " + installed.fileName() + ".");
                    refreshInstalled();
                } catch (Exception e) {
                    showFailure("Mod could not be installed", e);
                }
            }
        }.execute();
    }

    private void refreshInstalled() {
        checkUpdatesButton.setEnabled(false);
        updateSelectedButton.setEnabled(false);
        updateAllButton.setEnabled(false);
        new SwingWorker<List<InstalledMod>, Void>() {
            @Override
            protected List<InstalledMod> doInBackground() throws Exception {
                return client.listInstalled(instance);
            }

            @Override
            protected void done() {
                try {
                    List<InstalledMod> found = get();
                    installedMods.clear();
                    for (InstalledMod mod : found) installedMods.addElement(new InstalledRow(mod, null));
                    updates = List.of();
                    checkUpdatesButton.setEnabled(!found.isEmpty());
                    status.setText(found.size() + " tracked mod(s) installed.");
                } catch (Exception e) {
                    showFailure("Installed mods could not be loaded", e);
                }
            }
        }.execute();
    }

    private void checkUpdates() {
        checkUpdatesButton.setEnabled(false);
        status.setText("Checking Modrinth for compatible updates…");
        new SwingWorker<List<ModUpdate>, Void>() {
            @Override
            protected List<ModUpdate> doInBackground() throws Exception {
                return client.checkForUpdates(instance);
            }

            @Override
            protected void done() {
                checkUpdatesButton.setEnabled(true);
                try {
                    updates = get();
                    for (int i = 0; i < installedMods.size(); i++) {
                        InstalledMod mod = installedMods.get(i).installed();
                        ModUpdate update = updates.stream()
                                .filter(candidate -> candidate.installed().projectId().equals(mod.projectId()))
                                .findFirst().orElse(null);
                        installedMods.set(i, new InstalledRow(mod, update));
                    }
                    updateSelectedButton.setEnabled(!updates.isEmpty());
                    updateAllButton.setEnabled(!updates.isEmpty());
                    status.setText(updates.isEmpty()
                            ? "All tracked mods are up to date."
                            : updates.size() + " mod update(s) available.");
                } catch (Exception e) {
                    showFailure("Mod updates could not be checked", e);
                }
            }
        }.execute();
    }

    private void updateSelected() {
        InstalledRow selected = installedList.getSelectedValue();
        if (selected == null || selected.update() == null) {
            status.setText("Select a mod with an available update.");
            return;
        }
        runUpdate(List.of(selected.update()));
    }

    private void updateAll() {
        if (updates.isEmpty()) return;
        runUpdate(new ArrayList<>(updates));
    }

    private void runUpdate(List<ModUpdate> pending) {
        setUpdateButtonsDisabled();
        status.setText("Updating " + pending.size() + " mod(s)…");
        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws Exception {
                int completed = 0;
                for (ModUpdate update : pending) {
                    client.update(instance, update);
                    completed++;
                }
                return completed;
            }

            @Override
            protected void done() {
                try {
                    int completed = get();
                    status.setText("Updated " + completed + " mod(s).");
                    refreshInstalled();
                } catch (Exception e) {
                    showFailure("One or more mods could not be updated", e);
                    refreshInstalled();
                }
            }
        }.execute();
    }

    private void setUpdateButtonsDisabled() {
        checkUpdatesButton.setEnabled(false);
        updateSelectedButton.setEnabled(false);
        updateAllButton.setEnabled(false);
    }

    private void showFailure(String title, Exception error) {
        Throwable cause = error instanceof ExecutionException && error.getCause() != null
                ? error.getCause() : error;
        String message = cause.getMessage() == null ? cause.toString() : cause.getMessage();
        logger.error(title + ": " + message);
        status.setText(title + ".");
        JOptionPane.showMessageDialog(dialog, message, title, JOptionPane.ERROR_MESSAGE);
    }

    private record InstalledRow(InstalledMod installed, ModUpdate update) {
        @Override
        public String toString() {
            if (update == null) return installed.toString();
            return installed + "  →  " + update.latestVersionNumber() + " available";
        }
    }

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
