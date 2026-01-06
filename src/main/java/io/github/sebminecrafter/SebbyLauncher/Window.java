package io.github.sebminecrafter.SebbyLauncher;

import javax.swing.*;
import java.awt.*;
import static io.github.sebminecrafter.SebbyLauncher.GuiHelpers.*;

public class Window {
    static JFrame frame = new JFrame("Sebby Launcher");
    static GridBagConstraints c = new GridBagConstraints();
    static int crWidth = 1280, crHeight = 720;
    public static void gui(boolean fullscreen) {
        // Make window
        frame.setLayout(new GridBagLayout());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setIconImage(new ImageIcon(Main.getAsset("image/Sebby32.png")).getImage());
        frame.setUndecorated(false);

        // Add content
        ImageIcon logoIcon1 = new ImageIcon(Main.getAsset("image/Sebby64.png")); // load the image to a imageIcon
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
        ImageIcon playIcon = new ImageIcon(Main.getAsset("image/play1.png"));
        c.gridy= 1;
        JButton launch = new JButton("Play", playIcon);
        frame.getContentPane().add(launch, c);

        // Display window
        frame.pack();
        frame.setSize(crWidth, crHeight);
        frame.setVisible(true);
        if (fullscreen) frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
    }

    public static void start() {
        io.github.sebminecrafter.SebbyLogger.Main.debug("Showing window","SebbyLauncher/Window");
        gui(true);
    }

    public static void addItem(JComponent comp, GridBagConstraints gbc) {
        frame.getContentPane().add(comp, gbc);
        frame.revalidate();
        frame.repaint();
        frame.pack();
        frame.setSize(crWidth, crHeight);
    }
}
