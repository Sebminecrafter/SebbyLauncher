package io.github.sebminecrafter.SebbyLauncher;

import io.github.sebminecrafter.SebbyLogger.Main;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.URI;

public class GuiHelpers {
    public static void open(URI uri) {
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().browse(uri);
            } catch (IOException e) {
                Main.error("Failed to open browser.", "SebbyLauncher/GuiHelpers");
            }
        } else {
            Main.error("Desktop not supported.", "SebbyLauncher/GuiHelpers");
        }
    }

    public static ImageIcon scaleIcon(ImageIcon oldIcon, int w, int h) {
        /*
        Adapted from https://stackoverflow.com/a/18335435
        Posted by trolologuy
        Retrieved 2025-11-25, License - CC BY-SA 3.0
        */
        Image newIcon = oldIcon.getImage();
        Image newimg = newIcon.getScaledInstance(w, h,  Image.SCALE_SMOOTH); // scale it the smooth way
        return new ImageIcon(newimg); // transform it back
    }
}
