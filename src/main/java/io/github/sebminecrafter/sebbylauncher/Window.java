package io.github.sebminecrafter.sebbylauncher;

import io.github.sebminecrafter.sebbylogger.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static io.github.sebminecrafter.sebbylauncher.GuiHelpers.*;

public class Window {
    private static final JFrame frame = new JFrame("Sebby Launcher");
    private static final CardLayout cards = new CardLayout();
    private static final JPanel content = new JPanel(cards);
    private static final JPanel sidebar = new JPanel(new BorderLayout());
    private static final JPanel navigation = new JPanel();
    private static final JPanel sidebarFooter = new JPanel(new BorderLayout());
    private static final JPanel playScreen = new JPanel(new BorderLayout());
    private static final JComboBox<Instance> instanceSelector = new JComboBox<>();
    private static final ButtonGroup nav = new ButtonGroup();
    private static final ImageIcon logoSourceIcon = new ImageIcon(Main.getImageAsset("Sebby64.png"));
    private static JLabel sidebarLogo;

    private static final int minWidth = 480;
    private static final int minHeight = 360;
    private static final Logger logger = new Logger("SebbyLauncher/Window");

    // TODO: Add version, mod loader selectors
    // TODO: Add separate instances
    // TODO: Add separate profiles/accounts
    // TODO: Add mod downloading/updating

    public void gui(boolean fullscreen) {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setIconImage(new ImageIcon(Main.getImageAsset("Sebby32.png")).getImage());
        frame.setUndecorated(false);
        frame.setMinimumSize(new Dimension(minWidth, minHeight));

        navigation.setLayout(new BoxLayout(navigation, BoxLayout.Y_AXIS));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Color.GRAY));
        sidebar.add(createSidebarHeader(), BorderLayout.NORTH);
        sidebar.add(navigation, BorderLayout.CENTER);
        sidebar.add(sidebarFooter, BorderLayout.SOUTH);
        sidebar.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                updateSidebarLogo();
            }
        });

        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(sidebar, BorderLayout.WEST);
        frame.getContentPane().add(content, BorderLayout.CENTER);
        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                updateSidebarWidth();
            }
        });

        refreshInstanceSelector();
        playScreen.add(createPlayControls(), BorderLayout.CENTER);

        addScreen("Play", playScreen);
        addScreen("News", createComingSoonScreen());
        addScreen("Accounts", createComingSoonScreen());
        addScreen("Settings", createComingSoonScreen());

        frame.pack();
        frame.setSize(minWidth, minHeight);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        if (fullscreen) frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
    }

    private static JPanel createPlayControls() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(32, 32, 32, 32));

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(8, 8, 8, 8);
        constraints.fill = GridBagConstraints.HORIZONTAL;

        JLabel instanceLabel = new JLabel("Instance:");
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 0;
        panel.add(instanceLabel, constraints);

        constraints.gridx = 1;
        constraints.weightx = 1;
        panel.add(instanceSelector, constraints);

        JButton newInstanceButton = new JButton("New Instance");
        newInstanceButton.addActionListener(_ -> openInstanceDialog(null));
        constraints.gridx = 2;
        constraints.weightx = 0;
        panel.add(newInstanceButton, constraints);

        JButton editInstanceButton = new JButton("Edit");
        editInstanceButton.addActionListener(_ -> {
            Instance selected = (Instance) instanceSelector.getSelectedItem();
            if (selected != null) {
                openInstanceDialog(selected);
            }
        });
        constraints.gridx = 3;
        panel.add(editInstanceButton, constraints);

        ImageIcon playIcon = new ImageIcon(Main.getImageAsset("play1.png"));
        ImageIcon playIcon2 = new ImageIcon(Main.getImageAsset("play2.png"));
        JButton launch = new JButton("Play", playIcon);
        launch.setPressedIcon(playIcon2);
        launch.addActionListener(Window::onLaunch);
        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.gridwidth = 4;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.NONE;
        constraints.anchor = GridBagConstraints.CENTER;
        panel.add(launch, constraints);
        return panel;
    }

    private static void refreshInstanceSelector() {
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
    }

    private static void openInstanceDialog(Instance existing) {
        JDialog dialog = new JDialog(frame, existing == null ? "New Instance" : "Edit Instance", true);
        dialog.setLayout(new GridBagLayout());
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(8, 8, 8, 8);
        constraints.fill = GridBagConstraints.HORIZONTAL;

        JTextField nameField = new JTextField(existing == null ? "" : existing.name(), 20);
        JTextField versionField = new JTextField(existing == null ? "latest" : existing.version(), 20);
        JTextField loaderField = new JTextField(existing == null ? "Vanilla" : existing.loader(), 20);
        JTextField imageField = new JTextField(existing == null ? "Sebby64.png" : existing.image(), 20);
        JTextField memoryField = new JTextField(existing == null ? "2048" : String.valueOf(existing.memoryMb()), 10);
        JTextField jvmArgsField = new JTextField(existing == null || existing.jvmArgs() == null ? "" : String.join(" ", existing.jvmArgs()), 30);
        JTextField gameFolderField = new JTextField(existing == null ? "" : existing.gameFolder(), 20);

        addInstanceField(dialog, constraints, "Name", nameField, 0);
        addInstanceField(dialog, constraints, "Version", versionField, 1);
        addInstanceField(dialog, constraints, "Loader", loaderField, 2);
        addInstanceField(dialog, constraints, "Image", imageField, 3);
        addInstanceField(dialog, constraints, "Memory (MB)", memoryField, 4);
        addInstanceField(dialog, constraints, "JVM args", jvmArgsField, 5);
        addInstanceField(dialog, constraints, "Game folder", gameFolderField, 6);

        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(_ -> {
            try {
                String name = Instance.sanitizeName(nameField.getText());
                String version = versionField.getText() == null ? "latest" : versionField.getText().trim();
                String loader = loaderField.getText() == null ? "Vanilla" : loaderField.getText().trim();
                String image = imageField.getText() == null || imageField.getText().isBlank() ? "Sebby64.png" : imageField.getText().trim();
                String memoryText = memoryField.getText() == null ? "2048" : memoryField.getText().trim();
                int memoryMb = parseMemory(memoryText);
                String jvmArgs = jvmArgsField.getText() == null ? "" : jvmArgsField.getText().trim();
                List<String> jvmTokens = jvmArgs.isBlank() ? List.of() : Arrays.stream(jvmArgs.split("\\s+"))
                        .filter(arg -> !arg.isBlank())
                        .toList();
                String gameFolder = gameFolderField.getText() == null ? "" : gameFolderField.getText().trim();

                Instance instance = new Instance(name, version, loader, image, memoryMb, jvmTokens, gameFolder);
                instance.save();
                refreshInstanceSelector();
                instanceSelector.setSelectedItem(instance);
                dialog.dispose();
            } catch (Exception ex) {
                logger.error("Failed to save instance: " + ex.getMessage());
            }
        });

        constraints.gridx = 0;
        constraints.gridy = 7;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.NONE;
        constraints.anchor = GridBagConstraints.EAST;
        dialog.add(saveButton, constraints);

        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private static void addInstanceField(Container parent, GridBagConstraints constraints, String labelText,
                                        JTextField field, int rowIndex) {
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

    private static int parseMemory(String memoryText) {
        try {
            int value = Integer.parseInt(memoryText.trim());
            return value > 0 ? value : 2048;
        } catch (NumberFormatException e) {
            return 2048;
        }
    }

    private static JComponent createSidebarHeader() {
        sidebarLogo = new JLabel("Sebby Launcher", SwingConstants.LEFT);
        sidebarLogo.setToolTipText("Made by Sebminecrafter");
        sidebarLogo.setHorizontalTextPosition(SwingConstants.RIGHT);
        sidebarLogo.setIconTextGap(5);
        sidebarLogo.setBorder(BorderFactory.createEmptyBorder(12, 8, 12, 8));
        updateSidebarLogo();
        return sidebarLogo;
    }

    private static void updateSidebarWidth() {
        int width = frame.getContentPane().getWidth();
        if (width <= 0) return;

        int sidebarWidth = Math.clamp(Math.round(width * 0.22f), 150, 280);
        if (sidebar.getPreferredSize().width != sidebarWidth) {
            sidebar.setPreferredSize(new Dimension(sidebarWidth, minHeight));
            sidebar.revalidate();
            updateSidebarLogo();
        }
    }

    private static void updateSidebarLogo() {
        if (sidebarLogo == null || sidebar.getWidth() <= 0) return;

        Insets insets = sidebarLogo.getInsets();
        int availableWidth = sidebar.getWidth() - insets.left - insets.right - 1;
        Font baseFont = new Font("Sans-Serif", Font.PLAIN, 24);
        int fontSize = 24;
        while (fontSize > 10) {
            Font font = baseFont.deriveFont((float) fontSize);
            int iconSize = Math.min(fontSize, 24);
            int requiredWidth = iconSize + sidebarLogo.getIconTextGap()
                    + sidebarLogo.getFontMetrics(font).stringWidth(sidebarLogo.getText());
            if (requiredWidth <= availableWidth) break;
            fontSize--;
        }

        Font font = baseFont.deriveFont((float) fontSize);
        sidebarLogo.setFont(font);
        sidebarLogo.setIcon(scaleIcon(logoSourceIcon, fontSize, fontSize));
    }

    private static JComponent createComingSoonScreen() {
        JPanel screen = new JPanel(new GridBagLayout());
        JLabel label = new JLabel("Coming Soon!");
        label.setFont(label.getFont().deriveFont(Font.BOLD, 24f));
        screen.add(label);
        return screen;
    }

    public void start() {
        logger.debug("Showing window");
        gui(true);
    }

    private static void onLaunch(ActionEvent e) {
        logger.debug("Play button pressed");
        JButton button = (JButton) e.getSource();
        button.setEnabled(false);

        final Instance selectedInstance = (instanceSelector.getSelectedItem() == null)
                ? Instance.defaultInstance()
                : (Instance) instanceSelector.getSelectedItem();

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                if (!Login.attemptLogin()) {
                    logger.error("Login failed.");
                    return null;
                }
                logger.info("Login verified.");
                if (new Launcher().launchGame(selectedInstance)) {
                    logger.info("Game started!");
                } else {
                    logger.error("Failed to launch game!");
                }
                return null;
            }

            @Override
            protected void done() {
                button.setEnabled(true);
                try {
                    get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    logger.error("Launch crashed: " + ex.getCause());
                }
            }
        }.execute();
    }

    /**
     * Adds an item to the main pane on the AWT thread using the given component and constraints
     * @param comp {@link JComponent} to add to the pane
     * @param gbc {@link GridBagConstraints} to use when adding {@code comp}
     * */
    public static void addItem(JComponent comp, GridBagConstraints gbc) {
        SwingUtilities.invokeLater(() -> {
            playScreen.add(comp, gbc);
            playScreen.revalidate();
            playScreen.repaint();
            frame.revalidate();
        });
    }


    private static void addScreen(String name, JComponent screen) {
        content.add(screen, name);
        JToggleButton button = new JToggleButton(name);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        button.setFocusPainted(false);
        button.addActionListener(_ -> cards.show(content, name));
        nav.add(button);
        if (name.equals("Settings")) {
            sidebarFooter.add(button, BorderLayout.SOUTH);
        } else {
            navigation.add(button);
        }
        if (name.equals("Play")) {
            button.setSelected(true);
            cards.show(content, name);
        }
    }
}
