package io.github.sebminecrafter.SebbyLauncher;

import io.github.sebminecrafter.sebbylogger.Logger;

import java.net.URL;
import java.util.Arrays;

public class Main {
    final static Logger logger = new Logger();

    public static URL getAsset(String directory) {
        URL result = Main.class.getResource("/assets/" + directory);
        if (result == null) {
            result = Main.class.getResource("/assets/image/missing.png");
            logger.error("Resource not found: "+directory,"SebbyLauncher/Assets");
        }
        return result;
    }
    private static void startupMessage() {
        logger.info("SebbyLauncher started.","SebbyLauncher/Main");
        logger.info(" --- SebbyLauncher --- ","SebbyLauncher/Main");
        logger.info("An epic Minecraft launcher","SebbyLauncher/Main");
        logger.info("Made by Sebminecrafter","SebbyLauncher/Main");
    }

    static void main(String[] args) {
        if (!Arrays.asList(args).contains("nologo")) startupMessage();
        // Run GUI
        javax.swing.SwingUtilities.invokeLater(Window::start);
    }
}