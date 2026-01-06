package io.github.sebminecrafter.SebbyLauncher;

import java.net.URL;
import java.util.Arrays;

public class Main {
    public static URL getAsset(String directory) {
        URL result = Main.class.getResource("/assets/" + directory);
        if (result == null) {
            result = Main.class.getResource("/assets/image/missing.png");
            io.github.sebminecrafter.SebbyLogger.Main.error("Resource not found: "+directory,"SebbyLauncher/Assets");
        }
        return result;
    }
    private static void startupMessage() {
        io.github.sebminecrafter.SebbyLogger.Main.info("SebbyLauncher started.","SebbyLauncher/Main");
        io.github.sebminecrafter.SebbyLogger.Main.info(" --- SebbyLauncher --- ","SebbyLauncher/Main");
        io.github.sebminecrafter.SebbyLogger.Main.info("An epic Minecraft launcher","SebbyLauncher/Main");
        io.github.sebminecrafter.SebbyLogger.Main.info("Made by Sebminecrafter","SebbyLauncher/Main");
    }
    public static void main(String[] args) {
        io.github.sebminecrafter.SebbyLogger.Main.main(args);
        if (!Arrays.asList(args).contains("nologo")) {
            startupMessage();
        }
        javax.swing.SwingUtilities.invokeLater(new Runnable() { // SWING
            public void run() {Window.start();} // Run GUI
        });
    }
}