package io.github.sebminecrafter.sebbylauncher;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.sebminecrafter.sebbylauncher.Main.logger;

public class GuiHelpers {
    public static void open(URI uri) {
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().browse(uri);
            } catch (IOException e) {
                logger.error("Failed to open browser.", "SebbyLauncher/GuiHelpers");
            }
        } else {
            logger.error("Desktop not supported.", "SebbyLauncher/GuiHelpers");
        }
    }

    public static void openFolder(Path folder, Component parent) {
        try {
            Files.createDirectories(folder);
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                throw new IOException("Opening folders is not supported on this system.");
            }
            Desktop.getDesktop().open(folder.toFile());
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            logger.error("Failed to open folder " + folder + ": " + e.getMessage());
            JOptionPane.showMessageDialog(parent, e.getMessage(), "Folder could not be opened",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    public static ImageIcon scaleIcon(ImageIcon oldIcon, int w, int h) {
        Image newIcon = oldIcon.getImage();
        Image newImg = newIcon.getScaledInstance(w, h,  Image.SCALE_SMOOTH); // scale it the smooth way
        return new ImageIcon(newImg); // transform it back
    }
}
