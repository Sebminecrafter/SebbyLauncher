package io.github.sebminecrafter.sebbylauncher.window;

import io.github.sebminecrafter.sebbylauncher.*;
import io.github.sebminecrafter.sebbylogger.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.net.http.HttpClient;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import static io.github.sebminecrafter.sebbylauncher.GuiHelpers.*;

public class Window {
    static final JFrame frame = new JFrame("Sebby Launcher");
    static final CardLayout cards = new CardLayout();
    static final JPanel content = new JPanel(cards);
    static final JPanel sidebar = new JPanel(new BorderLayout());
    static final JPanel navigation = new JPanel();
    static final JPanel sidebarFooter = new JPanel(new BorderLayout());
    static final JPanel playScreen = new JPanel(new BorderLayout());
    static final JComboBox<Instance> instanceSelector = new JComboBox<>();
    static final JComboBox<Login.Account> accountSelector = new JComboBox<>();
    static final JPanel instanceGrid = new JPanel(new GridLayout(0, 2, 12, 12));
    static final JPanel instanceOptions = new JPanel(new BorderLayout(8, 8));
    static final DefaultListModel<Login.Account> accountListModel = new DefaultListModel<>();
    static final JList<Login.Account> accountList = new JList<>(accountListModel);
    static final Map<String, ImageIcon> accountHeads = new ConcurrentHashMap<>();
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(java.time.Duration.ofSeconds(15))
            .build();
    static final ButtonGroup nav = new ButtonGroup();
    static final ImageIcon logoSourceIcon = new ImageIcon(Main.getImageAsset("logo/Sebby64.png"));
    static JLabel sidebarLogo;
    static LauncherPreferences preferences = LauncherPreferences.defaults();
    static JLabel newsStatus;
    static String currentNewsUrl = "https://www.minecraft.net/article";
    static JLabel accountNameLabel;
    static JLabel accountTypeLabel;
    static JLabel skinImageLabel;
    static JPanel capeList;
    static JLabel settingsStatus;
    static JButton launchButton;
    static boolean refreshingAccounts;

    static final int minWidth = 480;
    static final int minHeight = 360;
    static final Logger logger = new Logger("SebbyLauncher/Window");

    public void gui(boolean fullscreen) {
        try {
            preferences = LauncherPreferences.load();
        } catch (IOException e) {
            logger.error("Failed to load launcher settings: " + e.getMessage());
            JOptionPane.showMessageDialog(null, e.getMessage(), "Settings could not be loaded",
                    JOptionPane.ERROR_MESSAGE);
        }
        applyPreferences();

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setIconImage(new ImageIcon(Main.getImageAsset("logo/Sebby32.png")).getImage());
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

        PlayPage.refreshInstanceSelector();
        PlayPage.refreshAccountSelector();
        playScreen.add(PlayPage.create(), BorderLayout.CENTER);

        addScreen("Play", playScreen);
        addScreen("News", NewsPage.create());
        addScreen("Accounts", AccountsPage.create());
        addScreen("Settings", SettingsPage.create());

        frame.pack();
        frame.setSize(minWidth, minHeight);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        if (fullscreen)
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
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
        if (width <= 0)
            return;

        int sidebarWidth = Math.clamp(Math.round(width * 0.22f), 150, 280);
        if (sidebar.getPreferredSize().width != sidebarWidth) {
            sidebar.setPreferredSize(new Dimension(sidebarWidth, minHeight));
            sidebar.revalidate();
            updateSidebarLogo();
        }
    }

    private static void updateSidebarLogo() {
        if (sidebarLogo == null || sidebar.getWidth() <= 0)
            return;

        Insets insets = sidebarLogo.getInsets();
        int availableWidth = sidebar.getWidth() - insets.left - insets.right - 1;
        Font baseFont = new Font("Sans-Serif", Font.PLAIN, 24);
        int fontSize = 24;
        while (fontSize > 10) {
            Font font = baseFont.deriveFont((float) fontSize);
            int iconSize = Math.min(fontSize, 24);
            int requiredWidth = iconSize + sidebarLogo.getIconTextGap()
                    + sidebarLogo.getFontMetrics(font).stringWidth(sidebarLogo.getText());
            if (requiredWidth <= availableWidth)
                break;
            fontSize--;
        }

        Font font = baseFont.deriveFont((float) fontSize);
        sidebarLogo.setFont(font);
        sidebarLogo.setIcon(scaleIcon(logoSourceIcon, fontSize, fontSize));
    }

    static void applyPreferences() {
        try {
            if ("System".equals(preferences.lookAndFeel())) {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } else {
                String className = preferences.lookAndFeel();
                for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                    if (info.getName().equals(className)) className = info.getClassName();
                }
                UIManager.setLookAndFeel(className);
            }
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException e) {
            logger.error("Could not apply selected theme: " + e.getMessage());
        }
        try {
            Color accent = Color.decode(preferences.accentColor());
            UIManager.put("Button.select", accent);
            UIManager.put("Component.focusColor", accent);
            UIManager.put("TabbedPane.selected", accent);
            UIManager.put("ProgressBar.foreground", accent);
        } catch (NumberFormatException e) {
            logger.error("Invalid accent color in launcher settings.");
        }
        if (frame.isDisplayable()) SwingUtilities.updateComponentTreeUI(frame);
    }

    public void start() {
        logger.debug("Showing window");
        gui(true);
    }

    static void onLaunch(ActionEvent e) {
        logger.debug("Play button pressed");
        JButton button = e == null ? launchButton : (JButton) e.getSource();
        if (button == null) return;
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

    /** Adds the temporary Microsoft login link to the sidebar. */
    public static void addItem(JComponent comp) {
        SwingUtilities.invokeLater(() -> {
            sidebarFooter.add(comp, BorderLayout.NORTH);
            sidebarFooter.revalidate();
            sidebarFooter.repaint();
        });
    }

    private static void addScreen(String name, JComponent screen) {
        content.add(screen, name);
        JToggleButton button = new JToggleButton(name);
        button.setIcon(NavigationIcon(name));
        button.setIconTextGap(10);
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

    private static ImageIcon NavigationIcon(String page) {
        ImageIcon icon = switch (page) {
            case "Play" -> new ImageIcon(Main.getImageAsset("pages/play.png"));
            case "News" -> new ImageIcon(Main.getImageAsset("pages/news.png"));
            case "Accounts" -> new ImageIcon(Main.getImageAsset("pages/accounts.png"));
            case "Settings" -> new ImageIcon(Main.getImageAsset("pages/settings.png"));
            default -> new ImageIcon(Main.getImageAsset("missing.png"));
        };
        icon = GuiHelpers.scaleIcon(icon, 32, 32);
        return icon;
    }
}
