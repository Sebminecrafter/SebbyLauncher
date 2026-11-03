package io.github.sebminecrafter.sebbylauncher;

import io.github.sebminecrafter.sebbylogger.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LauncherDirs {
    public static final Path ROOT = Path.of(System.getProperty("user.home"), "SebbyLauncher");
    public static final Path TOKENS = ROOT.resolve("tokens");
    public static final Path VERSIONS = ROOT.resolve("versions");
    public static final Path LIBRARIES = ROOT.resolve("libraries");
    public static final Path INSTANCES = ROOT.resolve("instances");
    public static final Path ASSETS = ROOT.resolve("assets");
    public static final Path RUNTIMES = ROOT.resolve("runtimes");
    private static final Path[] paths = {ROOT, TOKENS, VERSIONS, LIBRARIES, INSTANCES, ASSETS, RUNTIMES};

    /**
     * Prevent instantiation
     */
    private LauncherDirs() {
        throw new UnsupportedOperationException(
                "Cannot be instantiated."
        );
    }

    /**
     * Creates directories
     */
    public static void init() {
        Logger logger = new Logger("SebbyLauncher/LauncherDirs");
        logger.info("Creating Directories...");

        for (Path path : paths) {
            if (path.toFile().isDirectory()) continue;
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                logger.error("Failed to create directory: " + path.toAbsolutePath());
                logger.trace(e.getMessage());
            }
        }
    }
}