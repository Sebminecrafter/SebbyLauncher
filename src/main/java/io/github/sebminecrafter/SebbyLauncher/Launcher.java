package io.github.sebminecrafter.SebbyLauncher;

import io.github.sebminecrafter.sebbylogger.Logger;

public class Launcher {
    private final Logger logger;

    /**
     * Create a new launcher instance (to launch the game)
     */
    public Launcher() {
        logger = new Logger("SebbyLauncher/Launch");
    }

    public void launchGame() {
        logger.info("Starting game...");
        /*
         TODO:
          - Get game version, mod loader, etc.
          - Check if it is already downloaded
          - Create java command OR start with subprocess
          - Start game w/ token+username based on login
        */
    }
}
