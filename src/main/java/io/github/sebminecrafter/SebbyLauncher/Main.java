package io.github.sebminecrafter.SebbyLauncher;

import io.github.sebminecrafter.sebbylogger.Logger;

import java.net.URL;
import java.util.Arrays;

public class Main {
    final static Logger logger = new Logger();

    public static final Window window = new Window();

    static void main(String[] args) {
        if (!Arrays.asList(args).contains("nologo")) startupMessage();
        // Run GUI
        javax.swing.SwingUtilities.invokeLater(window::start);
    }

    public static URL getAsset(String path) {
        URL result = Main.class.getResource("/assets/" + path);
        if (result == null) logger.error("Resource not found: "+path,"SebbyLauncher/Assets");
        return result;
    }

    public static URL getImageAsset(String path) {
        URL result = getAsset("image/" + path);
        if (result == null) result = getAsset("/assets/image/missing.png");
        return result;
    }

    private static void startupMessage() {
        logger.info("SebbyLauncher started.     ","SebbyLauncher/Main");
        logger.info("   --- SebbyLauncher ---   ","SebbyLauncher/Main");
        logger.info("An epic Minecraft launcher ","SebbyLauncher/Main");
        logger.info("Made by Sebminecrafter     ","SebbyLauncher/Main");
    }
}