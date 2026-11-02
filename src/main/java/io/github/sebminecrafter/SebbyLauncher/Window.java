package io.github.sebminecrafter.SebbyLauncher;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.concurrent.ExecutionException;

import static io.github.sebminecrafter.SebbyLauncher.GuiHelpers.*;
import static io.github.sebminecrafter.SebbyLauncher.Main.logger;

public class Window {
    private static final JFrame frame = new JFrame("Sebby Launcher");
    private static final GridBagConstraints c = new GridBagConstraints();
    private static final String source = "SebbyLauncher/Window";
    private static final int minWidth = 480;
    private static final int minHeight = 360;

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
        logger.debug("Showing window", source);
        gui(true);
    }

    private void onLaunch(ActionEvent e) {
        logger.debug("Play button pressed", source);
        JButton button = (JButton) e.getSource();
        button.setEnabled(false);

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return Login.attemptLogin();
            }

            @Override
            protected void done() {
                button.setEnabled(true);
                try {
                    if (get()) {
                        logger.info("Login verified.", source);
                        Launcher launcher = new Launcher();
                        launcher.launchGame();
                    } else {
                        logger.error("Login failed.", source);
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    logger.error("Login crashed: " + ex.getCause(), source);
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
}
