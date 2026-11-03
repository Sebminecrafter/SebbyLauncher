package io.github.sebminecrafter.sebbylauncher.window;

import io.github.sebminecrafter.sebbylauncher.Instance;
import io.github.sebminecrafter.sebbylauncher.FileUtils;
import io.github.sebminecrafter.sebbylauncher.LauncherDirs;
import io.github.sebminecrafter.sebbylauncher.LauncherPreferences;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static io.github.sebminecrafter.sebbylauncher.window.Window.*;

public final class SettingsPage {
    private SettingsPage() {}

    static JComponent create() {
        JPanel screen = new JPanel(new BorderLayout(8, 8));
        screen.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        JLabel title = new JLabel("Settings");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        screen.add(title, BorderLayout.NORTH);

        JPanel sections = new JPanel();
        sections.setLayout(new BoxLayout(sections, BoxLayout.Y_AXIS));
        sections.add(createAppearanceSettings());
        sections.add(createResourceSettings());
        sections.add(createImportExportSettings());
        sections.add(createMaintenanceSettings());
        screen.add(new JScrollPane(sections), BorderLayout.CENTER);
        settingsStatus = new JLabel(" ");
        screen.add(settingsStatus, BorderLayout.SOUTH);
        return screen;
    }

    private static JComponent createAppearanceSettings() {
        JPanel section = new JPanel(new GridBagLayout());
        section.setBorder(BorderFactory.createTitledBorder("Appearance"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 6, 5, 6);
        c.anchor = GridBagConstraints.WEST;

        JComboBox<String> themes = new JComboBox<>();
        themes.addItem("System");
        for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
            themes.addItem(info.getName());
        }
        String currentTheme = preferences.lookAndFeel();
        if (currentTheme != null && !currentTheme.equals("System")) {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if (info.getClassName().equals(currentTheme) || info.getName().equals(currentTheme)) {
                    themes.setSelectedItem(info.getName());
                    break;
                }
            }
        }
        c.gridx = 0;
        c.gridy = 0;
        section.add(new JLabel("Theme"), c);
        c.gridx = 1;
        section.add(themes, c);
        themes.addActionListener(_ -> {
            String choice = (String) themes.getSelectedItem();
            String className = "System";
            if (choice != null) {
                for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                    if (info.getName().equals(choice)) className = info.getClassName();
                }
            }
            preferences = new LauncherPreferences(className, preferences.accentColor());
            persistPreferences();
        });

        JButton color = new JButton("Choose accent color");
        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 2;
        section.add(color, c);
        color.addActionListener(_ -> {
            Color initial;
            try {
                initial = Color.decode(preferences.accentColor());
            } catch (NumberFormatException e) {
                initial = new Color(79, 109, 138);
            }
            Color selected = JColorChooser.showDialog(frame, "Choose accent color", initial);
            if (selected != null) {
                preferences = new LauncherPreferences(preferences.lookAndFeel(),
                        String.format("#%02X%02X%02X", selected.getRed(), selected.getGreen(), selected.getBlue()));
                persistPreferences();
            }
        });
        return section;
    }

    private static JComponent createResourceSettings() {
        JPanel section = new JPanel(new GridBagLayout());
        section.setBorder(BorderFactory.createTitledBorder("Saved resources"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;
        JCheckBox versions = new JCheckBox("Minecraft versions and client JARs");
        JCheckBox libraries = new JCheckBox("Libraries");
        JCheckBox assets = new JCheckBox("Assets");
        JCheckBox runtimes = new JCheckBox("Downloaded Java runtimes");
        c.gridx = 0;
        c.gridy = 0;
        section.add(versions, c);
        c.gridy++;
        section.add(libraries, c);
        c.gridy++;
        section.add(assets, c);
        c.gridy++;
        section.add(runtimes, c);
        JButton clear = new JButton("Delete selected resources…");
        c.gridy++;
        section.add(clear, c);
        clear.addActionListener(_ -> {
            Map<JCheckBox, Path> resources = Map.of(
                    versions, LauncherDirs.VERSIONS,
                    libraries, LauncherDirs.LIBRARIES,
                    assets, LauncherDirs.ASSETS,
                    runtimes, LauncherDirs.RUNTIMES);
            List<Path> selected = resources.entrySet().stream()
                    .filter(entry -> entry.getKey().isSelected()).map(Map.Entry::getValue).toList();
            if (selected.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Select at least one resource category.",
                        "Nothing selected", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            int answer = JOptionPane.showConfirmDialog(frame,
                    "Delete the selected cached resources? They will be downloaded again when needed.",
                    "Confirm resource cleanup", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) return;
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    for (Path path : selected) FileUtils.deleteTree(path);
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                        selected.forEach(path -> resources.entrySet().stream()
                                .filter(entry -> entry.getValue().equals(path))
                                .forEach(entry -> entry.getKey().setSelected(false)));
                        settingsStatus.setText("Selected cached resources were deleted.");
                    } catch (Exception e) {
                        logger.error("Resource cleanup failed: " + e.getMessage());
                        JOptionPane.showMessageDialog(frame, e.getMessage(), "Resource cleanup failed",
                                JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        });
        return section;
    }

    private static JComponent createImportExportSettings() {
        JPanel section = new JPanel(new FlowLayout(FlowLayout.LEFT));
        section.setBorder(BorderFactory.createTitledBorder("Launcher settings"));
        JButton loadVersion = new JButton("Load custom version JSON…");
        loadVersion.addActionListener(_ -> chooseCustomVersion());
        JButton export = new JButton("Export settings…");
        export.addActionListener(_ -> exportSettings());
        JButton importButton = new JButton("Import settings…");
        importButton.addActionListener(_ -> importSettings());
        section.add(loadVersion);
        section.add(export);
        section.add(importButton);
        return section;
    }

    private static JComponent createMaintenanceSettings() {
        JPanel section = new JPanel(new FlowLayout(FlowLayout.LEFT));
        section.setBorder(BorderFactory.createTitledBorder("Maintenance"));
        JButton updates = new JButton("Check all instances for updates");
        updates.addActionListener(_ -> checkInstanceUpdates(updates));
        section.add(updates);
        section.add(new JLabel("Account tokens are stored separately and are never included in exports."));
        return section;
    }

    private static void persistPreferences() {
        try {
            preferences.save();
            applyPreferences();
            settingsStatus.setText("Settings saved.");
        } catch (IOException e) {
            logger.error("Failed to save launcher settings: " + e.getMessage());
            JOptionPane.showMessageDialog(frame, e.getMessage(), "Could not save settings",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void chooseCustomVersion() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select a Minecraft version JSON");
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        Path source = chooser.getSelectedFile().toPath();
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                JsonObject json;
                try (var reader = Files.newBufferedReader(source)) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (!parsed.isJsonObject()) throw new IOException("Version JSON must contain an object.");
                    json = parsed.getAsJsonObject();
                }
                String id = json.has("id") ? json.get("id").getAsString() : "";
                if (!id.matches("[A-Za-z0-9._-]{1,64}") || id.equals(".") || id.equals("..")) {
                    throw new IOException("Version JSON has an invalid or missing id.");
                }
                JsonObject downloads = json.has("downloads") && json.get("downloads").isJsonObject()
                        ? json.getAsJsonObject("downloads") : null;
                if (downloads == null || !downloads.has("client")
                        || !downloads.get("client").isJsonObject()
                        || !downloads.getAsJsonObject("client").has("url")) {
                    throw new IOException("Version JSON must include a client download URL.");
                }
                Path destination = LauncherDirs.VERSIONS.resolve(id).resolve(id + ".json");
                if (!destination.normalize().startsWith(LauncherDirs.VERSIONS.normalize())) {
                    throw new IOException("Version JSON id points outside the versions directory.");
                }
                Files.createDirectories(destination.getParent());
                Files.copy(source, destination);
                return id;
            }

            @Override
            protected void done() {
                try {
                    String id = get();
                    settingsStatus.setText("Loaded custom version " + id + ".");
                    JOptionPane.showMessageDialog(frame, "Custom version " + id + " is ready to select.",
                            "Version loaded", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    logger.error("Failed to load custom version JSON: " + e.getMessage());
                    JOptionPane.showMessageDialog(frame, e.getMessage(), "Could not load version JSON",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private static void exportSettings() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export launcher settings");
        chooser.setSelectedFile(new java.io.File("sebbylauncher-settings.json"));
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        SettingsExport export = new SettingsExport(preferences, Instance.loadAll());
        try {
            Files.writeString(chooser.getSelectedFile().toPath(), GSON.toJson(export), StandardCharsets.UTF_8);
            settingsStatus.setText("Settings and instance configurations exported.");
        } catch (IOException e) {
            logger.error("Failed to export launcher settings: " + e.getMessage());
            JOptionPane.showMessageDialog(frame, e.getMessage(), "Export failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void importSettings() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import launcher settings");
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        try (var reader = Files.newBufferedReader(chooser.getSelectedFile().toPath())) {
            SettingsExport imported = GSON.fromJson(reader, SettingsExport.class);
            if (imported == null || imported.preferences() == null || imported.instances() == null) {
                throw new IOException("Export file is missing settings or instances.");
            }
            if (imported.preferences().lookAndFeel() == null || imported.preferences().lookAndFeel().isBlank()
                    || imported.preferences().accentColor() == null
                    || !imported.preferences().accentColor().matches("#[0-9A-Fa-f]{6}")) {
                throw new IOException("Export file contains invalid appearance settings.");
            }
            for (Instance instance : imported.instances()) {
                if (instance == null || instance.name() == null || instance.name().isBlank()) {
                    throw new IOException("Export file contains an instance with no name.");
                }
            }
            preferences = imported.preferences();
            preferences.save();
            for (Instance instance : imported.instances()) instance.save();
            applyPreferences();
            PlayPage.refreshInstanceSelector();
            settingsStatus.setText("Settings and instance configurations imported.");
        } catch (IOException | RuntimeException e) {
            logger.error("Failed to import launcher settings: " + e.getMessage());
            JOptionPane.showMessageDialog(frame, e.getMessage(), "Import failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void checkInstanceUpdates(JButton button) {
        button.setEnabled(false);
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                io.github.sebminecrafter.sebbylauncher.downloader.Downloader downloader =
                        new io.github.sebminecrafter.sebbylauncher.downloader.Downloader();
                String latest = downloader.getLatestVersion(false);
                if (latest == null) throw new IOException("Could not retrieve the latest Minecraft release.");
                StringBuilder result = new StringBuilder("Latest Minecraft release: ").append(latest).append("\n\n");
                for (Instance instance : Instance.loadAll()) {
                    String version = instance.version();
                    if (version == null || version.isBlank() || "latest".equalsIgnoreCase(version)) {
                        result.append(instance.name()).append(": tracks the latest release (").append(latest).append(")\n");
                    } else {
                        try {
                            if (downloader.getVersionMeta(version, instance.loader(), instance.loaderVersion()) == null) {
                                result.append(instance.name()).append(": version ").append(version)
                                        .append(" is unavailable; consider ").append(latest).append("\n");
                            } else {
                                result.append(instance.name()).append(": version ").append(version)
                                        .append(" is available");
                                if (!version.equals(latest)) result.append("; latest release is ").append(latest);
                                result.append("\n");
                            }
                        } catch (IOException e) {
                            result.append(instance.name()).append(": could not check ").append(version)
                                    .append(" (").append(e.getMessage()).append(")\n");
                        }
                    }
                }
                return result.toString();
            }

            @Override
            protected void done() {
                button.setEnabled(true);
                try {
                    JOptionPane.showMessageDialog(frame, get(), "Instance version check",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    logger.error("Instance update check failed: " + e.getMessage());
                    JOptionPane.showMessageDialog(frame, e.getMessage(), "Update check failed",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private record SettingsExport(LauncherPreferences preferences, List<Instance> instances) {}
}
