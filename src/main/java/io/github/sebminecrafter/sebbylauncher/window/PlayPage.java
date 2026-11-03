package io.github.sebminecrafter.sebbylauncher.window;

import io.github.sebminecrafter.sebbylauncher.Instance;
import io.github.sebminecrafter.sebbylauncher.Login;
import io.github.sebminecrafter.sebbylauncher.FileUtils;
import io.github.sebminecrafter.sebbylauncher.Main;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static io.github.sebminecrafter.sebbylauncher.GuiHelpers.*;
import static io.github.sebminecrafter.sebbylauncher.window.Window.*;

public final class PlayPage {
    private PlayPage() {}

    static JPanel create() {
        JPanel screen = new JPanel(new BorderLayout(12, 12));
        screen.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel library = new JPanel(new BorderLayout(8, 12));
        JPanel header = new JPanel(new BorderLayout());
        JLabel title = new JLabel("Instances");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        header.add(title, BorderLayout.WEST);
        JButton newInstanceButton = new JButton("Add instance");
        newInstanceButton.addActionListener(_ -> openInstanceDialog(null));
        header.add(newInstanceButton, BorderLayout.EAST);
        library.add(header, BorderLayout.NORTH);

        instanceGrid.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        library.add(new JScrollPane(instanceGrid), BorderLayout.CENTER);

        instanceOptions.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        instanceOptions.setPreferredSize(new Dimension(250, 300));
        refreshInstanceOptions();

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, library, instanceOptions);
        split.setResizeWeight(0.7);
        split.setBorder(null);
        screen.add(split, BorderLayout.CENTER);
        accountSelector.addActionListener(_ -> {
            Login.Account selected = (Login.Account) accountSelector.getSelectedItem();
            if (selected != null) {
                Login.setSelectedAccount(selected.id());
            }
        });
        JPanel accountRow = new JPanel(new BorderLayout(8, 0));
        accountRow.add(new JLabel("Account"), BorderLayout.WEST);
        accountRow.add(accountSelector, BorderLayout.CENTER);
        JButton manageAccounts = new JButton("Manage accounts");
        manageAccounts.addActionListener(_ -> cards.show(content, "Accounts"));
        accountRow.add(manageAccounts, BorderLayout.EAST);
        screen.add(accountRow, BorderLayout.SOUTH);
        refreshInstanceGrid();
        return screen;
    }

    private static void refreshInstanceGrid() {
        instanceGrid.removeAll();
        for (int i = 0; i < instanceSelector.getItemCount(); i++) {
            Instance instance = instanceSelector.getItemAt(i);
            JButton tile = new JButton("<html><center>" + escapeHtml(instance.name()) + "<br>"
                    + escapeHtml(instance.version()) + " · " + escapeHtml(loaderDescription(instance))
                    + "</center></html>",
                    instanceIcon(instance, 72));
            tile.setHorizontalTextPosition(SwingConstants.CENTER);
            tile.setVerticalTextPosition(SwingConstants.BOTTOM);
            tile.setPreferredSize(new Dimension(150, 140));
            tile.setFocusPainted(false);
            Color accent = UIManager.getColor("Component.focusColor");
            if (accent == null) accent = new Color(79, 109, 138);
            tile.setBorder(instance.equals(instanceSelector.getSelectedItem())
                    ? BorderFactory.createLineBorder(accent, 2)
                    : BorderFactory.createEtchedBorder());
            tile.addActionListener(_ -> {
                instanceSelector.setSelectedItem(instance);
                refreshInstanceGrid();
                refreshInstanceOptions();
            });
            tile.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    if (event.getClickCount() == 2) {
                        instanceSelector.setSelectedItem(instance);
                        onLaunch(null);
                    }
                }
            });
            instanceGrid.add(tile);
        }
        instanceGrid.revalidate();
        instanceGrid.repaint();
    }

    private static void refreshInstanceOptions() {
        instanceOptions.removeAll();
        Instance instance = (Instance) instanceSelector.getSelectedItem();
        if (instance == null) {
            launchButton = null;
            instanceOptions.add(new JLabel("Create an instance to get started."), BorderLayout.CENTER);
            instanceOptions.revalidate();
            instanceOptions.repaint();
            return;
        }

        JPanel details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        JLabel image = new JLabel(instanceIcon(instance, 128));
        image.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel name = new JLabel(instance.name());
        name.setFont(name.getFont().deriveFont(Font.BOLD, 20f));
        name.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel version = new JLabel(instance.version() + " · " + loaderDescription(instance));
        version.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel memory = new JLabel(instance.memoryMb() + " MB memory");
        memory.setAlignmentX(Component.CENTER_ALIGNMENT);
        details.add(image);
        details.add(Box.createVerticalStrut(8));
        details.add(name);
        details.add(version);
        details.add(memory);
        instanceOptions.add(details, BorderLayout.CENTER);

        JPanel actions = new JPanel(new GridLayout(0, 1, 0, 8));
        JButton edit = new JButton("Instance settings");
        edit.addActionListener(_ -> openInstanceDialog(instance));
        actions.add(edit);
        JButton manageMods = new JButton("Manage mods & packs");
        manageMods.addActionListener(_ -> ModsDialog.open(instance));
        actions.add(manageMods);
        JButton delete = new JButton("Delete instance");
        delete.addActionListener(_ -> deleteInstance(instance));
        actions.add(delete);
        launchButton = new JButton("Play", new ImageIcon(Main.getImageAsset("play1.png")));
        launchButton.setPressedIcon(new ImageIcon(Main.getImageAsset("play2.png")));
        launchButton.addActionListener(Window::onLaunch);
        actions.add(launchButton);
        actions.add(new JLabel("Double-click an instance to launch it."));
        instanceOptions.add(actions, BorderLayout.SOUTH);
        instanceOptions.revalidate();
        instanceOptions.repaint();
    }

    private static ImageIcon instanceIcon(Instance instance, int size) {
        String image = instance.image();
        if (image != null && !image.isBlank()) {
            try {
                Path path = Path.of(image);
                if (Files.isRegularFile(path)) {
                    return scaleIcon(new ImageIcon(path.toString()), size, size);
                }
            } catch (RuntimeException ignored) {
                logger.error("Invalid image path for instance " + instance.name());
            }
            return scaleIcon(new ImageIcon(Main.getImageAsset(image)), size, size);
        }
        return scaleIcon(new ImageIcon(Main.getImageAsset("logo/Sebby64.png")), size, size);
    }

    static String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String loaderDescription(Instance instance) {
        return instance.loaderVersion() == null || instance.loaderVersion().isBlank()
                ? instance.loader()
                : instance.loader() + " " + instance.loaderVersion();
    }

    static void refreshInstanceSelector() {
        Instance selected = (Instance) instanceSelector.getSelectedItem();
        instanceSelector.removeAllItems();
        List<Instance> instances = Instance.loadAll();
        for (Instance instance : instances) {
            instanceSelector.addItem(instance);
        }

        if (selected != null && instances.contains(selected)) {
            instanceSelector.setSelectedItem(selected);
        } else if (instanceSelector.getItemCount() > 0) {
            instanceSelector.setSelectedIndex(0);
        }
        refreshInstanceGrid();
        refreshInstanceOptions();
    }

    static void refreshAccountSelector() {
        Login.Account selected = (Login.Account) accountSelector.getSelectedItem();
        accountSelector.removeAllItems();
        List<Login.Account> accounts = Login.getAccounts();
        for (Login.Account account : accounts) {
            accountSelector.addItem(account);
        }

        Login.Account current = Login.getSelectedAccount();
        if (current != null) {
            accountSelector.setSelectedItem(current);
        } else if (accountSelector.getItemCount() > 0) {
            accountSelector.setSelectedIndex(0);
        }

        if (selected != null && selected.id() != null && Login.getSelectedAccount() != null
                && selected.id().equals(Login.getSelectedAccount().id())) {
            accountSelector.setSelectedItem(selected);
        }
    }

    private static void openInstanceDialog(Instance existing) {
        JDialog dialog = new JDialog(frame, existing == null ? "New Instance" : "Edit Instance", true);
        dialog.setLayout(new GridBagLayout());
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(8, 8, 8, 8);
        constraints.fill = GridBagConstraints.HORIZONTAL;

        JTextField nameField = new JTextField(existing == null ? "" : existing.name(), 20);
        JComboBox<String> versionField = new JComboBox<>(new String[] {"latest"});
        versionField.setEditable(true);
        versionField.setSelectedItem(existing == null ? "latest" : existing.version());
        versionField.setToolTipText("Choose a version or type a custom version ID.");
        JComboBox<String> loaderField = new JComboBox<>(new String[] {"Vanilla", "Fabric", "Quilt"});
        loaderField.setSelectedItem(existing == null ? "Vanilla" : existing.loader());
        loaderField.setToolTipText("Choose Vanilla, Fabric, or Quilt.");
        JComboBox<String> loaderVersionField = new JComboBox<>(new String[] {"Latest"});
        String existingLoaderVersion = existing == null ? null : existing.loaderVersion();
        if (existingLoaderVersion != null && !existingLoaderVersion.isBlank()) {
            loaderVersionField.addItem(existingLoaderVersion);
            loaderVersionField.setSelectedItem(existingLoaderVersion);
        }
        loaderVersionField.setToolTipText("Choose the mod-loader version, or Latest.");
        loaderVersionField.setEnabled(!"Vanilla".equalsIgnoreCase(comboText(loaderField, "Vanilla")));
        JTextField imageField = new JTextField(existing == null ? "logo/Sebby64.png" : existing.image(), 20);
        JTextField memoryField = new JTextField(existing == null ? "2048" : String.valueOf(existing.memoryMb()), 10);
        JTextField jvmArgsField = new JTextField(
                existing == null || existing.jvmArgs() == null ? "" : String.join(" ", existing.jvmArgs()), 30);
        JTextField gameFolderField = new JTextField(existing == null ? "" : existing.gameFolder(), 20);

        new SwingWorker<String[], Void>() {
            @Override
            protected String[] doInBackground() {
                return new io.github.sebminecrafter.sebbylauncher.downloader.Downloader().listVersions();
            }

            @Override
            protected void done() {
                try {
                    Object selected = versionField.getEditor().getItem();
                    for (String version : get()) {
                        if (((javax.swing.DefaultComboBoxModel<String>) versionField.getModel())
                                .getIndexOf(version) < 0) {
                            versionField.addItem(version);
                        }
                    }
                    versionField.setSelectedItem(selected);
                } catch (Exception e) {
                    logger.error("Failed to load version suggestions: " + e.getMessage());
                }
            }
        }.execute();

        Runnable refreshLoaderVersions = () -> {
            String selectedLoader = comboText(loaderField, "Vanilla");
            String selectedGameVersion = comboText(versionField, "latest");
            loaderVersionField.setEnabled(!"Vanilla".equalsIgnoreCase(selectedLoader));
            if ("Vanilla".equalsIgnoreCase(selectedLoader)) {
                loaderVersionField.removeAllItems();
                loaderVersionField.addItem("Latest");
                return;
            }

            Object selectedItem = loaderVersionField.getSelectedItem();
            String previousSelection = selectedItem == null ? "Latest" : selectedItem.toString();
            loaderVersionField.removeAllItems();
            loaderVersionField.addItem("Latest");
            new SwingWorker<List<String>, Void>() {
                @Override
                protected List<String> doInBackground() throws Exception {
                    return new io.github.sebminecrafter.sebbylauncher.downloader.Downloader()
                            .listLoaderVersions(selectedGameVersion, selectedLoader);
                }

                @Override
                protected void done() {
                    if (!selectedLoader.equals(comboText(loaderField, "Vanilla"))
                            || !selectedGameVersion.equals(comboText(versionField, "latest"))) return;
                    try {
                        List<String> versions = get();
                        loaderVersionField.removeAllItems();
                        loaderVersionField.addItem("Latest");
                        for (String loaderVersion : versions) {
                            loaderVersionField.addItem(loaderVersion);
                        }
                        if (((DefaultComboBoxModel<String>) loaderVersionField.getModel())
                                .getIndexOf(previousSelection) >= 0) {
                            loaderVersionField.setSelectedItem(previousSelection);
                        } else {
                            loaderVersionField.setSelectedItem("Latest");
                        }
                    } catch (Exception e) {
                        logger.error("Failed to load loader versions: " + e.getMessage());
                        loaderVersionField.setToolTipText(
                                "Could not load versions. Choose Latest or try again later.");
                    }
                }
            }.execute();
        };
        loaderField.addActionListener(_ -> refreshLoaderVersions.run());
        Timer versionRefreshDebounce = new Timer(350, _ -> refreshLoaderVersions.run());
        versionRefreshDebounce.setRepeats(false);
        versionField.addActionListener(_ -> versionRefreshDebounce.restart());
        if (!"Vanilla".equalsIgnoreCase(comboText(loaderField, "Vanilla"))) {
            refreshLoaderVersions.run();
        }

        addInstanceField(dialog, constraints, "Name", nameField, 0);
        addInstanceField(dialog, constraints, "Version", versionField, 1);
        addInstanceField(dialog, constraints, "Loader", loaderField, 2);
        addInstanceField(dialog, constraints, "Loader version", loaderVersionField, 3);
        addInstanceField(dialog, constraints, "Image", imageField, 4);
        addInstanceField(dialog, constraints, "Memory (MB)", memoryField, 5);
        addInstanceField(dialog, constraints, "JVM args", jvmArgsField, 6);
        addInstanceField(dialog, constraints, "Game folder", gameFolderField, 7);

        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(_ -> {
            try {
                String name = Instance.sanitizeName(nameField.getText());
                String version = comboText(versionField, "latest");
                String loader = comboText(loaderField, "Vanilla");
                String loaderVersion = comboText(loaderVersionField, "Latest");
                if ("Vanilla".equalsIgnoreCase(loader) || "Latest".equalsIgnoreCase(loaderVersion)) {
                    loaderVersion = null;
                }
                String image = imageField.getText() == null || imageField.getText().isBlank() ? "logo/Sebby64.png"
                        : imageField.getText().trim();
                String memoryText = memoryField.getText() == null ? "2048" : memoryField.getText().trim();
                int memoryMb = parseMemory(memoryText);
                String jvmArgs = jvmArgsField.getText() == null ? "" : jvmArgsField.getText().trim();
                List<String> jvmTokens = jvmArgs.isBlank() ? List.of()
                        : Arrays.stream(jvmArgs.split("\\s+"))
                                .filter(arg -> !arg.isBlank())
                                .toList();
                String gameFolder = gameFolderField.getText() == null ? "" : gameFolderField.getText().trim();

                Instance instance = new Instance(
                        name, version, loader, image, memoryMb, jvmTokens, gameFolder, loaderVersion);
                instance.save();
                refreshInstanceSelector();
                instanceSelector.setSelectedItem(instance);
                dialog.dispose();
            } catch (Exception ex) {
                logger.error("Failed to save instance: " + ex.getMessage());
            }
        });

        constraints.gridx = 0;
        constraints.gridy = 8;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.NONE;
        constraints.anchor = GridBagConstraints.EAST;
        dialog.add(saveButton, constraints);

        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private static void addInstanceField(Container parent, GridBagConstraints constraints, String labelText,
                                         JComponent field, int rowIndex) {
        constraints.gridx = 0;
        constraints.gridy = rowIndex;
        constraints.gridwidth = 1;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.anchor = GridBagConstraints.EAST;
        parent.add(new JLabel(labelText + ":"), constraints);

        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.WEST;
        parent.add(field, constraints);
    }

    private static String comboText(JComboBox<String> combo, String fallback) {
        Object value = combo.isEditable() ? combo.getEditor().getItem() : combo.getSelectedItem();
        return value == null || value.toString().isBlank() ? fallback : value.toString().trim();
    }

    private static void deleteInstance(Instance instance) {
        Path instanceDirectory;
        Path gameDirectory;
        try {
            instanceDirectory = instance.instanceDirectory().toAbsolutePath().normalize();
            gameDirectory = instance.gameDirectory().toAbsolutePath().normalize();
            FileUtils.validateDeleteTarget(instanceDirectory, instanceDirectory);
            FileUtils.validateDeleteTarget(gameDirectory, instanceDirectory);
        } catch (IOException | InvalidPathException e) {
            logger.error("Refusing to delete instance: " + e.getMessage());
            JOptionPane.showMessageDialog(frame, e.getMessage(), "Instance could not be deleted",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        String confirmation = "Delete instance \"" + instance.name() + "\" and its files?\n\n"
                + "Instance folder: " + instanceDirectory + "\n"
                + "Game folder: " + gameDirectory;
        if (JOptionPane.showConfirmDialog(frame, confirmation, "Confirm instance deletion",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                if (!gameDirectory.equals(instanceDirectory) && !gameDirectory.startsWith(instanceDirectory)) {
                    FileUtils.deleteTree(gameDirectory, instanceDirectory);
                }
                FileUtils.deleteTree(instanceDirectory, instanceDirectory);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    refreshInstanceSelector();
                } catch (Exception e) {
                    logger.error("Failed to delete instance: " + e.getMessage());
                    JOptionPane.showMessageDialog(frame, e.getMessage(), "Instance could not be deleted",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private static int parseMemory(String memoryText) {
        try {
            int value = Integer.parseInt(memoryText.trim());
            return value > 0 ? value : 2048;
        } catch (NumberFormatException e) {
            return 2048;
        }
    }

}
