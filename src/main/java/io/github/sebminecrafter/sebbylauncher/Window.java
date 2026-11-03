package io.github.sebminecrafter.sebbylauncher;

import io.github.sebminecrafter.sebbylogger.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.concurrent.ExecutionException;

import static io.github.sebminecrafter.sebbylauncher.GuiHelpers.*;

public class Window {
    private static final JFrame frame = new JFrame("Sebby Launcher");
    private static final CardLayout cards = new CardLayout();
    private static final JPanel content = new JPanel(cards);
    private static final JPanel sidebar = new JPanel();
    private static final ButtonGroup nav = new ButtonGroup();

    private static final int minWidth = 480;
    private static final int minHeight = 360;
    private static final Logger logger = new Logger("SebbyLauncher/Window");

    // TODO: Improve layout, separate into two panes (small left top to bottom, big right selection screen(s), like official mc launcher)
    // TODO: Add version, mod loader selectors
    // TODO: Add separate instances
    // TODO: Add separate profiles/accounts
    // TODO: Add mod downloading/updating

    public void gui(boolean fullscreen) {
        // Make window
        frame.setLayout(new GridBagLayout());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setIconImage(new ImageIcon(Main.getImageAsset("Sebby32.png")).getImage());
        frame.setUndecorated(false);
        frame.setMinimumSize(new Dimension(minWidth, minHeight));

        // Add content
        ImageIcon logoIcon1 = new ImageIcon(Main.getImageAsset("Sebby64.png")); // load the image to a imageIcon
        int logoSize = 24;
        ImageIcon logoIcon = scaleIcon(logoIcon1, logoSize, logoSize);
        JLabel logo = new JLabel("Sebby Launcher",logoIcon,SwingConstants.LEFT);
        logo.setToolTipText("Made by Sebminecrafter");
        logo.setFont(new Font("Sans-Serif", Font.PLAIN, logoSize));
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.FIRST_LINE_START;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.ipadx = c.ipady = c.gridx = c.gridy = 0;
        c.weightx = c.weighty = 1;
        frame.getContentPane().add(logo, c);
        ImageIcon playIcon = new ImageIcon(Main.getImageAsset("play1.png"));
        ImageIcon playIcon2 = new ImageIcon(Main.getImageAsset("play2.png"));
        c.gridy= 1;
        JButton launch = new JButton("Play", playIcon);
        launch.setIcon(playIcon);
        launch.setPressedIcon(playIcon2);
        
        launch.addActionListener(this::onLaunch);
        
        frame.getContentPane().add(launch, c);

        // Display window
        frame.pack();
        frame.setSize(minWidth, minHeight);
        frame.setVisible(true);
        if (fullscreen) frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
    }

    public void start() {
        logger.debug("Showing window");
        gui(true);
    }

    private void onLaunch(ActionEvent e) {
        logger.debug("Play button pressed");
        JButton button = (JButton) e.getSource();
        button.setEnabled(false);

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                if (!Login.attemptLogin()) {
                    logger.error("Login failed.");
                    return null;
                }
                logger.info("Login verified.");
                if (new Launcher().launchGame()) {
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
        // adds the component on AWT thread
        SwingUtilities.invokeLater(() -> {
            Container pane = frame.getContentPane();
            pane.add(comp, gbc);
            pane.revalidate();
            pane.repaint();
            frame.revalidate();
        });
    }


    private static void addScreen(String name, JComponent screen) {
        content.add(screen, name);
        JToggleButton button = new JToggleButton(name);
        button.addActionListener(_ -> cards.show(content, name));
        nav.add(button);
        sidebar.add(button);
    }
}
